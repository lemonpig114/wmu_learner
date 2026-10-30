# Codex 复核：教务白屏探针（2026-10-30）

范围：阅读现有白屏分析、阶段03工作记录、BrowserScreen 源码，匿名 GET 认证页与静态脚本。未登录、未使用账号密码、未修改应用代码；adb 当前无设备，未在故障手机上复现。

## 新证据

1. 认证顶层 HTML 包含 `<iframe id="loginIframe"></iframe>` 和备用表单 `#default`。官方站点 `/authserver/cas/js/init.js` 在 onload 中依据 UA 将 iframe 指向 login-mobile.html 或 login-normal.html，并隐藏 #default。外层 index.css 为 html/body/iframe 设置 100% 宽高。
2. 两个 iframe 页均有 `parent.document.title = window.messages[config.locale]['systemname']`。用户截图标题“校园统一身份认证”与子页设置父标题的机制相符，支持子页至少部分脚本已执行；不能据此确认所有资源成功。
3. 当前探针只读取顶层 body.innerText，该值不包含 iframe 文档的文字。HTML 长度 11493、body 子元素 9、文本长度 0 可以出现在正常 iframe 容器上，不能据此认定 WAF 中间页或空响应。
4. onReceivedError/onReceivedHttpError 都仅报告 isForMainFrame 的错误，iframe、JS、CSS 的失败被忽略。readyState complete 不证明这些资源加载成功，也不证明 JS 无异常。
5. 默认 UA 将 Android/Mobile 改写后，按站点现有脚本会选择桌面 iframe；原生 Android UA 会选择移动 iframe。参考项目使用伪装只能证明存在这种做法，无法证明当前白屏由 UA 引起。
6. onCreateWindow 将原 view 填回 transport.webView，复用了父 WebView。Android/Chromium 的新窗口流程应使用新 WebView；这是明确的实现问题，但本次已到达认证页，尚不能证明它是当前白屏的直接原因。

## 结论与下一步

最值得定位的是 iframe 内部加载、脚本执行与实际布局。现有证据不足以确定唯一根因。文档把 mixedContent 当主因、UA 当最可能根因、缺 JSESSIONID 当非正常登录页证据，均应降级为待验证假设。尚未完成登录时缺少教务会话并不解释登录界面白屏，Cookie 名称和数量也不能替代服务端会话校验。

下一轮探针应只采集结构与状态：WebView 内核版本、UA、WebView 实际宽高；#loginIframe 的 src、边界矩形、计算样式；同源 iframe 的 readyState、标题、body 文字长度和表单/输入框数量；#default 可见性；加载失败资源的主机/路径/错误码（去除查询参数）、脚本错误。禁止采集输入框值、密码、Cookie 值或登录后正文。优先比较原生/桌面两种模式的这些指标，再决定修复；不再仅凭顶层 innerText 判断。

可用站点已有 `dev` 参数分支做一次受控对照：init.js 检测 dev 后显示备用表单并移除 iframe；若此分支正常显示，可进一步缩小到 iframe 自定义登录界面。该对照只用于显示诊断，不代表真实登录链路已验证。

来源：
- https://authserver.wmu.edu.cn/authserver/cas/js/init.js
- https://authserver.wmu.edu.cn/authserver/css/index.css
- https://authserver.wmu.edu.cn/authserver/cas/login-normal.html
- https://authserver.wmu.edu.cn/authserver/cas/login-mobile.html
- https://chromium.googlesource.com/chromium/src/+/HEAD/android_webview/docs/how-does-on-create-window-work.md

## v0.2.3 修复尝试（Codex，2026-10-30）

用户授权：“尝试改一下，需要链接设备和我说。”

- 改为 WebView 单窗口模式，由内核将 target=_blank/window.open 作为当前页导航，移除复用父 WebView 的 onCreateWindow 实现。
- 默认原生手机 UA，保留伪装/桌面切换；WebView 使用 Column 剩余高度。
- 增加“简版登录”：仅在 HTTPS authserver.wmu.edu.cn/authserver/login 上添加站点已实现的 dev 参数，保留 service 等现有参数，显示备用表单；不自动提交凭据。
- 自检改为统计顶层及同源 iframe 的 readyState、文本长度、表单数量与 iframe 布局，不采集正文和输入值。补充 WebView 内核版本、UA 与原生尺寸。
- 最近20项诊断覆盖子资源 HTTP/加载错误及控制台错误位置，去除 URL 查询参数，不收集控制台正文。Cookie 名称不再作为登录成功的充分条件。
- 版本 0.2.3 / versionCode 5。是否解决手机上的白屏仍待真机验证。

本轮验证：assembleDebug 成功，apksigner verify 通过；修正本机 PDF 测试路径的 properties 盘符转义后，lintDebug --rerun-tasks 成功。尚无 adb 设备，未安装验证白屏。用户连接设备后可继续安装 0.2.3，先测试默认登录页，再测试“简版登录”，必要时查看新自检。

## v0.2.4 真机定位与修复（Codex，2026-10-30）

设备：Meizu M332BF / Android 16，WebView 143.0.7499.192。通过 adb 覆盖安装，无清库、无账号提交。

v0.2.3 真机复现白屏，自检：原生 WebView 尺寸 1280×1610；认证 iframe login-mobile.html readyState=complete，textLen=49、forms=1、inputs=2，但布局高度为0（宽约394 CSS像素），默认表单隐藏，未捕获资源失败。直接原因确定为认证 iframe 高度坍缩；现有证据不支持将白屏归因为登录失败、缺会话或验证码。

v0.2.4（versionCode 6）：WebView 设置 MATCH_PARENT 布局参数；仅在 HTTPS authserver.wmu.edu.cn/authserver/login 中，将已知 loginIframe 及 html/body 高度按原生视口和 CSS 宽度比例明确设置，在加载完成及布局变化时重算。简版 dev 页面移除 iframe 时不处理。不改页面输入值或提交逻辑。

真机复测：iframe 高度从0变为495.58 CSS像素，宽393.85，页面正常显示学校标识、账号/密码框、登录按钮；关闭诊断后停在登录页供用户手动登录。登录成功与课表获取尚未验证。截图保存在 gitignore 的 .analysis-local/keban-check.png，未采集任何登录输入值。

构建 assembleDebug 成功（36秒）；0.2.4已覆盖安装成功。Lint结果见本轮追加记录。

最终验证：v0.2.4 APK 签名校验通过，lintDebug 成功。用户随后确认“终于好了”，白屏修复得到用户确认；此反馈不等同于课表自动获取已验证。
