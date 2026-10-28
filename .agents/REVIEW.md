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

## REVIEW-20261028-03：真实旧文件目录直读

- 变更摘要：1.0.2 改为 SAF 持久目录授权，直接枚举 DCIM/BingWallpapers 下符合日期命名的文件；按文档流生成缩略图；支持目录内同名旧文件重复下载保护；移除不再需要的全照片权限。
- 关键文件：ImageStore.java、WallpaperFiles.java、MainActivity.java、AndroidManifest.xml、WallpaperFilesTest.java、DeviceChecks.java、README.md。
- 本地验证：assembleDebug、assembleDebugAndroidTest、testDebugUnitTest、lintDebug 最终全部成功；7 项原测试及 4 项新增文件名/目录边界测试通过；Lint 0 错误、8 警告；git diff --check 通过。
- 设备验证：S23 Ultra SM-S9180 / Android 16，1.0.2 已覆盖安装。直接使用设备原有的 20260907.jpg：修复前 FAIL，目录授权后可枚举且缩略图可解码 PASS；20180210.jpg 同样 PASS。最初列出 3001 张；四种尺寸保存与重复下载集成检查全部 PASS，新增 3 张后列表 3004 张，与实际目录中匹配文件数一致。滚动显示 20261027.jpg、20261026.jpg、20261025.jpg；强制停止后重开，持久目录授权和 3004 张列表仍存在。
- 旧文件保护：本轮未创建临时旧文件、未扫描媒体库、未复制或改写设备已有的旧文件；默认下载测试只保存缺少的当日尺寸，按既定命名保留。
- 未运行项：Android 10–15 实机、撤销目录授权及重新选择流程、可移除存储卷、逐一打开全部 3004 张图片；没有将抽样解码泛化为全部文件内容验证。
- 遗留风险：目录需用户选择一次，卸载重装后需重新授权；文件必须符合已记录的命名规则；损坏图片可能只能显示文件名及加载失败提示。
- 后续事项：无。
- 对上一轮结论的修正：REVIEW-20261028-02 只证明已进入媒体库的跨应用图片可见，未验证用户实际旧目录文件；其“旧图片已修复”的范围过宽，现由本记录及实际旧文件回归取代。

## REVIEW-20261028-04：下载尺寸改为即时操作列表

- 变更摘要：1.0.3 下载尺寸弹窗改为普通列表，去掉单选圆点及上次选择的显示状态；点击尺寸立即下载；取消按钮放在左下角，并处理平台将唯一按钮居中的主题行为。
- 关键文件：MainActivity.choose、app/build.gradle、README.md。
- 本地验证：assembleDebug、assembleDebugAndroidTest、testDebugUnitTest、lintDebug 全部成功；现有 11 项单元测试通过；git diff --check 通过。
- 设备验证：S23 Ultra SM-S9180 / Android 16，1.0.3 已覆盖安装。截图确认四行无圆点，UI 层级均为不可选中的 TextView；取消按钮处于弹窗左下角，点击后弹窗关闭；点击 1920×1080 行直接进入下载队列。
- 未运行项：其他 Android 版本和批量弹窗未独立实测；批量入口共用同一弹窗实现。
- 遗留风险：未发现此次变更的已知遗留问题。
- 后续事项：无。

## REVIEW-20261028-05：页面与缩略图缓存优化

- 变更摘要：1.0.4 保留最近八天；当天元数据按地区持久缓存，目录快照五分钟复用，标签保留页面和滚动位置；预览加内存/磁盘缓存与并发合并；下载成功和授权/手动刷新触发失效；不再在每次返回前台清空图片缓存。
- 关键文件：MainActivity.java、CachePolicy.java、SnapshotCache.java、ThumbnailCache.java、ImageStore.java、DownloadService.java、CachePolicyTest.java、CacheChecks.java、README.md。
- 本地验证：assembleDebug、assembleDebugAndroidTest、testDebugUnitTest、lintDebug 最终全部成功；18 项 JVM 测试（含日界线、回拨、重试节流、目录修订和过期）通过；Lint 0 错误、8 警告；git diff --check 通过。
- 设备验证：S23 Ultra SM-S9180 / Android 16，最终 APK 已覆盖安装。内存命中、冷缓存磁盘命中、损坏缓存恢复、显式清理、磁盘上限检查 PASS。真实按钮连续八轮切换复用两个页面，未新增目录扫描或元数据请求，手动刷新恰好枚举一次，PASS。设备原有 20180210.jpg 可见且缩略图可读，匹配目录文件约三千张；四种分辨率真实下载、重复检测、无效 JPEG 等原有集成检查全部 PASS。
- 未运行项：Android 10–15 实机、真实跨午夜连续运行、移除目录授权的现场操作、并发请求压力测试、精确响应时间基准未执行；日界线与失效策略由确定性测试覆盖，不声称实测速度提升百分比。
- 遗留风险：外部增删文件可在五分钟内仍显示旧快照，手动刷新立即更新；进程重开后目录首次进入仍扫描一次；磁盘预览可被系统清理，首次未缓存图片仍需读取。
- 后续事项：无。
