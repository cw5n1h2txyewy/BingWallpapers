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

## REVIEW-20261028-06：深色主题与中英日界面

- 变更摘要：1.0.5 新增跟随系统/浅色/深色、简体中文/英文/日文/跟随系统选项并持久保存；页面、文字、按钮、占位图及原生弹窗按 night 资源切换；80 条界面和常见错误文案迁移为三套资源；下载尺寸、通知及进度本地化，Bing 元数据不自动翻译。
- 关键文件：AppSettings.java、MainActivity.java、Resolution.java、DownloadService.java、ImageStore.java、BingClient.java、res/values*、AppearanceChecks.java、README.md。
- 本地验证：assembleDebug、assembleDebugAndroidTest、testDebugUnitTest、lintDebug 全部成功；18 项既有 JVM 测试通过；Lint 0 错误、4 警告（工具链升级建议和备份配置）；三套各 80 条资源的键及格式参数核对通过；aapt2 确认 APK 包含 ja、zh、night 资源；git diff --check 通过。
- 设备验证：初次因未连接设备未执行；用户接回后在 S23 Ultra SM-S9180 / Android 16 覆盖安装 1.0.5。AppearanceChecks 的中英日 × 浅深色六种组合、系统语言/主题、服务文案、配色、格式参数及 Bing 地区独立性检查全部 PASS。实际菜单切换深色中文、深色英文、深色日语和浅色日语并查看截图，页面/弹窗/系统栏对比正常；英文与深色设置在强制停止重开后保留。日语实际下载显示“完了：保存 0、既存 1、失敗 0”。
- 其他回归：八轮真实标签切换复用页面且没有额外目录扫描/元数据请求，手动刷新恰好枚举一次，PASS。四种分辨率下载、重复保存、无效 JPEG 等完整集成检查 PASS；原有 20180210.jpg 可见且缩略图可读，匹配目录文件 3005 张。测试后核对偏好已恢复 language=zh、theme=system，保留图片、目录授权与缓存。
- 未运行项：Android 10–15 实机、系统自动日夜切换的现场触发、通知栏视觉布局和大字体缩放未测；跟随系统的资源配置与服务翻译已通过设备检查。
- 遗留风险：未发现本轮已验证场景的已知问题；系统文件选择器、权限提示及系统异常文本可能采用设备语言；图片标题和版权保留 Bing 来源文本。
- 后续事项：无。

## REVIEW-20261028-07：完成通知残留下载动画

- 变更摘要：1.0.6 只在下载队列运行时使用系统下载动画，全部结束后改用静态完成图标；完成通知允许清除、点击自动消失，并显式清除不确定进度，保持排队中的前台通知行为。
- 根因：1.0.5 的 notification(message, ongoing) 不论 ongoing 值都使用 android.R.drawable.stat_sys_download，该 drawable 本身是动画；设备原完成通知 flags 已为 0 且服务已停止，单纯 setOngoing(false) 不会停止动画资源。
- 关键文件：DownloadService.java、ic_download_done.xml、NotificationChecks.java、DeviceChecks.java、app/build.gradle、README.md。
- 本地验证：assembleDebug、assembleDebugAndroidTest、testDebugUnitTest、lintDebug 全部成功；18 项既有 JVM 测试通过；Lint 0 错误、4 警告；git diff --check 通过。
- 设备验证：S23 Ultra SM-S9180 / Android 16。旧版运行 -e notifications true 明确 FAIL：Finished notification still uses animated stat_sys_download。修复后同一回归 PASS，覆盖真实服务的空批次、错误 JSON 和连续批次结束，检查非 Animatable 图标、无 ongoing/foreground/no-clear 标记、auto-cancel 开启、无不确定进度。
- 真实下载验证：最终 APK 已覆盖安装；界面执行 1920x1080 实际下载，显示保存 1、已存在 0、失败 0。截图确认状态栏静态对勾；dumpsys activity services 显示无本应用服务；展开后本应用完成文案一致，点击本应用通知回到应用，活动通知记录数为 0。未操作其他应用通知。
- 未运行项：其他 Android 版本/OEM、长批量网络任务中途的通知检查、超时分支未独立实测；未对无关主题/缓存功能重复全量设备测试。
- 遗留风险：未发现本次修复的已知遗留问题；完成通知会保留静态提示直到被划掉或点击，这是预期行为。
- 后续事项：无。

## REVIEW-20261028-08：GitHub 用户包名

- 变更摘要：1.0.7 将应用身份和 Java 命名空间改为 io.github.cw5n1h2txyewy.bingwallpapers，移动 main/test/androidTest 三套 Java 源目录，更新广播、权限、runner、README 命令及 AGENTS 路径。
- 关键文件：app/build.gradle、AndroidManifest.xml、MainActivity.java、DownloadService.java、三套 Java 包目录、README.md、AGENTS.md。
- 本地验证：assembleDebug、assembleDebugAndroidTest、testDebugUnitTest、lintDebug 全部成功；18 项单元测试通过；Lint 0 错误、4 警告；APK badging 显示新包和 versionCode 8 / versionName 1.0.7；测试 APK 为新包加 .test；Manifest 组件和 INTERNAL 权限均为新命名空间；DEX 未保留旧包类；git diff --check 通过。
- 设备验证：S23 Ultra SM-S9180 / Android 16 安装两个新 APK 成功，新 runner 的 AppearanceChecks 完整 PASS；新组件 MainActivity 启动且 UI 节点归属新包，显示中文首页。旧应用和旧测试包仍保留，未卸载或清除旧数据。
- 未运行项：新包尚未重新选择保存目录或开启通知权限，因此没有重复执行需要这些授权的下载/通知和旧图片访问设备测试；相关功能逻辑未变，包名和权限连接已通过构建、清单与启动检查。
- 遗留风险：Android 不跨包继承私有设置、缓存和目录/通知授权；用户需在新应用重新设置。共享照片不变，选择原目录后可继续读取。
- 后续事项：无代码后续事项；新包的初次授权由用户按需完成。

## REVIEW-20261028-09：Kotlin 迁移

- 变更摘要：1.0.8 将全部 11 个生产 Java 类改为 Kotlin，移至 main/kotlin；保留原生 View、线程池、包名、持久配置、缓存格式、目录和图片命名。保留既有 Java 测试以及对应静态方法/字段、Saved 访问器的调用兼容性。README 和 AGENTS 已同步。
- 关键文件：app/src/main/kotlin/、app/build.gradle、README.md、AGENTS.md。
- 本地验证：clean assembleDebug testDebugUnitTest lintDebug assembleDebugAndroidTest --offline --no-daemon 成功，77 项任务全部重新执行；18 项原有 Java 单元测试全部通过；Lint 0 错误、4 警告（既有目标 API、Gradle 版本、内部权限命名提示及备份规则提示）；git diff --check 通过。首次获取 Kotlin 编译器依赖后可完整离线构建，未增加独立 Kotlin Android 插件。
- 设备验证：S23 Ultra SM-S9180 / Android 16；覆盖安装主/测试 APK 成功。AppearanceChecks 中英日与浅深色六种组合、系统配置、服务文案 PASS；位图缓存复用/损坏修复/上限 PASS；真实页面八轮切换复用页面且未重复扫描或请求，手动刷新只扫描一次 PASS（空目录与授权后真实目录各验证一次）。
- 目录及下载：为新包通过系统选择器授权 DCIM/BingWallpapers 后，直接读取 3002 张匹配旧图片，20180210.jpg 缩略图可读。真实 Bing 的 UHD、1920x1080、1920x1200、1080x1920 下载及重复检测、发布状态、错误 JPEG 清理、日期序列化和地址边界检查全部 PASS。
- 通知及实际界面：真实服务成功/失败/排队完成通知的静态图标、可清除状态和进度清理 PASS。实际菜单切换深色与日语、详情重建正常；尺寸弹窗无 RadioButton，取消按钮位于左侧；点击 1080p 后停留详情，日语状态显示保存 0、已存在 1、失败 0，结束后无残留服务。测试后恢复中文及跟随系统主题，目录/通知授权保留；最后重新安装干净构建产物并启动。未发现新包崩溃记录。
- 未运行项：其他 Android 版本和其他设备、长时间下载的系统超时分支未实测。
- 遗留风险：未发现迁移引入的已知行为问题；现有四项 Lint 警告保留，尚未覆盖的系统/OEM 行为仍需按需验证。
- 后续事项：无。
