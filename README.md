# 课伴 KeBan

面向温州医科大学学生的安卓原生课程助手(AI 黑客松项目)。

## 定位与范围(当前版本)

**首版聚焦**:导入温州医科大学课表 PDF → 解析 → 预览校正 → 确认保存 → 显示周课表,并支持本地待办管理。
**明确不在本期**:学习通 / 知到 / 在浙学接入、AI 通知转待办(后续阶段再做)。

## 技术栈

Kotlin + Jetpack Compose(Material3)+ Room + WebView + Navigation Compose
构建:Gradle 9.6.0 wrapper / AGP 9.4.1 / JDK 21 / compileSdk 37.1 / minSdk 26

## 构建

```bash
./gradlew assembleDebug
# 产物: app/build/outputs/apk/debug/app-debug.apk
```

需要本机 Android SDK(`local.properties` 指向,不入 Git)。

## 模块结构

```
app/src/main/java/com/wmu/keban/
├── MainActivity.kt            # 单 Activity 入口
├── data/                     # Room:TaskItem、Setting、AppDatabase、KebanRepo、Graph
├── ui/
│   ├── KebanApp.kt           # 底部导航 + NavHost(今天/课表/待办/我的 + 浏览器)
│   ├── today/                # 今天页:日期、教学周、近期待办
│   ├── timetable/            # 课表页(第一阶段空状态,第二阶段实现导入)
│   ├── todos/                # 待办页:新增/编辑/完成/删除,日期时间选择
│   ├── profile/              # 我的页:学期起始日、内置浏览器入口、阶段进度
│   ├── browser/              # 内置浏览器(WebView,第三阶段教务登录)
│   └── common/               # 复用组件(分区卡片、待办行、示例徽章)
└── util/Dates.kt             # 日期/教学周工具
```

## 安全与隐私红线

- 密码、Cookie、密钥**不写入**日志、Git、AI 提示词
- 个人课表 PDF **不入 Git**(`.gitignore` 排除 `*.pdf` 与分析目录)
- WebView 与原生能力隔离:仅 INTERNET 权限,禁 file://、content://,拦截非 http(s) 协议跳转,不记录 Cookie 与浏览历史
- 缺失的截止时间一律显示「未设置」,**不做猜测填充**
- 示例数据明确标注(isSample + 「示例」徽章),与真实数据分开
- 同步失败保留旧数据并显示原因(后续阶段实现时遵循)

## 进度与记录

- 工程进度:见 [PROGRESS.md](PROGRESS.md)
- 作品记录(黑客松 AI 合规,含提示词与 AI 使用记录):见 [docs/worklog/](docs/worklog/README.md)
- 课表 PDF 格式分析结论(脱敏):见 worklog 阶段 00 记录

