> **Codex 修复更新（2026-10-30）**：下方为历史交接记录，安装 SDK 并非唯一问题。
> 已完成：修正本机 Gradle 失效代理端口（55880 → 当前 mihomo 的 7890）、安装完整 Android SDK Platform 37.1、配置 compileSdk 37.1（targetSdk 36 / minSdk 26）、修复仓库构造函数可见性、Card 导入、WebView 混合内容常量与 TaskEditDialog 实验 API 声明。当前已通过 SDK 依赖校验及 Kotlin 编译，完整验证结果见 PROGRESS.md。
> 构建命令：`.\gradlew.bat assembleDebug`。代理属于本机全局配置，不写入项目。真机未连接，运行行为尚未验证。
# 课伴 KeBan — 构建链交接文档(2026-10-30)

> 本文件由 WorkBuddy 生成。交接原因:**构建链最后一公里(Android SDK Platform 37 安装)转由用户人工处理**。
> 黑客松合规用的作品阶段记录(含提示词原文与 AI 使用记录)见 `docs/worklog/`。

## 0. 一句话现状

工程代码已全部写完(四页面、Room 待办、内置浏览器、示例数据策略、图标、清单),构建链已打通到 **依赖校验阶段**,唯一卡点是:**本机缺少 Android SDK Platform 37**——所选依赖(Compose BOM 2026.09.00 等)要求 compileSdk ≥ 37,而本机 SDK 只装到 android-36.1。
装好平台后一条命令出包:

```bash
cd C:/Users/11257/Documents/wmu_learner
./gradlew assembleDebug
# 产物:app/build/outputs/apk/debug/app-debug.apk
```

---

## 1. 你要人工做的最后一步

### 推荐:Android Studio 图形化安装
打开 Android Studio → **Settings → Android SDK**(或 SDK Manager)→ SDK Platforms → 勾选 **Android SDK Platform 37.1** → Apply。

### 或:命令行安装(注意本机代理)
`cmd` / PowerShell 中:

```bat
set JAVA_TOOL_OPTIONS=-Dhttp.proxyHost=127.0.0.1 -Dhttp.proxyPort=55880 -Dhttps.proxyHost=127.0.0.1 -Dhttps.proxyPort=55880
"%LOCALAPPDATA%\Android\Sdk\cmdline-tools\latest\bin\sdkmanager.bat" "platforms;android-37.1"
```

> 我此前命令行安装被中断(SIGTERM);SDK 仓库已确认有 `platforms;android-37.0` 和 `platforms;android-37.1` 两个包,装 37.1 即可。

### 为什么是装平台,而不是降依赖

- 报错原文(多次一致):`Dependency 'androidx.compose.runtime:runtime-saveable-android:1.12.1' requires ... compile against version 37 or later`,同类要求还来自 `core-ktx:1.19.1`、`lifecycle:2.11.0`、`compose.ui:1.12.1` 等。
- 备选 Plan B(不推荐):不装 37,改为降级依赖——Compose BOM→2026.06.01、core-ktx→1.17.0、navigation→2.9.x、activity→1.11.x 等,需逐项降并重试;这会偏离"兼容且当前"的选型目标。装平台一次到位,AS 2025.3.3 完全支持。

---

## 2. 环境实测记录(2026-10-30)

| 项 | 实测值 |
|----|--------|
| JDK | Oracle **21.0.9** LTS(PATH 中) |
| Android SDK | `C:/Users/11257/AppData/Local/Android/Sdk` |
| 已装 platforms | android-34、android-36.1(**缺 37**) |
| build-tools | 34.0.0 / 36.1.0 / 37.0.0 |
| cmdline-tools | latest(可用 sdkmanager) |
| licenses | android-sdk-license 已接受 |
| Gradle | 项目 wrapper **9.6.0**(见问题 4);本机还缓存 9.3.0/9.4.1 |
| Android Studio | **2025.3.3**(`D:/Install/android_stu`,JBR 21.0.10) |
| adb | v37.0.0 正常;**当前无真机连接** |
| 网络 | 本机代理 `127.0.0.1:55880`(curl/git 认环境变量;**Java 不认**,见问题 3) |

## 3. 依赖版本(`gradle/libs.versions.toml`,全部经仓库元数据核实于 2026-10-30)

| 组件 | 版本 |
|------|------|
| AGP | 9.4.1 |
| Kotlin | 2.4.21(仅 compose 插件使用;KGP 经 classpath 覆盖) |
| KSP | 2.3.12 |
| Compose BOM | 2026.09.00 |
| Room | 2.8.5 |
| Navigation Compose | 2.10.2 |
| activity-compose | 1.13.0 |
| core-ktx | 1.19.1 |
| material-icons-core | 1.7.8 |
| Gradle wrapper | 9.6.0 |

构建参数:`compileSdk = 36`(待升 37)、`targetSdk = 36`、`minSdk = 26`、versionName 0.1.0。

## 4. 已解决的问题清单(处理记录)

1. **`gradle wrapper` 任务失败**:`settings.gradle.kts` 里 `include(":app")` 但 `app/` 目录不存在(Gradle 9 的强制检查)。
   → 处理:先建 `app/` 目录结构再跑 wrapper,17 秒成功。
2. **wrapper 重复下载官方发行包并超时**:本机已有的 Gradle 9.4.1 缓存来自镜像 URL,hash 目录(`45w7kj...`)与官方 URL 的 hash(`arn2x92...`)不一致,wrapper 认不出缓存。
   → 处理:把缓存的 `gradle-9.4.1/` 目录复制进新 hash 目录并补 `.ok`/`.lck` 标记,零下载复用。
3. **Java/Gradle 不读 curl 的代理环境变量**:直连官方源超时。
   → 处理:`~/.gradle/gradle.properties` 写入 `systemProp.http(s).proxyHost/Port=127.0.0.1:55880`(**本机全局配置,不入 Git;换网络环境请自行调整或删除**)。
4. **AGP 9.4.1 要求 Gradle ≥ 9.6.0**(原假设 AGP 9.4↔Gradle 9.4 不成立)。
   → 处理:wrapper 升到 9.6.0;官方源 10s 超时扛不住 134MB,改用**腾讯镜像**手动下载 `gradle-9.6.0-bin.zip` 放入 wrapper hash 目录(`42k10rwplmzkhuboz9kdazi7s`),38 秒完成。
5. **AGP 9 内置 Kotlin,禁用 `org.jetbrains.kotlin.android` 插件**(报错明示移除,链接 kotl.in/gradle/agp-built-in-kotlin)。
   → 处理:根 + app 两处移除该插件;移除 `kotlin { compilerOptions { jvmTarget } }` 块(内置 Kotlin 跟随 `compileOptions` 的 Java 17);保留 `org.jetbrains.kotlin.plugin.compose:2.4.21` 与 `com.google.devtools.ksp:2.3.12`(compose 插件会传递引入 KGP 2.4.21,高于内置基线 2.2.10,符合官方升级方式)。
6. **当前卡点(未解决)**:依赖要求 compileSdk ≥ 37,本机缺 Platform 37。最后一次构建已执行 14 个任务并在 AAR 元数据校验阶段失败——说明**插件链、KSP、Kotlin 配置均已通过**,只差平台。

## 5. 尚未验证项(不打包票)

- KSP 2.3.12 × Kotlin 2.4.21 的完整编译(上次构建已通过插件应用与依赖解析,编译任务未跑到);
- material3 `DatePicker`/`TimePicker` 等 API 在 BOM 2026.09 下的最终可用性(编译期确认);
- App 图标矢量与四页面运行时行为——待真机/模拟器冒烟;
- 示例数据(seedSampleIfNeeded)逻辑已审查,真机行为待验证。

## 6. 工程结构(代码现状)

```
C:/Users/11257/Documents/wmu_learner/
├── .gitignore                 # 排除构建产物、密钥、*.pdf、.analysis-local/
├── settings.gradle.kts        # 仓库配置(google/mavenCentral)
├── build.gradle.kts           # 根:plugins apply false
├── gradle.properties
├── local.properties           # 本机 SDK 路径(不入 Git)
├── gradle/libs.versions.toml  # 版本目录(见第 3 节)
├── gradle/wrapper/            # 9.6.0 wrapper
├── gradlew / gradlew.bat
├── README.md                  # 项目说明
├── PROGRESS.md                # 工程进度记录
├── HANDOVER.md                # 本文件
├── docs/worklog/              # 黑客松 AI 合规记录(含提示词原文)
└── app/
    ├── build.gradle.kts       # 模块配置(已按 AGP9 内置 Kotlin 调整)
    ├── proguard-rules.pro
    └── src/main/
        ├── AndroidManifest.xml
        ├── res/               # 图标(日历+对勾矢量)、主题、字符串
        └── java/com/wmu/keban/
            ├── MainActivity.kt
            ├── data/          # TaskItem、Setting、DAO、AppDatabase、KebanRepo、Graph
            ├── util/Dates.kt
            └── ui/            # KebanApp(导航)、today、timetable、todos、profile、browser、common、theme
```

另:`.analysis-local/`(不入 Git)内存放真实课表 PDF 分析转储与 WakeUp 参考仓库浅克隆。

## 7. 隐私与安全约束(已在代码与流程中落实)

- `*.pdf`、`.analysis-local/`、密钥类文件不进 Git;
- WebView:仅 INTERNET 权限;禁 `file://`/`content://`;拦截非 http(s) 跳转;不记录 Cookie/凭据/历史;
- 示例数据 `isSample=true` + UI「示例」徽章,与真实数据分开,可删除;
- 缺失截止时间一律显示「未设置」,不猜测填充。

## 8. 待你处理/提供的事项

| # | 事项 | 说明 |
|---|------|------|
| 1 | 安装 Android SDK Platform 37.1 | 见第 1 节;完成后跑 `./gradlew assembleDebug` 即可出 APK |
| 2 | GitHub 仓库地址 | 编译通过后做 `git init` + 首次提交 + `phase-1` 标签,再关联远程 |
| 3 | 真机连接 | 手机开 USB 调试连电脑,`adb devices` 可见后我可继续真机验证 |
| 4 | 截图补充 | `docs/worklog/` 阶段 00/01 中留有产品截图与 WorkBuddy 会话截图占位,按清单补拍 |

## 9. 如果你要我继续

装完 37.1 后回来说一声「继续」,我会:构建 → 迭代报错(若有)→ 验证 APK → git 提交 + 标签 → 回填作品记录与进度文档。

