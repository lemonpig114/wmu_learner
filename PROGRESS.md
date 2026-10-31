# 开发进度记录（PROGRESS）

最近更新：2026-10-31（v0.5.0 已推送 GitHub）

**远程仓库**：<https://github.com/lemonpig114/wmu_learner>（公开，分支 `main`，标签 `phase-1`）
本机推送方式：Windows 凭据管理器已存 `git:https://github.com`（账号 `lemonpig114`），
用系统 Git（`D:/Install/Git`，带 GCM）以 `credential.helper=manager` 推送；WorkBuddy 自带 Git 无凭据。

## 当前范围

课伴 KeBan：面向温州医科大学学生的安卓课程助手。
- 第一阶段 ✅：四页面、Room 本地待办、内置浏览器、首个可安装 APK、Git 版本管理
- 第二阶段 ✅：解析核心（真实文件单测）+ 导入流程（选 PDF → 解析 → 预览校正 → 确认保存）+ 周课表网格 + 手动增删改 + 节次时间表 + 清空课表入口
- 第三阶段 ✅：教务统一身份认证登录（自动填入账号密码）+ **课表 PDF 一键获取**（用户真机确认成功）
  · 实测导出接口：`POST /jwglxt/kbcx/xskbcx_cxXsShcPdf.html`
  · 登录链路：`jwxt.wmu.edu.cn/sso/jziotlogin` → `authserver.wmu.edu.cn/authserver/login`（登录表单在同源 iframe 内）
- 温医大特供默认值 ✅：学期起始日 9 月 7 日、节次作息第 1~17 节（均由用户提供/核对，并用单测锁死）
- 第四阶段 🔄：真机验证与交付（已真机跑通导入全流程；自动登录与本周次定位待你实测）
- 不在本期：学习通 / 知到 / 在浙学接入、AI 通知

## Git 记录

| 提交 | 内容 |
|------|------|
| `2610681` + 标签 `phase-1` | 第一阶段：最小工程与版本管理（四页面 + 本地待办 + 内置浏览器，APK 已验证） |
| `858d12d` | 阶段2(上)：PDF 解析核心 + 真实文件单测验证 |

> 注：本目录此前由辅助工具 Codex 做过接续修复（见下节），相关改动已包含在 `2610681` 中。

## 辅助工具 Codex 接续修复记录（2026-10-30，用户在其环境中执行）

- 本机 Gradle 代理端口从 55880 改为 mihomo 监听的 **7890**（用户级 `.gradle/gradle.properties`，不入项目）。
- SDK 37.1 完整安装（此前目录只有 `.installer`）。
- `app/build.gradle.kts` 明确使用 compileSdk 37.1（AGP 9 新 DSL）；targetSdk 36、minSdk 26。
- 修复 WorkBuddy 初版代码的 4 处真实缺陷：
  1. `KebanRepo` 构造函数可见性；
  2. 今天页误用 `androidx.compose.foundation.layout.Card` 导入（应为 material3）；
  3. WebView 常量 `MIXED_CONTENT_NEVER` → `MIXED_CONTENT_NEVER_ALLOW`；
  4. `TaskEditDialog` 使用实验 API 缺 `@OptIn` 声明。
- 修复 `local.properties` 盘符转义。
- 验证：`assembleDebug lintDebug --rerun-tasks` 成功（52 秒，47 项任务全执行），Lint 0 错误、6 条非阻断警告，APK 签名校验通过。
- 指出示例数据缺陷：原实现只检查待办数量，删除示例后重启会被重新写入 → **已由 WorkBuddy 修复**（改为 settings 标记位只播种一次，见 `KebanRepo.seedSampleIfNeeded`）。

## 第二阶段实测结论（重要，均为真实文件实测）

| 事实 | 依据 |
|------|------|
| 课表 PDF 为文字型、8 页横排、页面旋转 90°，pdfbox 的 xDirAdj/yDirAdj 已自动校正 | 结构转储 `.analysis-local/token_dump.txt` |
| 表头（星期一~日）**仅首页出现一次**，各页共用列坐标 | 转储中「星期一」仅 1 处 |
| 单元格节次取 `(a-b节)` 注记，**不能按等距行推**（行高随内容自适应，单元格可跨页续行） | 转储页 1/页 2 衔接处 |
| 真实单元格数 = **52**（独立统计 `(a-b节)` 注记），pdfplumber 早前计 45 为漏计 | 两种方法交叉核对 |
| **同一课表重复导出，文件 MD5 不同**（打印时间差异）→ 去重必须做解析后语义比对，不能只看 hash | 3 个副本实测：df84df…/aca605…/f887c2… |
| 教务页面匿名访问返回 **403**，必须登录 → WebView 登录后拦截真实下载响应，不能猜直链 | curl 探测 |

## 工具链

Gradle wrapper 9.6.0 / AGP 9.4.1（内置 Kotlin）/ Kotlin Compose 插件 2.4.21 / KSP 2.3.12 / Compose BOM 2026.09.00 / Room 2.8.5 / JDK 21 / compileSdk 37.1 / minSdk 26 / targetSdk 36；PDF：App 内 pdfbox-android 2.0.27.0，单测桌面 pdfbox 2.0.37。依赖见 `gradle/libs.versions.toml`。

## 测试与验证

- ✅ 单元测试全通过（`./gradlew :app:testDebugUnitTest`）：
  - `WeeksTest` 6 项（周次表达式：单周/区间/不连续/单双周/混合/不可解析返回 null）
  - `WmuTimetableParserTest` 2 项（2026-2027-1 样本 **52 条**、跨学期 2025-2026-1 样本 **45 条**，含隐私回归断言）
  - `PdfTokenDumpTest` 1 项（结构转储）
- ✅ 2026-2027-1 样本解析结果与独立计数一致，学期/打印时间/实践课程行均正确识别
- ✅ 2026-10-30 真机冒烟（Meizu M332BF / Android 16）：安装成功、启动无崩溃、四页面渲染正常（截图存 `docs/worklog/assets/`）；数据库直查确认 `sample_seeded=1`、示例任务与手动任务均在；「今天」页不显示已完成任务是**正确行为**（仅显示未完成）
- ✅ 导入链路静态验证：装配器单测 `52 时段 → 10 门课程`、时段合计 52（不丢失）、语义指纹与顺序无关；
- ✅ **Room v1→v2 迁移本机实测**：模拟 v1 库（含既有待办）执行迁移 SQL → 逐列/外键/索引与 Room 导出的 schema **忽略空白后完全一致**，既有数据保留（tasks=1、sample_seeded 保留）
- ❌ 待用户在真机验证：导入真实 PDF 全流程、周次切换、时段编辑/删除、重复导入提示、重启后数据仍在

## 下一步

1. 第二阶段下半：SAF 选 PDF → 解析 → **预览校正** → 确认保存（Room v2：Course/CourseSlot）→ 周课表网格；重复导入按语义比对去重（替换上次导入、保留手动课程）；导入失败不破坏既有数据；手动增删改课程。
2. 再连真机做第一阶段冒烟 + 第二阶段整体验证。

## Codex：浏览器白屏修复尝试（v0.2.3，2026-10-30）

已修复新窗口复用父 WebView 的错误，采用单窗口导航；默认原生手机 UA；新增站点备用表单入口“简版登录”；扩充 iframe 结构、子资源与脚本错误诊断。版本号 0.2.3 / 5。APK 构建和签名校验通过，真机白屏改善情况待验证。详细分析见 `docs/教务登录白屏-Codex复核.md`。本次不更改登录自动提交及 PDF 解析流程。

## 浏览器白屏根因已定位并真机修复（v0.2.4）

真机探针确认：认证 iframe 已加载并含表单，但高度为0；定点修正认证页容器高度后，iframe 高度约496 CSS像素，登录页已正常显示。v0.2.4 / versionCode 6 已安装到连接手机，保留原有数据；用户手动登录及登录后课表获取仍待验证。详见 `docs/教务登录白屏-Codex复核.md` 最新段落。
