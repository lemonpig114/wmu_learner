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
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/**
 * 内置浏览器：教务登录、会话复用及 PDF 下载。
 *
 * 安全边界:
 * - 诊断仅在界面显示，不采集输入值、Cookie 值或正文；不开启远程调试
 * - 禁止 file:// / content:// 访问;拦截非 http(s) 协议跳转
 * - 仅接收 PDF 下载，文件选择未启用
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
    val loadIssues = remember { mutableListOf<String>() }
    val navHistory = remember { mutableListOf<String>() }
    val exportHits = remember { mutableListOf<String>() }
    fun safeAddress(url: String?): String = runCatching {
        val uri = android.net.Uri.parse(url.orEmpty())
        "${uri.scheme}://${uri.host}${uri.path.orEmpty()}"
    }.getOrDefault("(未知地址)")
    /** 记录导航地址(已去除查询参数,避免把会话串写进诊断) */
    fun recordNav(url: String?) {
        val a = safeAddress(url)
        if (a.isBlank() || navHistory.lastOrNull() == a) return
        if (navHistory.size >= 12) navHistory.removeAt(0)
        navHistory.add(a)
    }
    /** 记录与导出/打印/PDF 有关的请求(含 JS 表单 POST —— 这类请求不会触发页面导航) */
    fun recordExportHit(url: String, method: String) {
        val low = url.lowercase()
        val hit = low.contains("pdf") || low.contains("print") || low.contains("export") ||
            low.contains("shcpdf") || low.contains("/drdc/") || low.contains("dcclbh")
        if (!hit) return
        val a = "$method ${safeAddress(url)}"
        if (!exportHits.contains(a)) {
            if (exportHits.size >= 12) exportHits.removeAt(0)
            exportHits.add(a)
        }
    }
    fun recordIssue(issue: String) {
        if (loadIssues.size >= 20) loadIssues.removeAt(0)
        loadIssues.add(issue)
    }
    fun sizeAuthFrame(view: WebView) {
        val uri = android.net.Uri.parse(view.url.orEmpty())
        if (uri.scheme != "https" || uri.host != JwxtClient.AUTH_SERVER_HOST ||
            uri.path != "/authserver/login" || view.width <= 0 || view.height <= 0) return
        // 此认证页在真机 WebView 中百分比高度坍缩：iframe 已加载但高度为 0。
        // 按原生视口换算 CSS 像素，限定修复认证页的已知容器。
        view.evaluateJavascript("""
            (function(){
              var f=document.getElementById('loginIframe');
              if(!f) return;
              var h=${view.height} * (window.innerWidth / ${view.width});
              if(!(h>0)) return;
              document.documentElement.style.height=h+'px';
              document.body.style.height=h+'px';
              f.style.display='block';
              f.style.height=h+'px';
              f.style.width='100%';
            })()
        """.trimIndent(), null)
    }
    // 默认原生 UA，让认证站点选择手机登录页；保留其他模式供对照。
    var uaMode by remember { mutableStateOf(2) }
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
            append("当前地址:${safeAddress(currentShownUrl)}\n\n")
            append("jwxt.wmu.edu.cn Cookie:${names(jwxt)}\n")
            append("authserver Cookie:${names(auth)}\n\n")
            append(if (loggedIn) "判断:看起来已进入教务内页(登录可能已成功)" else "判断:仍在登录相关页面(登录可能未完成)")
            append("\n\n提示:Cookie 名称不能证明登录成功，需由教务服务端验证会话。")
            append("\nWebView:${WebView.getCurrentWebViewPackage()?.versionName ?: "未知"}")
            append("\n尺寸:${webView?.width} × ${webView?.height}")
            append("\nUA:${webView?.settings?.userAgentString}")
            append("\n\n最近加载问题:\n${loadIssues.joinToString("\n").ifBlank { "未捕获" }}")
            append("\n\n最近访问(已去查询参数):\n${navHistory.joinToString("\n").ifBlank { "(无)" }}")
            append("\n\n导出相关请求(已去查询参数):\n${exportHits.joinToString("\n").ifBlank { "(未捕获)" }}")
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
                function info(d){
                  var b=d.body;
                  return {ready:d.readyState, textLen:b?(b.innerText||'').length:-1,
                    forms:d.forms.length, inputs:d.querySelectorAll('input').length};
                }
                function layout(e){
                  if(!e) return null;
                  var r=e.getBoundingClientRect(), s=getComputedStyle(e);
                  return {width:r.width,height:r.height,x:r.x,y:r.y,
                    display:s.display,visibility:s.visibility,opacity:s.opacity};
                }
                var frames=Array.from(document.querySelectorAll('iframe')).map(function(f){
                  var u; try{u=new URL(f.src,location.href); }catch(e){}
                  var v={src:u?u.origin+u.pathname:'',layout:layout(f)};
                  try{v.document=f.contentDocument?info(f.contentDocument):'不可访问';}
                  catch(e){v.document='跨域不可访问';}
                  return v;
                });
                // 导出入口证据:扫描本页及同源 iframe 的 HTML 结构,只取结构与属性,不取任何输入值
                function scanExport(root){
                  var h;
                  try{ h=root.documentElement.outerHTML; }catch(e){ return null; }
                  var out={onclick:[],kw:[],urls:[],forms:[]},seen={},m,r;
                  function add(arr,v,cap){ v=(v||'').replace(/\s+/g,' ').trim(); if(v&&!seen[v]&&arr.length<cap){seen[v]=1;arr.push(v);} }
                  seen={};
                  r=/onclick="([^"]{0,140})"/gi;
                  while((m=r.exec(h))!==null) add(out.onclick,m[1],18);
                  seen={};
                  r=/<[^<>]{0,260}?(?:导出|打印|下载|PDF|生成)[^<>]{0,200}?>/gi;
                  while((m=r.exec(h))!==null) add(out.kw,m[0],12);
                  seen={};
                  r=/(\/jwglxt\/[A-Za-z0-9_\/.\-]{0,120})/g;
                  while((m=r.exec(h))!==null) add(out.urls,m[1],25);
                  r=/<form[^>]{0,300}>/gi;
                  while((m=r.exec(h))!==null&&out.forms.length<5) out.forms.push(m[0].replace(/\s+/g,' '));
                  return out;
                }
                return JSON.stringify({top:info(document),frames:frames,
                  defaultForm:layout(document.getElementById('default')),
                  export:scanExport(document)});
              }catch(e){ return JSON.stringify({error: String(e)}); }
            })()
        """.trimIndent()
        w.evaluateJavascript(probe) { raw ->
            val pretty = runCatching {
                val obj = org.json.JSONObject(org.json.JSONTokener(raw).nextValue() as String)
                "\n\n【页面结构探针（含 iframe）】\n" + obj.toString(2)
            }.getOrElse { "\n\n【页面结构探针】解析失败" }
            diagText = basic + pretty
        }
    }

    /**
     * 在页面内挂钩:捕获页面**自己**发出的表单提交 / XHR。
     * 只记录方法、地址与字段;字段值仅用于原样重放,不写入诊断。
     * 动机:课表页「输出 PDF」发出的是 POST,而下载拦截只拿得到 URL、拿不到请求体。
     */
    fun installCaptureHooks(w: WebView) {
        val js = """
            (function(){
              if(window.__kbHook) return 'ok';
              window.__kbHook=true; window.__kbForm=null; window.__kbXhr=null;
              try{
                var os=HTMLFormElement.prototype.submit;
                HTMLFormElement.prototype.submit=function(){
                  try{
                    var f=this,o={action:f.getAttribute('action')||location.href,
                      method:(f.getAttribute('method')||'GET').toUpperCase(),fields:{}};
                    var es=f.elements;
                    for(var i=0;i<es.length;i++){var e=es[i];if(!e.name)continue;
                      if(e.type==='checkbox'||e.type==='radio'){if(e.checked)o.fields[e.name]=e.value;}
                      else o.fields[e.name]=e.value;}
                    window.__kbForm=JSON.stringify(o);
                  }catch(e){}
                  return os.apply(this,arguments);
                };
              }catch(e){}
              try{
                var oo=XMLHttpRequest.prototype.open, se=XMLHttpRequest.prototype.send;
                XMLHttpRequest.prototype.open=function(m,u){
                  this.__kbM=(m||'GET').toUpperCase(); this.__kbU=String(u);
                  return oo.apply(this,arguments);};
                XMLHttpRequest.prototype.send=function(b){
                  try{
                    if(this.__kbU && /pdf|print|export|shcpdf|drdc|kbcx/i.test(this.__kbU))
                      window.__kbXhr=JSON.stringify({method:this.__kbM,url:this.__kbU,
                        body:(typeof b==='string'?b:null)});
                  }catch(e){}
                  return se.apply(this,arguments);};
              }catch(e){}
              return 'hooked';
            })()
        """.trimIndent()
        w.evaluateJavascript(js, null)
    }

    /** 读取页面内捕获到的真实导出请求,并按原样重放(同方法、同地址、同字段) */
    fun captureAndReplayExport() {
        val w = webView
        if (w == null) {
            browserMessage = "页面尚未就绪"
            return
        }
        w.evaluateJavascript(
            "(function(){return JSON.stringify({form:window.__kbForm||null,xhr:window.__kbXhr||null});})()"
        ) { raw ->
            val obj = runCatching {
                org.json.JSONObject(org.json.JSONTokener(raw).nextValue() as String)
            }.getOrNull()
            val formStr = obj?.optString("form")?.takeIf { it.isNotBlank() && it != "null" }
            val xhrStr = obj?.optString("xhr")?.takeIf { it.isNotBlank() && it != "null" }
            if (formStr == null && xhrStr == null) {
                browserMessage = "未捕获到导出请求:请先在页面上点一次「输出 PDF」"
                return@evaluateJavascript
            }
            var url = ""
            var method = "POST"
            var fields: Map<String, String>? = null
            var rawBody: String? = null
            var ajax = false
            val names = StringBuilder()
            if (formStr != null) {
                val f = org.json.JSONObject(formStr)
                url = f.optString("action")
                method = f.optString("method", "POST").ifBlank { "POST" }
                val fm = linkedMapOf<String, String>()
                f.optJSONObject("fields")?.let { fo -> fo.keys().forEach { k -> fm[k] = fo.optString(k) } }
                fields = fm
                names.append("捕获到页面表单提交\n地址:${safeAddress(url)}\n方法:$method\n")
                names.append("字段名(${fm.size}):${fm.keys.joinToString(", ")}\n(字段值不上报)\n")
            } else if (xhrStr != null) {
                val x = org.json.JSONObject(xhrStr)
                url = x.optString("url")
                method = x.optString("method", "POST").ifBlank { "POST" }
                rawBody = x.optString("body").takeIf { it.isNotBlank() && it != "null" }
                ajax = true
                names.append("捕获到页面 XHR\n地址:${safeAddress(url)}\n方法:$method\n")
                names.append(
                    "请求体字段名:" +
                        (rawBody?.split('&')?.map { it.substringBefore('=') }?.joinToString(", ") ?: "(空)") +
                        "\n(字段值不上报)\n"
                )
            }
            val absUrl = if (url.startsWith("http")) url else JwxtClient.JWXT + url
            browserMessage = "正在按页面真实请求重放导出…"
            scope.launch {
                val out = withContext(Dispatchers.IO) {
                    runCatching {
                        JwxtClient(
                            userAgent = JwxtClient.wmuUserAgent(WebSettings.getDefaultUserAgent(ctx))
                        ).replayExport(
                            absUrl, method, fields, rawBody, ajax,
                            CookieManager.getInstance().getCookie(absUrl), currentShownUrl
                        )
                    }.getOrElse {
                        JwxtClient.ReplayOutcome(false, null, "重放失败:${it.javaClass.simpleName} ${it.message}")
                    }
                }
                val bytes = out.bytes
                if (out.ok && bytes != null) {
                    val f = withContext(Dispatchers.IO) {
                        File(ctx.cacheDir, "pending_import.pdf").apply { writeBytes(bytes) }
                    }
                    ImportHandoff.pendingPdf = f
                    browserMessage = "已获取课表 PDF,请切到「课表」页确认导入"
                } else {
                    browserMessage = "导出未拿到 PDF,详情见诊断"
                    diagText = names.toString() + "\n" + out.message
                }
            }
        }
    }

    /**
     * 让页面自己走它的导出流程:点击页面里已存在的导出按钮(证据:课表页有 id="shcPDF" 的按钮)。
     * 不猜接口、不伪造请求 —— 触发后由 DownloadListener 或导航记录接管,拿到的就是网站的真实产物。
     */
    fun clickPageExport() {
        val w = webView
        if (w == null) {
            browserMessage = "页面尚未就绪"
            return
        }
        val js = """
            (function(){
              try{
                var b=document.getElementById('shcPDF');
                if(b){ b.click(); return '已点击导出按钮(shcPDF)'; }
                var all=document.querySelectorAll('button,a,input');
                for(var i=0;i<all.length;i++){
                  var t=(all[i].textContent||all[i].value||'').trim();
                  if(t.indexOf('PDF')>=0||t.indexOf('导出')>=0||t.indexOf('打印')>=0){
                    all[i].click(); return '已点击文本匹配按钮:'+t.slice(0,24);
                  }
                }
                return '页面上没找到导出按钮';
              }catch(e){ return '点击失败:'+e; }
            })()
        """.trimIndent()
        w.evaluateJavascript(js) { raw ->
            val res = runCatching {
                org.json.JSONTokener(raw).nextValue() as String
            }.getOrElse { raw ?: "" }
            browserMessage = "$res;正在捕获导出请求…"
            // 等页面把导出请求发出去,再读捕获结果并原样重放(拿得到 POST 体,下载拦截拿不到)
            scope.launch {
                delay(1500)
                captureAndReplayExport()
            }
        }
    }

    /**
     * 同构取课表数据:直接复用**页面自己的查询表单**(action 与字段名都来自页面,不是我编的),
     * 用当前登录会话 POST 一次,只回报响应结构(字段名),不回报任何课程数据值。
     */
    fun probeTimetableData() {
        val w = webView
        if (w == null) {
            browserMessage = "页面尚未就绪"
            return
        }
        val cookie = CookieManager.getInstance().getCookie(JWXT_TIMETABLE_PAGE).orEmpty()
        if (cookie.isBlank()) {
            browserMessage = "尚未检测到登录会话,请先登录"
            return
        }
        val js = """
            (function(){
              try{
                var f=document.getElementById('ajaxForm')||document.forms[0];
                if(!f) return JSON.stringify({error:'页面上没有表单'});
                var out={action:f.getAttribute('action')||'',fields:{}};
                var els=f.elements;
                for(var i=0;i<els.length;i++){
                  var e=els[i]; if(!e.name) continue;
                  if(e.type==='checkbox'||e.type==='radio'){ if(e.checked) out.fields[e.name]=e.value; }
                  else out.fields[e.name]=e.value;
                }
                return JSON.stringify(out);
              }catch(e){ return JSON.stringify({error:'读取表单失败:'+e}); }
            })()
        """.trimIndent()
        w.evaluateJavascript(js) { raw ->
            val parsed = runCatching {
                org.json.JSONObject(org.json.JSONTokener(raw).nextValue() as String)
            }.getOrNull()
            val action = parsed?.optString("action").orEmpty()
            if (parsed == null || action.isBlank()) {
                diagText = "读取页面表单失败:${raw?.take(200)}"
                return@evaluateJavascript
            }
            val fields = linkedMapOf<String, String>()
            parsed.optJSONObject("fields")?.let { fo ->
                fo.keys().forEach { k -> fields[k] = fo.optString(k) }
            }
            browserMessage = "正在用页面表单取课表数据…"
            scope.launch {
                val summary = withContext(Dispatchers.IO) {
                    runCatching {
                        JwxtClient(
                            userAgent = JwxtClient.wmuUserAgent(WebSettings.getDefaultUserAgent(ctx))
                        ).probeTimetableData(JWXT_TIMETABLE_PAGE, action, fields, cookie)
                    }.getOrElse { "请求失败:${it.javaClass.simpleName} ${it.message}" }
                }
                val sentFields = fields.keys.joinToString(", ")
                diagText = "表单 action:$action\n提交字段名:$sentFields\n(字段值不上报)\n\n$summary"
                browserMessage = null
            }
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
                // 让页面自己走它的导出流程:点了之后由 DownloadListener 或导航记录接管(不猜接口)
                TextButton(onClick = { clickPageExport() }) { Text("点页面「导出PDF」") }
                TextButton(onClick = { probeTimetableData() }) { Text("取课表数据") }
            }
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(onClick = {
                    val uri = android.net.Uri.parse(currentShownUrl)
                    if (uri.scheme == "https" && uri.host == JwxtClient.AUTH_SERVER_HOST &&
                        uri.path == "/authserver/login") {
                        val fallback = uri.buildUpon().clearQuery()
                        uri.queryParameterNames.filter { it != "dev" }.forEach { key ->
                            uri.getQueryParameters(key).forEach { fallback.appendQueryParameter(key, it) }
                        }
                        currentUrl = fallback.appendQueryParameter("dev", "true").build().toString()
                        webView?.loadUrl(currentUrl)
                        lastRequested = currentUrl
                    } else {
                        browserMessage = "请先到达统一身份认证登录页，再切换简版登录"
                    }
                }) { Text("简版登录") }
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
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    factory = { ctx ->
                        WebView(ctx).apply {
                            layoutParams = android.view.ViewGroup.LayoutParams(
                                android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                                android.view.ViewGroup.LayoutParams.MATCH_PARENT
                            )
                            addOnLayoutChangeListener { view, _, _, _, _, _, _, _, _ ->
                                sizeAuthFrame(view as WebView)
                            }
                            settings.javaScriptEnabled = true
                            settings.domStorageEnabled = true
                            settings.allowFileAccess = false
                            settings.allowContentAccess = false
                            // 教务站点在 https 页面里混用 http(CAS 回跳/内嵌),参考项目(WakeUp fork)同样
                            // 使用 MIXED_CONTENT_ALWAYS_ALLOW;本页已禁用 file/content 访问、拦截非 http(s) 跳转
                            settings.mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                            // 登录页可能用 window.open / target=_blank 打开统一身份认证
                            settings.javaScriptCanOpenWindowsAutomatically = true
                            // 单窗口模式让 target=_blank / window.open 成为当前页导航。
                            settings.setSupportMultipleWindows(false)
                            // 页面按宽度自适应,避免固定宽度登录页渲染成空白
                            settings.useWideViewPort = true
                            settings.loadWithOverviewMode = true
                            settings.textZoom = 100
                            // UA 与界面选择一致，默认使用原生手机 UA。
                            settings.userAgentString = uaFor(uaMode, WebSettings.getDefaultUserAgent(ctx))
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
                                    sizeAuthFrame(view)
                                    // 教务页装挂钩:用于捕获页面自己发出的导出请求
                                    if ((url ?: "").contains("jwxt.wmu.edu.cn")) installCaptureHooks(view)
                                }

                                override fun onPageStarted(view: WebView, url: String?, favicon: android.graphics.Bitmap?) {
                                    super.onPageStarted(view, url, favicon)
                                    loadIssues.clear()
                                    currentShownUrl = url ?: currentShownUrl
                                    recordNav(url)
                                }

                                /** 把"白屏"变成可诊断信息 */
                                override fun onReceivedError(
                                    view: WebView,
                                    request: WebResourceRequest,
                                    error: android.webkit.WebResourceError
                                ) {
                                    super.onReceivedError(view, request, error)
                                    recordIssue("加载 ${error.errorCode}: ${safeAddress(request.url.toString())}")
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
                                    recordIssue("HTTP ${errorResponse.statusCode}: ${safeAddress(request.url.toString())}")
                                    if (request.isForMainFrame) {
                                        browserMessage = "页面返回 HTTP ${errorResponse.statusCode}(url=${request.url})"
                                    }
                                }

                                /** 记录与导出相关的请求(含 JS 表单 POST,这类请求不触发导航,只能在此看到) */
                                override fun shouldInterceptRequest(
                                    view: WebView,
                                    request: WebResourceRequest
                                ): android.webkit.WebResourceResponse? {
                                    recordExportHit(request.url.toString(), request.method)
                                    return null
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
                                override fun onConsoleMessage(
                                    message: android.webkit.ConsoleMessage
                                ): Boolean {
                                    // 不收集控制台正文，避免页面输出账号或令牌。
                                    if (message.messageLevel() == android.webkit.ConsoleMessage.MessageLevel.ERROR) {
                                        recordIssue("脚本错误: ${safeAddress(message.sourceId())}:${message.lineNumber()}")
                                    }
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
