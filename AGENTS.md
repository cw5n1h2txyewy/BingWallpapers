# AGENTS.md

本文件适用于全仓库。用户明确要求优先。开始任务先读 README、相关源码、测试、已有相关 `.agents/` 记录及近期 Git 历史，检查 git status，保留无关改动。

## 项目目的

独立实现 Android Bing 图片浏览及下载工具，重点保证 Android 16 下可靠保存。screenshots 是旧软件的交互参考，并非旧源码。图片版权归原权利人。

## 代码结构

- app/src/main/java/io/github/bingwallpapers/：原生 Java 应用；界面、HTTP、元数据、尺寸、MediaStore 保存和前台队列分开维护。
- app/src/test/：无需设备的尺寸与命名测试。
- app/src/androidTest/：真实 Bing 与 MediaStore 集成检查。
- screenshots/：用户原始参考截图，保留原样。
- README.md：构建、安装、使用方法与限制的事实来源。

## 开发环境与代码规范

使用 Gradle Wrapper、Android SDK 和 Java，版本以 Gradle 配置为准；命令见 README。本项目不需要 Python 环境。

- 使用明确 Java 类型，类和重要方法使用简短 Javadoc；保持模块简单，避免无必要依赖及过度抽象。
- 网络、图片解码和文件 I/O 在后台线程执行；UI 更新回到主线程。处理 Activity 重建与服务生命周期。
- 流、游标等资源使用 try-with-resources；捕获具体异常，保留上下文并向用户显示可操作的失败信息。
- 新增平台 API 检查最低版本或适配，以 Lint 及设备验证确认兼容性。

## 下载与存储契约

- 保存 Bing 原始 JPEG，不放大或重压缩，不把预览当原图；验证真实像素及完整性，不满足请求尺寸时明确失败。
- 路径和命名规则由 Resolution、ImageStore 及对应测试定义，修改时同步 README。
- 使用 MediaStore RELATIVE_PATH 和 IS_PENDING，失败清理新条目，成功后发布。访问本应用有权访问的媒体，无需所有文件权限。
- 下载串行执行；重复下载复用本应用可见的同名文件，不覆盖用户图片。
- Bing 接口缺少公开稳定性保证；新增尺寸先验证实际 JPEG，不能仅依据二手文章。

## 测试要求与交付

1. 重要逻辑增加行为测试，覆盖命名、尺寸不匹配、无效响应和重复操作。
2. 运行 gradlew.bat testDebugUnitTest lintDebug assembleDebug assembleDebugAndroidTest。
3. 有设备时安装 APK 并运行 README 的集成检查，核对文件名、真实尺寸、相册发布、连续下载和崩溃日志。
4. 最后检查 git diff --check、git diff、git status。交付说明列出实际检查、设备系统、未运行项和限制；不把计划验证写成通过。
5. 提交使用 Conventional Commits，不重写共享历史；不提交缓存、密钥、日志、设备标识和本机路径。APK 作为本地构建产物交付。

## 项目记录与 skills

仅按需维护 .agents/：TASKS.md 记录复杂任务步骤及验收；DECISIONS.md 记录项目兼容决策或正式 ADR 索引；FIELD_GUIDE.md 记录已验证且易重复踩坑的经验；REVIEW.md 记录验证和遗留风险。

- 新条目按绝对日期、稳定编号追加到末尾；原条目状态原位更新，保留其他有效记录。
- 结论追溯到代码、测试、上游文档或设备实验，并写明系统和适用范围。相反决策新增条目并将旧项标为 superseded。
- 只记录结果及原因，不记录聊天、完整日志、内网地址和设备唯一标识。
- CONTEXT.md 如存在，只定义稳定术语；正式 ADR 仅用于难以逆转、存在真实替代且缺少背景会难以理解的决策。
- 修改代理指令使用可用的 writing-for-agents；复杂故障适用 diagnosing-bugs；模块设计适用 codebase-design；用户要求测试先行时用 tdd；审查适用 code-review。不要为使用 skill 扩大范围。
