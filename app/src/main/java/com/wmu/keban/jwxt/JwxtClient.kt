package com.wmu.keban.jwxt

import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URI
import java.net.URL
import java.net.URLEncoder
import java.util.zip.GZIPInputStream

/**
 * 温州医科大学教务自动获取客户端(统一身份认证 SSO)
 *
 * 全部地址、参数与判定条件均来自**对真实站点的匿名抓取实测**(2026-10-30),未使用任何猜测:
 *
 * 1. 入口:`GET https://jwxt.wmu.edu.cn/sso/jziotlogin` → 302 →
 *    `https://authserver.wmu.edu.cn/authserver/login?service=http://jwxt.wmu.edu.cn/sso/jziotlogin`
 * 2. 统一身份认证登录页(金智 authserver):表单 `POST /authserver/login?service=…`
 *    字段:`username`(学号) `password` `execution`(隐藏,服务端下发) `_eventId=submit`
 *    · 验证码容器 `#captchaParent` 默认 `style="display:none"` → **首次登录无需验证码**
 *      (仅失败若干次后服务端才要求;届时本客户端如实报告,不猜、不重试以免账号锁定)
 *    · 页面无任何密码加密 JS(无 AES/RSA/salt),即密码经 HTTPS 明文提交(该站为 http 回跳,见网络策略)
 * 3. 登录成功 → CAS 携带 ticket 回跳 jwxt 建立会话(JSESSIONID)
 * 4. 课表 PDF **不硬编码地址**:会话建立后取个人课表查询页 HTML,从页面中**发现**导出链接,
 *    逐个请求并校验 `%PDF` 魔数;发现不到就如实报告并记录诊断片段
 *
 * 隐私:密码只在内存中使用;诊断信息不含账号(学号打码)。不做自动重试(登录失败锁定阈值 5 次)。
 */
class JwxtClient(
    private val ssoEntry: String = JWXT + TIMETABLE_SSO_ENTRY,
    /** 传入 WebView 的 UA,让请求与「内置浏览器」完全一致(降低被风控判定为可疑的概率) */
    private val userAgent: String? = null
) {

    companion object {
        const val JWXT = "https://jwxt.wmu.edu.cn"
        const val AUTH_SERVER_HOST = "authserver.wmu.edu.cn"
        const val TIMETABLE_SSO_ENTRY = "/sso/jziotlogin"

        /** 用户提供的个人课表查询页(新正方 学生课表查询) */
        const val TIMETABLE_PAGE =
            "/jwglxt/kbcx/xskbcx_cxXskbcxIndex.html?gnmkdm=N2151&layout=default"

        /** "a=1; b=2" → Map(WebView Cookie 头 → 客户端 Cookie 表) */
        fun parseCookieHeader(header: String): Map<String, String> =
            header.split(';').mapNotNull { part ->
                val kv = part.trim()
                if (kv.isEmpty() || !kv.contains('=')) null
                else kv.substringBefore('=') to kv.substringAfter('=')
            }.toMap()

        /**
         * 教务站点用的 UA 伪装:把 `Mobile`→`eliboM`、`Android`→`diordnA`。
         *
         * 做法来自参考项目 wakeup-course-schedule 的 WebViewLoginFragment(默认启用,并配了开关):
         * 正方/金智教务对"Android/Mobile" UA 会返回异常页面(手机版/白屏)。
         * 只改 UA 里的两个标记词,引擎与版本号保持不变,避免 UA 与内核不一致再被识别。
         */
        fun wmuUserAgent(base: String): String =
            base.replace("Mobile", "eliboM").replace("Android", "diordnA")

        private const val UA =
            "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0 Mobile Safari/537.36"
    }

    /** 按主机分别保管 Cookie(避免把教务会话发给认证服务器) */
    private val cookiesByHost = mutableMapOf<String, LinkedHashMap<String, String>>()
    private val diag = mutableListOf<String>()

    private fun log(s: String) {
        diag += s
    }

    /** 会话诊断:只列 Cookie **名称**,不含任何值(值可能等同凭据) */
    private fun cookieNames(host: String): String =
        cookiesByHost[host]?.keys?.joinToString(",")?.ifBlank { "(空)" } ?: "(无)"

    fun diagnostic(): String = diag.joinToString("\n")

    /** 学号打码,仅保留后 4 位(用于诊断展示) */
    private fun mask(account: String): String =
        if (account.length <= 4) "****" else "****" + account.takeLast(4)

    // ───────────────────────── HTTP 基础设施 ─────────────────────────

    private class Resp(val code: Int, val body: ByteArray, val contentType: String, val location: String?, val finalUrl: String) {
        val text: String get() = String(body, Charsets.UTF_8)
        val isPdf: Boolean get() = body.size > 4 && String(body.copyOfRange(0, 4), Charsets.US_ASCII) == "%PDF"
    }

    private fun cookieHeaderFor(host: String): String? =
        cookiesByHost[host]?.takeIf { it.isNotEmpty() }?.entries?.joinToString("; ") { "${it.key}=${it.value}" }

    private fun storeCookies(host: String, conn: HttpURLConnection) {
        val jar = cookiesByHost.getOrPut(host) { linkedMapOf() }
        conn.headerFields?.forEach { (k, values) ->
            if (k != null && k.equals("Set-Cookie", ignoreCase = true)) {
                values?.forEach { raw ->
                    val kv = raw.substringBefore(';')
                    val name = kv.substringBefore('=', "")
                    val value = kv.substringAfter('=', "")
                    if (name.isNotBlank()) jar[name] = value
                }
            }
        }
    }

    private fun readBody(conn: HttpURLConnection): ByteArray {
        val raw = runCatching { conn.inputStream }.getOrNull()
            ?: conn.errorStream
            ?: return ByteArray(0)
        val stream = if ((conn.contentEncoding ?: "").contains("gzip", true)) GZIPInputStream(raw) else raw
        stream.use { input ->
            val out = ByteArrayOutputStream()
            val buf = ByteArray(32 * 1024)
            while (true) {
                val n = input.read(buf)
                if (n <= 0) break
                out.write(buf, 0, n)
            }
            return out.toByteArray()
        }
    }

    private fun request(url: String, method: String, referer: String?, form: Map<String, String>? = null): Resp =
        requestWithCookieHeader(url, method, referer, form, null)

    private fun requestWithCookieHeader(
        url: String,
        method: String,
        referer: String?,
        form: Map<String, String>? = null,
        cookieOverride: String? = null
    ): Resp {
        val uri = URI(url)
        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = method
            connectTimeout = 15000
            readTimeout = 25000
            instanceFollowRedirects = false
            setRequestProperty("User-Agent", userAgent ?: UA)
            setRequestProperty("Accept-Encoding", "gzip")
            setRequestProperty("Accept", "text/html,application/xhtml+xml,application/pdf,*/*")
            setRequestProperty("Accept-Language", "zh-CN,zh;q=0.9")
            (cookieOverride ?: cookieHeaderFor(uri.host))?.let { setRequestProperty("Cookie", it) }
            referer?.let { setRequestProperty("Referer", it) }
        }
        if (form != null) {
            conn.doOutput = true
            conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded; charset=UTF-8")
            // 表单 POST 补上 Origin(浏览器会带,部分风控会校验)
            runCatching { conn.setRequestProperty("Origin", "${uri.scheme}://${uri.host}") }
            val body = form.entries.joinToString("&") {
                URLEncoder.encode(it.key, "UTF-8") + "=" + URLEncoder.encode(it.value, "UTF-8")
            }
            conn.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
        }
        val code = runCatching { conn.responseCode }.getOrElse { -1 }
        val ct = conn.contentType ?: ""
        val loc = conn.getHeaderField("Location")
        storeCookies(uri.host, conn)
        val body = readBody(conn)
        conn.disconnect()
        return Resp(code, body, ct, loc, url)
    }

    /** 跟随重定向(跨主机;沿用 GET) */
    private fun follow(url: String, referer: String?, maxHops: Int = 8): Resp {
        var current = url
        var ref = referer
        var hops = 0
        while (true) {
            val r = request(current, "GET", ref)
            val loc = r.location
            if (r.code in 300..399 && !loc.isNullOrBlank() && hops < maxHops) {
                ref = current
                current = URI(current).resolve(loc).toString()
                hops++
                continue
            }
            return r
        }
    }

    // ───────────────────────── 登录(统一身份认证) ─────────────────────────

    class LoginOutcome(val ok: Boolean, val message: String)

    fun login(username: String, password: String): LoginOutcome {
        // 与真实浏览器一致:先落地教务登录页(建立 jwxt 会话与路由 Cookie),再进入统一身份认证。
        // 直接空手跳 SSO 会被服务端判为可疑会话(实测表现为"要求图形验证码")。
        val primed = runCatching { request("$JWXT/jwglxt/xtgl/login_slogin.html", "GET", null) }.getOrNull()
        log("⓪ 预热教务会话:HTTP ${primed?.code ?: "-"},cookie=${cookieNames(URI(JWXT).host)}")

        log("① 访问教务 SSO 入口,获取统一身份认证登录页")
        val entry = follow(ssoEntry, "$JWXT/jwglxt/xtgl/login_slogin.html")
        val entryUrl = entry.finalUrl
        log("   最终地址主机:${URI(entryUrl).host}（HTTP ${entry.code}）")
        log("   cookie:authserver=${cookieNames(AUTH_SERVER_HOST)} jwxt=${cookieNames(URI(JWXT).host)}")

        if (entry.code != 200) {
            return LoginOutcome(false, "无法打开统一身份认证登录页(HTTP ${entry.code})")
        }
        val loginHtml = entry.text

        val execution = Regex("""name="execution"[^>]*value="([^"]+)"""").find(loginHtml)?.groupValues?.get(1)
        if (execution == null) {
            log("   未找到 execution 隐藏项")
            return LoginOutcome(false, "登录页结构变化:未找到 execution")
        }
        // 验证码是否**本次**被要求:由服务端按会话/风控决定,不是每次都要求
        // (用户实测:正常浏览器登录无需验证码;而线程外的匿名抓取被判定可疑时才出现 show() 调用)
        val captchaRequired = Regex("""captchaParent"\)\s*\.\s*show""").containsMatchIn(loginHtml)
        if (captchaRequired) {
            log("   本次会话被服务端要求图形验证码(页面含 captchaParent.show + captchaId)")
            return LoginOutcome(
                false,
                "本次会话被要求图形验证码(服务端按会话/风控判定)。\n" +
                    "请改用「内置浏览器」登录一次:在浏览器里正常登录不需要验证码,登录后自动获取会复用该会话。"
            )
        }
        val service = Regex("""[?&]service=([^&"']+)""").find(entryUrl)?.groupValues?.get(1)

        log("② 提交统一身份认证登录(账号 ${mask(username)},密码不明文记录)")
        val postUrl = buildString {
            append("https://").append(AUTH_SERVER_HOST).append("/authserver/login")
            if (service != null) append("?service=").append(service)
        }
        val form = mapOf(
            "username" to username,
            "password" to password,
            "execution" to execution,
            "_eventId" to "submit"
        )
        val posted = runCatching { request(postUrl, "POST", entryUrl, form) }
            .getOrElse { return LoginOutcome(false, "网络请求失败:${it.javaClass.simpleName}") }
        log("   提交响应 HTTP ${posted.code}${posted.location?.let { " → ${URI(it).host}" } ?: ""}")

        // 失败提示:优先取认证服务器的错误容器 #errorDiv p(实测页面 config.error 即取此处)
        val errFromDiv = Regex("""id="errorDiv"[^>]*>(.*?)</div>""", RegexOption.DOT_MATCHES_ALL)
            .find(posted.text)?.groupValues?.get(1)
            ?.replace(Regex("<[^>]+>"), "")
            ?.trim()
            ?.takeIf { it.isNotEmpty() }
        val errText = errFromDiv ?: listOf(
            "用户名或密码错误", "密码错误", "用户名不存在", "账号被锁定", "账户被锁定",
            "请输入验证码", "验证码错误", "登录失败", "认证失败", "密码已过期"
        ).firstOrNull { posted.text.contains(it) }
        if (errText != null) log("   服务端提示:$errText")
        // 诊断附上响应片段(便于据实排查;不含任何凭据)
        val snippet = posted.text
            .replace(Regex("<(script|style)[^>]*>.*?</\\1>", RegexOption.DOT_MATCHES_ALL), " ")
            .replace(Regex("<[^>]+>"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()
            .take(200)
        log("   响应片段:${snippet.ifEmpty { "(空)" }}")

        // 成功:跟随票据回跳,或已建立 jwxt 会话
        var landed = posted
        if (posted.code in 300..399 && !posted.location.isNullOrBlank()) {
            landed = follow(URI(postUrl).resolve(posted.location).toString(), postUrl)
            log("③ 跟随票据回跳,最终主机 ${URI(landed.finalUrl).host}(HTTP ${landed.code})")
        }
        val verified = verifySession()
        log("④ 会话校验:${if (verified) "成功" else "失败"}")
        return when {
            verified -> LoginOutcome(true, "登录成功")
            errText != null -> LoginOutcome(false, "登录失败:$errText")
            landed.text.contains("authserver/login") -> LoginOutcome(false, "登录未通过(账号或密码可能不正确)")
            else -> LoginOutcome(false, "登录后未能建立教务会话,请重试或改用内置浏览器")
        }
    }

    /** 会话是否有效:教务首页可访问且不再跳登录页 */
    private fun verifySession(): Boolean {
        val r = request("$JWXT/jwglxt/xtgl/index_initMenu.html", "GET", JWXT)
        val ok = r.code == 200 && !r.text.contains("login_slogin")
        log("   教务首页 HTTP ${r.code},长度 ${r.body.size},cookie=${cookieNames(URI(JWXT).host)}")
        return ok
    }

    /** 注入外部会话(来自内置浏览器登录后的 Cookie),用于免账号密码取课表 */
    fun useSession(cookies: Map<String, String>) {
        val jar = cookiesByHost.getOrPut(URI(JWXT).host) { linkedMapOf() }
        jar.putAll(cookies)
        log("使用内置浏览器会话(${cookies.size} 项:${cookies.keys.joinToString(",")})")
    }

    fun hasSession(): Boolean = verifySession()

    /**
     * 直接用会话 Cookie 下载某个 URL 的 PDF(内置浏览器下载拦截用)。
     * 非 PDF 内容返回 null,由调用方提示用户。
     */
    fun downloadPdf(url: String, cookieHeader: String?): ByteArray? {
        val r = requestWithCookieHeader(url, "GET", JWXT, null, cookieHeader)
        log("   下载 $url → HTTP ${r.code},${r.body.size} 字节,${r.contentType}${if (r.isPdf) ",PDF ✓" else ""}")
        return if (r.isPdf) r.body else null
    }

    // ───────────────────────── 课表 PDF ─────────────────────────

    class PdfOutcome(
        val ok: Boolean,
        val bytes: ByteArray? = null,
        val message: String,
        val triedCandidates: List<String> = emptyList()
    )

    /**
     * 取课表页 → 从页面发现导出候选 → 试取 PDF。
     * 页面里找不到候选时不猜地址,而是记录相关片段供诊断。
     */
    fun fetchTimetablePdf(): PdfOutcome {
        log("⑤ 请求个人课表查询页")
        val page = request("$JWXT$TIMETABLE_PAGE", "GET", "$JWXT/jwglxt/xtgl/index_initMenu.html")
        log("   HTTP ${page.code},长度 ${page.body.size}")
        if (page.code != 200) {
            return PdfOutcome(false, null, "课表页访问失败(HTTP ${page.code}),会话可能已失效")
        }
        val html = page.text

        // 先把页面里与"导出/打印/PDF"有关的真实证据记进诊断:
        // 不猜地址,靠页面自己给出来的线索定位入口(便于据实适配)
        logExportEvidence(html)

        val candidates = linkedSetOf<String>()
        // 只认真正的导出/打印语义,不再用 "dy" 这类宽松匹配(会被 dynaForm 的 JS/CSS 误命中)
        Regex("""["'](/jwglxt/[^"']*\.(?:html|htm|pdf|action)[^"']*)["']""").findAll(html).forEach { m ->
            val u = m.groupValues[1]
            val path = u.substringBefore('?').lowercase()
            if (path.contains("print") || path.contains("pdf") || path.contains("export") ||
                path.contains("xskbcx") || path.contains("kbcx")
            ) candidates += u
        }
        val list = candidates.toList()
        log("   发现候选导出链接 ${list.size} 个")
        list.take(8).forEach { log("     · $it") }

        if (list.isEmpty()) {
            return PdfOutcome(false, null, "未能从课表页发现 PDF 导出入口(诊断已记录页面证据)", list)
        }

        for (c in list.take(8)) {
            val r = request(URI("$JWXT$TIMETABLE_PAGE").resolve(c).toString(), "GET", "$JWXT$TIMETABLE_PAGE")
            log("   试 ${c} → HTTP ${r.code},${r.body.size} 字节,${r.contentType}${if (r.isPdf) ",PDF ✓" else ""}")
            if (r.isPdf) return PdfOutcome(true, r.body, "已获取课表 PDF(${r.body.size} 字节)", list)
        }
        return PdfOutcome(false, null, "找到候选但都未返回 PDF(诊断已记录,可改用网页上的「下载」按钮)", list)
    }

    /**
     * 用**页面自己的查询表单**做一次同构请求(action 与字段名都取自页面,非猜测),
     * 只回报响应结构与字段名,**不回报任何课程数据值**(便于据此写映射,同时避免泄露个人数据)。
     */
    fun probeTimetableData(
        pageUrl: String,
        actionPath: String,
        form: Map<String, String>,
        cookieHeader: String
    ): String {
        val url = runCatching { URI(pageUrl).resolve(actionPath).toString() }.getOrDefault(actionPath)
        val r = requestWithCookieHeader(url, "POST", pageUrl, form, cookieHeader)
        val sb = StringBuilder()
        sb.append("POST $url\n→ HTTP ${r.code},${r.body.size} 字节,${r.contentType}\n")
        val text = r.text.trimStart()
        if (text.startsWith("{") || text.startsWith("[")) {
            runCatching {
                val o = org.json.JSONObject(text)
                sb.append("顶层字段名:${o.keys().asSequence().toList()}\n")
                o.keys().forEach { k ->
                    val arr = o.optJSONArray(k)
                    if (arr != null && arr.length() > 0) {
                        arr.optJSONObject(0)?.let { e ->
                            sb.append("$k[0] 字段名(${arr.length()} 条):")
                            sb.append(e.keys().asSequence().toList())
                            sb.append('\n')
                        }
                    }
                }
            }.onFailure { sb.append("JSON 解析失败:${it.javaClass.simpleName}\n") }
        } else {
            sb.append("非 JSON 响应;前 120 字:${text.take(120)}\n")
        }
        return sb.toString()
    }

    /**
     * 采集页面里与导出/打印/PDF 有关的**真实证据**(只记录结构,不含任何凭据或正文):
     * onclick 处理函数、含关键字的标签、所有 /jwglxt/ 链接、表单 action。
     * 用于在无法登录的情况下据实定位导出入口,而不是靠猜 URL。
     */
    private fun logExportEvidence(html: String) {
        fun uniq(re: Regex, limit: Int, clean: (String) -> String = { it }): List<String> {
            val set = linkedSetOf<String>()
            re.findAll(html).forEach { m ->
                if (set.size >= limit) return@forEach
                val v = clean(m.value).replace(Regex("\\s+"), " ").trim()
                if (v.isNotEmpty()) set += v
            }
            return set.toList()
        }
        val onclick = uniq(Regex("""onclick="([^"]{0,140})""""), 15)
        val kwTags = uniq(Regex("""<[^<>]{0,260}?(?:导出|打印|下载|PDF|生成)[^<>]{0,200}?>"""), 12)
        val urls = uniq(Regex("""/jwglxt/[A-Za-z0-9_/.\-]{0,120}"""), 25)
        val forms = uniq(Regex("""<form[^>]{0,300}>"""), 5)
        val scripts = uniq(Regex("""["']([^"']*(?:kbcx|print|export)[^"']*\.js[^"']*)["']"""), 10) { it.trim('"', '\'') }

        log("   —— 导出入口证据 ——")
        log("   onclick(${onclick.size}):${onclick.joinToString(" | ").ifBlank { "(无)" }}")
        log("   含关键字标签(${kwTags.size}):${kwTags.joinToString("\n     ").ifBlank { "(无)" }}")
        log("   /jwglxt/ 链接(${urls.size}):${urls.joinToString(", ").ifBlank { "(无)" }}")
        log("   表单(${forms.size}):${forms.joinToString(" | ").ifBlank { "(无)" }}")
        log("   相关脚本(${scripts.size}):${scripts.joinToString(", ").ifBlank { "(无)" }}")
    }
}
