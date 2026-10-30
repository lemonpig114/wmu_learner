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
class JwxtClient(private val ssoEntry: String = JWXT + TIMETABLE_SSO_ENTRY) {

    companion object {
        const val JWXT = "https://jwxt.wmu.edu.cn"
        const val AUTH_SERVER_HOST = "authserver.wmu.edu.cn"
        const val TIMETABLE_SSO_ENTRY = "/sso/jziotlogin"

        /** 用户提供的个人课表查询页(新正方 学生课表查询) */
        const val TIMETABLE_PAGE =
            "/jwglxt/kbcx/xskbcx_cxXskbcxIndex.html?gnmkdm=N2151&layout=default"

        private const val UA =
            "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0 Mobile Safari/537.36"
    }

    /** 按主机分别保管 Cookie(避免把教务会话发给认证服务器) */
    private val cookiesByHost = mutableMapOf<String, LinkedHashMap<String, String>>()
    private val diag = mutableListOf<String>()

    private fun log(s: String) {
        diag += s
    }

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

    private fun request(url: String, method: String, referer: String?, form: Map<String, String>? = null): Resp {
        val uri = URI(url)
        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = method
            connectTimeout = 15000
            readTimeout = 25000
            instanceFollowRedirects = false
            setRequestProperty("User-Agent", UA)
            setRequestProperty("Accept-Encoding", "gzip")
            setRequestProperty("Accept", "text/html,application/xhtml+xml,application/pdf,*/*")
            cookieHeaderFor(uri.host)?.let { setRequestProperty("Cookie", it) }
            referer?.let { setRequestProperty("Referer", it) }
        }
        if (form != null) {
            conn.doOutput = true
            conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
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
        log("① 访问教务 SSO 入口,获取统一身份认证登录页")
        val entry = follow(ssoEntry, null)
        val entryUrl = entry.finalUrl
        log("   最终地址主机:${URI(entryUrl).host}（HTTP ${entry.code}）")
        if (entry.code != 200) {
            return LoginOutcome(false, "无法打开统一身份认证登录页(HTTP ${entry.code})")
        }
        val loginHtml = entry.text

        val execution = Regex("""name="execution"[^>]*value="([^"]+)"""").find(loginHtml)?.groupValues?.get(1)
        if (execution == null) {
            log("   未找到 execution 隐藏项")
            return LoginOutcome(false, "登录页结构变化:未找到 execution")
        }
        // 验证码是否已被服务端要求(容器默认 display:none)
        val captchaShown = Regex("""id="captchaParent"[^>]*style="([^"]*)"""")
            .find(loginHtml)?.groupValues?.get(1)?.contains("none")?.not() ?: false
        if (captchaShown) {
            log("   该账号登录需要验证码")
            return LoginOutcome(false, "教务处要求输入验证码(通常是多次失败后触发),请稍后用内置浏览器手动登录一次")
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

        // 失败提示(认证服务器在返回页里给出)
        val errText = listOf(
            "用户名或密码错误", "密码错误", "用户名不存在", "账号被锁定", "账户被锁定",
            "验证码", "登录失败", "认证失败", "密码已过期"
        ).firstOrNull { posted.text.contains(it) }

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
        if (!ok) log("   教务首页返回 HTTP ${r.code},长度 ${r.body.size}")
        return ok
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

        val candidates = linkedSetOf<String>()
        // 页面里实际出现的导出/打印相关链接
        Regex("""["'](/jwglxt/[^"']*)["']""").findAll(html).forEach { m ->
            val u = m.groupValues[1]
            if (u.contains("pdf", true) || u.contains("print", true) || u.contains("dy", true)) candidates += u
        }
        Regex("""(xskbcx_[A-Za-z0-9_]*[Pp]df[A-Za-z0-9_]*\.html[^"']*)""").findAll(html).forEach { m ->
            candidates += "/jwglxt/kbcx/" + m.groupValues[1]
        }
        val list = candidates.toList()
        log("   发现候选导出链接 ${list.size} 个")
        list.take(8).forEach { log("     · $it") }

        if (list.isEmpty()) {
            val related = html.lines().filter {
                it.contains("pdf", true) || it.contains("导出") || it.contains("打印") || it.contains("下载")
            }.take(4).joinToString("\n").take(600)
            log("   页面中未见导出链接;相关片段(供据此调整):\n$related")
            return PdfOutcome(false, null, "未能从课表页发现 PDF 导出入口(诊断已记录)", list)
        }

        for (c in list.take(8)) {
            val r = request(URI("$JWXT$TIMETABLE_PAGE").resolve(c).toString(), "GET", "$JWXT$TIMETABLE_PAGE")
            log("   试 ${c} → HTTP ${r.code},${r.body.size} 字节,${r.contentType}${if (r.isPdf) ",PDF ✓" else ""}")
            if (r.isPdf) return PdfOutcome(true, r.body, "已获取课表 PDF(${r.body.size} 字节)", list)
        }
        return PdfOutcome(false, null, "找到导出链接但未返回 PDF(诊断已记录)", list)
    }
}
