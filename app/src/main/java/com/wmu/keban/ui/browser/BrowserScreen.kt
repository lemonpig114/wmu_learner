package com.wmu.keban.ui.browser

import android.annotation.SuppressLint
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
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.wmu.keban.ui.common.EmptyHint

/**
 * 内置浏览器(第一阶段:基础浏览)
 *
 * 安全边界:
 * - 不记录、不上传任何 URL、Cookie、凭据;不开启远程调试
 * - 禁止 file:// / content:// 访问;拦截非 http(s) 协议跳转
 * - 下载与文件选择未启用:第三阶段按教务实际流程实现
 */
@SuppressLint("SetJavaScriptEnabled")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BrowserScreen() {
    var inputUrl by rememberSaveable { mutableStateOf("") }
    var currentUrl by rememberSaveable { mutableStateOf("") }
    var canGoBack by remember { mutableStateOf(false) }
    var webView by remember { mutableStateOf<WebView?>(null) }
    var lastRequested by remember { mutableStateOf<String?>(null) }

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
                            "当前:可打开任意公开网页(手动输入地址)\n" +
                            "第三阶段:用于教务入口登录与课表 PDF 下载\n\n" +
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
