# 课伴 KeBan

面向温州医科大学学生的安卓原生课程助手(AI 黑客松项目)。

仓库:<https://github.com/lemonpig114/wmu_learner>

当前版本:**v0.5.0**(versionCode 13),可安装的 debug APK 见 `docs/课伴-v0.5.0-debug.apk`(本地交付物,未入库)。

## 定位与范围(当前版本)

**首版聚焦**:导入温州医科大学课表 PDF → 解析 → 预览校正 → 确认保存 → 显示周课表,并支持本地待办管理。
**已实现(超出首版)**:教务统一身份认证登录(自动填入账号密码)、课表 PDF 一键获取、教学周自动校准、
节次时间表(温医大真实作息默认值)、今日课程。
**明确不在本期**:学习通 / 知到 / 在浙学接入、AI 通知转待办(后续阶段再做)。

## 三处一次设置(开箱即用)

| 设置项 | 位置 | 默认值 |
|---|---|---|
| 学期起始日 | 我的 → 学期设置 | **9 月 7 日**(第 1 周周一),无需手动设置 |
| 节次作息 | 我的 → 节次时间表 | **温医大真实作息第 1~17 节**(每节 40/35 分钟 + 课间休息) |
| 教务账号 | 我的 → 教务账号 | 可选;配置后登录页自动填入(含防锁定保护) |

## 获取课表的三种方式

1. **一键获取**(推荐):课表页点「一键获取」→ 自动打开教务课表页 → 点击页面「输出 PDF」
   → 捕获网站真实请求并重放 → 自动返回并弹出解析预览
2. **内置浏览器手动导出**:在页面里自己点「输出 PDF」,应用会拦截并接住 PDF
3. **电脑导出后导入**:任意来源的课表 PDF → 课表页「导入」

> 原理说明(不猜接口):导出走的是 `POST /jwglxt/kbcx/xskbcx_cxXsShcPdf.html`,由**真机网络请求实测**得到。
> 应用在页面内挂钩捕获网站自己发出的请求(表单提交 / XHR),再按原方法、原地址、原字段重放,
> 因此网站改版时应用无需改动参数。

## 技术栈

Kotlin + Jetpack Compose(Material3)+ Room + WebView + Navigation Compose
构建:Gradle 9.6.0 wrapper / AGP 9.4.1 / JDK 21 / compileSdk 37.1 / minSdk 26

## 构建

```bash
./gradlew assembleDebug        # 出包
./gradlew :app:testDebugUnitTest   # 单元测试(当前 39 项)
# 产物: app/build/outputs/apk/debug/app-debug.apk
```

需要本机 Android SDK(`local.properties` 指向,不入 Git)。

## 模块结构

```
app/src/main/java/com/wmu/keban/
├── MainActivity.kt            # 单 Activity 入口
├── data/                      # Room:待办/课程/时段/导入记录/设置 + 仓库 + Graph
├── security/SecureStore.kt    # Android Keystore AES-256/GCM 加密存储账号密码
├── jwxt/JwxtClient.kt         # 教务客户端:SSO 登录、会话复用、原样重放导出请求
├── pdf/                       # 课表 PDF 解析(Weeks / 解析核心 / 提取器;纯 Kotlin,可在 JVM 单测)
├── ui/
│   ├── KebanApp.kt            # 底部导航 + NavHost(今天/课表/待办/我的 + 浏览器)
│   ├── today/                 # 今天页:日期、教学周、今日课程、近期待办
│   ├── timetable/             # 课表页:导入/一键获取、预览校正、周课表网格
│   ├── todos/                 # 待办页:新增/编辑/完成/删除
│   ├── profile/               # 我的页:学期设置、节次时间表、教务账号、课表数据
│   ├── browser/               # 内置浏览器:登录、自动填入、一键导出、诊断
│   └── common/                # 复用组件
└── util/                      # Dates(教学周/默认学期起始日)、SectionTimes(作息与生成)
```

## 安全与隐私红线

- 密码、Cookie、密钥**不写入**日志、Git、AI 提示词
- 账号密码经 Android Keystore 加密后仅存本机,可一键清除;自动提交带**防锁定保护**
- 个人课表 PDF **不入 Git**(`.gitignore` 排除 `*.pdf` 与分析目录)
- WebView 与原生能力隔离:仅 INTERNET 权限,禁 file://、content://,拦截非 http(s) 协议跳转,不记录 Cookie 值
- 诊断信息:学号打码、Cookie 只列名称、URL 去查询参数、字段只列**名**不列值
- 缺失的信息一律显示「未设置」,**不做猜测填充**(截止时间、节次时间、未提供的节次均如此)
- 示例数据明确标注(isSample + 「示例」徽章),与真实数据分开

## 进度与记录

- 工程进度:见 [PROGRESS.md](PROGRESS.md)
- 课表导入指引(含常见问题):见 [docs/课表导入指引.md](docs/课表导入指引.md)
- 作品记录(黑客松 AI 合规,含提示词与 AI 使用记录):见 [docs/worklog/](docs/worklog/README.md)
- 白屏问题根因分析(含参考开源项目记录):见 [docs/教务登录白屏-原因分析与处理建议.md](docs/教务登录白屏-原因分析与处理建议.md)

