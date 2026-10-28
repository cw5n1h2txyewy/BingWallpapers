# 验证记录

## REVIEW-20261028-01：Android Bing 图片下载应用

- 变更摘要：独立实现最近八天浏览、地区切换、详情与链接分享、四种尺寸单张及批量下载、应用可访问的已下载相册；MediaStore 原图发布和重复文件保护；初始化 Git，补齐 Wrapper、README 与 Android 项目规范。
- 关键文件：app/src/main/java/io/github/bingwallpapers/、app/src/test/、app/src/androidTest/、README.md、AGENTS.md。
- 本地验证：Gradle Wrapper 9.4.1 / AGP 9.2.1 / SDK 36.1 / Android Studio JBR 25；assembleDebug、assembleDebugAndroidTest、testDebugUnitTest、lintDebug 最终全部成功；7 项单元测试，0 失败；Lint 0 错误、8 警告（升级建议、备份配置和文案本地化）；git diff --check 通过。
- 设备验证：adb 当前连接型号 23049RAD8C，Android 16；不等同于用户 S23 Ultra。最终 APK 已安装。DeviceChecks 最终输出完整 PASS：真实 Bing 四种尺寸、同 URI 重复保存、正确目录/文件名/非空内容/发布状态、无效 JPEG 不留条目、日期序列化和 URL 边界。
- 像素核对：从设备 DCIM/BingWallpapers 取回 20261028 图片，分别为 1920x1080、1920x1200、3840x2160、1080x1920，文件名与真实像素一致。
- 界面验证：预览实际显示；详情连续两次下载均留在详情页并显示已存在 1、失败 0；八张批量下载切到桌面再打开，保存 7、已存在 1、失败 0；已下载列表显示；最终版本系统返回键从详情回到最近列表。最终回归时段未发现本应用崩溃日志。早期设备测试遇到测试 APK 跨类加载器访问 package-private 方法失败，已修正并重跑通过。
- 未运行项：S23 Ultra 实机、Android 10–15、通知授权分支、全地区数据、存储耗尽、进程被系统杀死与长达数小时下载场景未验证。没有旧源码，未诊断旧应用闪退根因。
- 遗留风险：Bing 未公开稳定接口契约，UHD 不保证每张均有；同日同尺寸跨地区按用户日期命名跳过；强制停止后的队列需手动重试；旧应用及重装前媒体不自动导入（此限制已由 REVIEW-20261028-02 修复）；APK 当前为调试签名。
- 后续事项：用户可将 APK 安装到 S23 Ultra 做最终设备验收。详细限制见 README。

## REVIEW-20261028-02：显示旧软件下载目录中的图片

- 变更摘要：修复旧版未申请其他应用照片读取权限的问题；1.0.1 提供完整、部分及未授权状态提示和设置入口；返回前台刷新权限与相册；大目录使用 ListView 按可见行生成缩略图。
- 关键文件：GalleryAccess.java、MainActivity.java、AndroidManifest.xml、DeviceChecks.java、app/build.gradle、README.md。
- 本地验证：assembleDebug、assembleDebugAndroidTest、testDebugUnitTest、lintDebug 全部成功；7 项单元测试通过；Lint 0 错误、8 警告；git diff --check 通过。
- 设备验证：S23 Ultra SM-S9180 / Android 16。adb 放入已索引的 shell 归属同目录图片和目录外图片。旧版可见性断言失败；新版未授权时隐藏测试通过。用户选择 Allow All 后，同目录图片可见、缩略图可读、目录外图片排除，回归 PASS。默认四种尺寸真实下载、重复保存、JPEG 校验等集成检查再次完整 PASS。
- 清理：仅删除本次明确命名的两个回归图片并刷新媒体索引；清理后用户实际保存图片仍显示，保留用户 Allow All 授权，应用停留在已下载页。
- 未运行项：Android 10–15 实机、实际选定部分照片后的选择器流程、永久拒绝后的设置流程、超大图库长时间滚动未实测；相关状态分支已做代码检查。
- 遗留风险：未进入系统媒体索引的文件仍需系统完成扫描；部分授权只能显示系统准许访问的图片。当前无该问题的已知未修复项。
- 后续事项：无。
