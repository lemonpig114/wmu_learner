package com.wmu.keban.ui.browser

import android.annotation.SuppressLint
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.wmu.keban.data.ImportHandoff
import com.wmu.keban.jwxt.JwxtClient
import com.wmu.keban.ui.common.EmptyHint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/**
 * 内置浏览器(第一阶段:基础浏览)
 *
 * 安全边界:
 * - 不记录、不上传任何 URL、Cookie、凭据;不开启远程调试
 * - 禁止 file:// / content:// 访问;拦截非 http(s) 协议跳转
 * - 下载与文件选择未启用:第三阶段按教务实际流程实现
 */
/** 教务课表查询页(与 JwxtClient.TIMETABLE_PAGE 对应,供浏览器默认打开) */
private const val JWXT_TIMETABLE_PAGE =
    "https://jwxt.wmu.edu.cn/jwglxt/kbcx/xskbcx_cxXskbcxIndex.html?gnmkdm=N2151&layout=default"

/** 桌面版 UA(部分教务页面在手机 UA 下渲染异常时的备用开关) */
private const val DESKTOP_UA =
    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0 Safari/537.36"

@SuppressLint("SetJavaScriptEnabled")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BrowserScreen() {
    val ctx = androidx.compose.ui.platform.LocalContext.current
    val scope = rememberCoroutineScope()
    // 默认打开教务课表查询入口,登录后即可在页面内点击「下载 PDF」
    var inputUrl by rememberSaveable { mutableStateOf(JWXT_TIMETABLE_PAGE) }
    var currentUrl by rememberSaveable { mutableStateOf(JWXT_TIMETABLE_PAGE) }
    var canGoBack by remember { mutableStateOf(false) }
    var webView by remember { mutableStateOf<WebView?>(null) }
    var lastRequested by remember { mutableStateOf<String?>(null) }
    var browserMessage by remember { mutableStateOf<String?>(null) }
    var currentShownUrl by remember { mutableStateOf(JWXT_TIMETABLE_PAGE) }
    var diagText by remember { mutableStateOf<String?>(null) }
    // UA 模式:0 伪装(默认,与参考项目一致) 1 桌面 2 原生
    var uaMode by remember { mutableStateOf(0) }
    val uaLabels = listOf("UA:伪装", "UA:桌面", "UA:原生")
    fun uaFor(mode: Int, base: String) = when (mode) {
        1 -> DESKTOP_UA
        2 -> base
        else -> JwxtClient.wmuUserAgent(base)
    }

    /** 用 WebView 的登录会话直接取课表 PDF(不依赖页面上的下载按钮) */
    fun fetchWithSession() {
        val cookie = CookieManager.getInstance().getCookie(JWXT_TIMETABLE_PAGE).orEmpty()
        if (cookie.isBlank()) {
            browserMessage = "尚未检测到教务登录会话,请先在页面中完成登录(地址栏会显示课表页)"
            return
        }
        browserMessage = "正在用当前登录会话获取课表…"
        scope.launch {
            val res = withContext(Dispatchers.IO) {
                runCatching {
                    val client = JwxtClient(userAgent = WebSettings.getDefaultUserAgent(ctx))
                    client.useSession(JwxtClient.parseCookieHeader(cookie))
                    if (!client.hasSession()) {
                        return@runCatching null to ("会话无效,教务首页仍跳登录页\n\n" + client.diagnostic())
                    }
                    val pdf = client.fetchTimetablePdf()
                    val bytes = pdf.bytes
                    if (pdf.ok && bytes != null) {
                        File(ctx.cacheDir, "pending_import.pdf").apply { writeBytes(bytes) } to pdf.message
                    } else {
                        null to (pdf.message + "\n\n" + client.diagnostic())
                    }
                }.getOrElse { null to ("获取失败:${it.javaClass.simpleName} ${it.message}") }
            }
            val file = res.first
            if (file != null) {
                ImportHandoff.pendingPdf = file
                browserMessage = "已获取课表 PDF,请切到「课表」页确认导入"
            } else {
                browserMessage = "未能获取课表 PDF,详情见诊断"
                diagText = res.second
            }
        }
    }

    /** 会话自检:只列 Cookie 名称(不含值),并探测页面实际内容,用于判断"白屏"到底是什么 */
    fun selfCheck() {
        val cm = CookieManager.getInstance()
        val jwxt = cm.getCookie(JwxtClient.JWXT).orEmpty()
        val auth = cm.getCookie("https://" + JwxtClient.AUTH_SERVER_HOST).orEmpty()
        fun names(h: String) = h.split(';').mapNotNull {
            it.trim().takeIf { s -> s.contains('=') }?.substringBefore('=')
        }.joinToString(", ").ifBlank { "(无)" }
        val loggedIn = currentShownUrl.contains("xskbcx") ||
            currentShownUrl.contains("index_initMenu") ||
            currentShownUrl.contains("/jwglxt/") && !currentShownUrl.contains("login")
        val basic = buildString {
            append("当前地址:${currentShownUrl}\n\n")
            append("jwxt.wmu.edu.cn Cookie:${names(jwxt)}\n")
            append("authserver Cookie:${names(auth)}\n\n")
            append(if (loggedIn) "判断:看起来已进入教务内页(登录可能已成功)" else "判断:仍在登录相关页面(登录可能未完成)")
            append("\n\n提示:只有出现 JSESSIONID 之类的教务会话 Cookie 才算登录成功。")
        }
        // 页面内容探针:evaluateJavascript 是异步的,不能写成同步函数返回值
        val w = webView
        if (w == null) {
            diagText = basic
            return
        }
        val probe = """
            (function(){
              try{
                var b = document.body;
                var t = b ? (b.innerText || '') : '';
                return JSON.stringify({
                  title: document.title || '',
                  ready: document.readyState || '',
                  bodyChildren: b ? b.childElementCount : -1,
                  htmlLen: document.documentElement ? document.documentElement.outerHTML.length : -1,
                  textLen: t.length,
                  text: t.replace(/\s+/g,' ').slice(0,300)
                });
              }catch(e){ return JSON.stringify({error: String(e)}); }
            })()
        """.trimIndent()
        w.evaluateJavascript(probe) { raw ->
            val pretty = runCatching {
                val obj = org.json.JSONObject(org.json.JSONTokener(raw).nextValue() as String)
                buildString {
                    append("\n\n【页面内容探针】\n")
                    append("标题:${obj.optString("title").ifBlank { "(空)" }}\n")
                    append("readyState:${obj.optString("ready")}\n")
                    append("body 子元素:${obj.optInt("bodyChildren", -1)}\n")
                    append("HTML 长度:${obj.optInt("htmlLen", -1)}\n")
                    append("可见文本长度:${obj.optInt("textLen", -1)}\n")
                    append("正文前 300 字:${obj.optString("text").ifBlank { "(空白页面)" }}")
                }
            }.getOrElse { "\n\n【页面内容探针】解析失败:${raw?.take(200)}" }
            diagText = basic + pretty
        }
    }

    /** 拦截网页下载:只接受 PDF,取出后交给课表页导入(复用 WebView 的登录会话) */
    fun handleDownload(url: String, mime: String?, disposition: String?) {
        val looksPdf = mime?.contains("pdf", true) == true ||
            url.contains(".pdf", true) ||
            disposition?.contains("pdf", true) == true
        if (!looksPdf) {
            browserMessage = "已忽略:只接收 PDF 下载(mime=${mime ?: "未知"})"
            return
        }
        browserMessage = "正在获取课表 PDF…"
        scope.launch {
            val file = withContext(Dispatchers.IO) {
                runCatching {
                    val cookie = CookieManager.getInstance().getCookie(url)
                    val bytes = JwxtClient().downloadPdf(url, cookie) ?: return@runCatching null
                    File(ctx.cacheDir, "pending_import.pdf").apply { writeBytes(bytes) }
                }.getOrNull()
            }
            if (file != null) {
                ImportHandoff.pendingPdf = file
                browserMessage = "已获取课表 PDF,请切到「课表」页确认导入"
            } else {
                browserMessage = "该下载没有返回 PDF(可能链接需重新登录或需在页面内点击导出)"
            }
        }
    }

    BackHandler(enabled = canGoBack) {
        webView?.goBack()
    }

    Scaffold(
        topBar = { TopAppBar(title = { Text("内置浏览器") }) }
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = inputUrl,
                    onValueChange = { inputUrl = it },
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                    placeholder = { Text("输入网址 https://…") }
                )
                Spacer(Modifier.width(8.dp))
                Button(
                    onClick = {
                        val t = inputUrl.trim()
                        if (t.isNotEmpty()) {
                            currentUrl = if (t.startsWith("http")) t else "https://$t"
                        }
                    },
                    enabled = inputUrl.isNotBlank()
                ) { Text("打开") }
            }

            Row(
                Modifier.fillMaxWidth().padding(horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(onClick = { webView?.goBack() }, enabled = canGoBack) { Text("‹ 后退") }
                TextButton(onClick = { webView?.reload() }) { Text("刷新") }
                TextButton(onClick = { fetchWithSession() }) { Text("获取课表") }
                TextButton(onClick = { selfCheck() }) { Text("自检") }
                TextButton(onClick = {
                    uaMode = (uaMode + 1) % 3
                    webView?.let { w ->
                        w.settings.userAgentString =
                            uaFor(uaMode, WebSettings.getDefaultUserAgent(ctx))
                        w.reload()
                    }
                }) { Text(uaLabels[uaMode]) }
            }
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    currentShownUrl.takeLast(60),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    modifier = Modifier.weight(1f)
                )
            }

            browserMessage?.let { msg ->
                Surface(color = MaterialTheme.colorScheme.secondaryContainer, modifier = Modifier.fillMaxWidth()) {
                    Row(
                        Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(msg, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                        TextButton(onClick = { browserMessage = null }) { Text("知道了") }
                    }
                }
            }

            if (currentUrl.isEmpty()) {
                Column(
                    Modifier
                        .fillMaxSize()
                        .padding(24.dp),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    EmptyHint(
                        "内置浏览器\n\n" +
                            "已默认打开教务课表查询页:登录(学号+密码+验证码)后,\n" +
                            "在页面里点击「下载」获取课表 PDF,应用会拦截并交给课表页导入。\n\n" +
                            "登录会话保留在应用内,「课表」页的「自动获取」可直接复用。\n\n" +
                            "安全说明:本页不记录 Cookie 与凭据,不保存浏览历史"
                    )
                }
            } else {
                AndroidView(
                    modifier = Modifier.fillMaxSize(),
                    factory = { ctx ->
                        WebView(ctx).apply {
                            settings.javaScriptEnabled = true
                            settings.domStorageEnabled = true
                            settings.allowFileAccess = false
                            settings.allowContentAccess = false
                            // 教务站点在 https 页面里混用 http(CAS 回跳/内嵌),参考项目(WakeUp fork)同样
                            // 使用 MIXED_CONTENT_ALWAYS_ALLOW;本页已禁用 file/content 访问、拦截非 http(s) 跳转
                            settings.mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                            // 登录页可能用 window.open / target=_blank 打开统一身份认证
                            settings.javaScriptCanOpenWindowsAutomatically = true
                            settings.setSupportMultipleWindows(true)
                            // 页面按宽度自适应,避免固定宽度登录页渲染成空白
                            settings.useWideViewPort = true
                            settings.loadWithOverviewMode = true
                            settings.textZoom = 100
                            // UA 伪装(参考项目默认做法):规避教务对 Android/Mobile UA 的异常响应
                            settings.userAgentString = uaFor(0, WebSettings.getDefaultUserAgent(ctx))
                            settings.setSupportZoom(true)
                            settings.builtInZoomControls = true
                            settings.displayZoomControls = false
                            webViewClient = object : WebViewClient() {
                                override fun shouldOverrideUrlLoading(
                                    view: WebView,
                                    request: WebResourceRequest
                                ): Boolean {
                                    // 仅放行 http/https,拦截 intent://、file:// 等
                                    val scheme = request.url.scheme
                                    return scheme != "http" && scheme != "https"
                                }

                                override fun onPageFinished(view: WebView, url: String?) {
                                    super.onPageFinished(view, url)
                                    canGoBack = view.canGoBack()
                                    currentShownUrl = url ?: currentShownUrl
                                }

                                override fun onPageStarted(view: WebView, url: String?, favicon: android.graphics.Bitmap?) {
                                    super.onPageStarted(view, url, favicon)
                                    currentShownUrl = url ?: currentShownUrl
                                }

                                /** 把"白屏"变成可诊断信息 */
                                override fun onReceivedError(
                                    view: WebView,
                                    request: WebResourceRequest,
                                    error: android.webkit.WebResourceError
                                ) {
                                    super.onReceivedError(view, request, error)
                                    if (request.isForMainFrame) {
                                        browserMessage = "页面加载失败(${error.errorCode} ${error.description}) " +
                                            "url=${request.url}"
                                    }
                                }

                                override fun onReceivedHttpError(
                                    view: WebView,
                                    request: WebResourceRequest,
                                    errorResponse: android.webkit.WebResourceResponse
                                ) {
                                    super.onReceivedHttpError(view, request, errorResponse)
                                    if (request.isForMainFrame) {
                                        browserMessage = "页面返回 HTTP ${errorResponse.statusCode}(url=${request.url})"
                                    }
                                }

                                /** 证书异常:只报告,不擅自放行(避免中间人窃取登录口令) */
                                override fun onReceivedSslError(
                                    view: WebView,
                                    handler: android.webkit.SslErrorHandler,
                                    error: android.net.http.SslError
                                ) {
                                    handler.cancel()
                                    browserMessage = "证书校验失败:${error.primaryError} " +
                                        "url=${error.url}(已拒绝加载,未放行)"
                                }
                            }
                            webChromeClient = object : WebChromeClient() {
                                /** 统一身份认证可能以新窗口打开:在同一 WebView 内加载,避免"白屏/无反应" */
                                override fun onCreateWindow(
                                    view: WebView,
                                    isDialog: Boolean,
                                    isUserGesture: Boolean,
                                    resultMsg: android.os.Message
                                ): Boolean {
                                    val transport = resultMsg.obj as? WebView.WebViewTransport ?: return false
                                    transport.webView = view
                                    resultMsg.sendToTarget()
                                    return true
                                }
                            }
                            // Cookie:允许跨站(authserver → jwxt 的 CAS 回跳需要)
                            CookieManager.getInstance().setAcceptCookie(true)
                            CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)
                            // 下载拦截:教务页面的「下载 PDF」按钮触发下载时由此接管
                            setDownloadListener { url, _, contentDisposition, mimeType, _ ->
                                handleDownload(url, mimeType, contentDisposition)
                            }
                            webView = this
                        }
                    },
                    update = { w ->
                        if (currentUrl.isNotEmpty() && currentUrl != lastRequested) {
                            lastRequested = currentUrl
                            w.loadUrl(currentUrl)
                        }
                    }
                )
                DisposableEffect(Unit) {
                    onDispose { webView?.destroy() }
                }
            }
        }
    }

    diagText?.let { text ->
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { diagText = null },
            title = { Text("诊断信息") },
            text = {
                Text(
                    text,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.verticalScroll(rememberScrollState())
                )
            },
            confirmButton = { TextButton(onClick = { diagText = null }) { Text("关闭") } }
        )
    }
}
