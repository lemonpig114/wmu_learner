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
                        settings.mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
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
                                }
                            }
                            webChromeClient = WebChromeClient()
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
}
