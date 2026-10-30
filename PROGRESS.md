# 开发进度记录（PROGRESS）

最近更新：2026-10-30，Codex 接续修复。

## 当前范围

课伴 KeBan：面向温州医科大学学生的安卓课程助手。第一阶段已实现四页面、本地 Room 待办及内置浏览器；第二阶段 PDF 解析与课表预览校正、第三阶段教务 PDF 下载尚未实现。学习通/知到/在浙学、AI 通知不在本期范围。

## 本次修复

- 本机 Gradle 代理原端口 55880 已失效，调整为当前 mihomo 监听的 7890；配置位于用户级 `.gradle/gradle.properties`，不进入项目。
- SDK 37.1 原目录只有 `.installer`，现通过官方 sdkmanager 完整安装。
- `app/build.gradle.kts` 明确使用 compileSdk 37.1；targetSdk 36、minSdk 26。
- 修复 `KebanRepo` 构造函数可见性、今天页 Material3 Card 导入、WebView `MIXED_CONTENT_NEVER_ALLOW` 常量及 TaskEditDialog 实验 API 声明。
- 修复本机 `local.properties` 的盘符转义；更新 README 工具链版本。

## 工具链

Gradle wrapper 9.6.0 / AGP 9.4.1 / Kotlin Compose 插件 2.4.21 / KSP 2.3.12 / JDK 21。依赖版本见 `gradle/libs.versions.toml`。

## 验证

- SDK 依赖元数据校验、KSP、Kotlin 编译通过，已生成 debug APK（0.1.0）。
- 完整重跑 `assembleDebug lintDebug --rerun-tasks` 成功（52 秒，47 项任务全部执行）；Lint 0 错误、6 条非阻断警告；APK 签名校验通过。
- 当前 `adb devices` 无设备，尚未验证安装、界面交互和重启持久化。
- 原文档声称“唯一问题是 SDK”“示例删除后不再出现”缺少验证依据：现有示例初始化只检查待办数量，删除所有待办后重新启动可能再次写入示例，留待行为修复。

## 下一步

连接真机完成安装与四页面冒烟，再推进 PDF 导入。当前目录尚未初始化 Git；历史交接和作品记录保存在 HANDOVER.md 与 docs/worklog/。

