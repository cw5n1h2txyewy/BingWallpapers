# 必应美图

轻量的 Android Bing 图片浏览和下载应用，独立实现，参考 `screenshots/` 中旧应用的交互。不是微软官方应用，也不包含 Vove7 原项目的源码（提供的 GitHub 链接目前为 404）。

## 功能

- 浏览 Bing 最近八天图片、标题、日期、摄影版权信息，点击进入预览。
- 下载单张或菜单中批量下载最近图片；前台服务串行执行，离开页面仍可继续。
- 下载尺寸使用普通列表，点击尺寸立即下载；取消按钮位于左下角。
- 下载中使用动画图标，队列全部结束后切换为静态完成图标；完成通知可以划掉，点击打开应用后自动清除。
- 中国、美国、日本地区切换；元数据缓存支持网络失败时显示上次列表。
- 最近八天和已下载页面复用缓存，切换不重复加载；预览使用内存和磁盘缓存。
- 已下载列表直接枚举 **`DCIM/BingWallpapers`** 中符合日期命名的文件，不依赖系统媒体库是否收录；调用系统图片查看器，分享 Bing 原图链接。
- 通过 MediaStore 保存新图片；读取旧图片只需选择并授权这个目录，不申请全相册或所有文件权限。

支持 Android 10 及以上，目标 Android 16（API 36）。独立包名 `io.github.cw5n1h2txyewy.bingwallpapers`，可以与旧软件共存。

## 包名变更（1.0.7）

包名及 Java 命名空间使用 GitHub 用户 ID：`io.github.cw5n1h2txyewy.bingwallpapers`。Android 将它视为新应用，不能直接覆盖原来的 `io.github.bingwallpapers`；安装后两者可共存，不会卸载旧版。

共享图片继续保存在 `DCIM/BingWallpapers`，文件名不变。新应用需要重新选择一次该目录并设置主题、语言、通知权限；旧应用的私有偏好、缓存与目录授权不会自动迁移。授权目录后即可查看已有图片。

## 主题与语言（1.0.5）

右上角菜单新增：

- **外观主题**：跟随系统、浅色、深色。默认跟随系统；页面、文字、按钮、下载尺寸弹窗及系统栏配色一同切换。
- **语言**：简体中文、English、日本語、跟随系统。默认保留原来的中文界面；选择后立即生效并保存，重开应用仍使用所选项。

设置变更只重建当前界面，保留目录授权、下载队列及已有缓存。界面语言与“切换地区”相互独立；图片标题和版权说明保留 Bing 返回的原文，不自动翻译。下载进度、通知、尺寸选择、常见存储及网络错误提示已提供三套翻译。旧语言的历史状态不会混入切换后的界面，下一次下载会显示新语言的进度。

应用自带三种语言资源，离线也可切换；不需要下载语言包。系统文件选择器和权限对话框由 Android 控制，可能使用手机系统语言。

## 缓存与刷新（1.0.4）

- **最近八天保持八天**：每个地区分别保存元数据，当天重复打开直接复用。跨天打开、回到前台或切回该页时，先展示已有内容，再后台更新；网络失败保留旧数据，自动重试至少间隔一分钟。“刷新最近图片”和该页菜单“刷新”可主动更新。
- **已下载**：在当前 Activity 中复用目录列表五分钟，切换标签及短暂返回前台不重复枚举目录。新图片保存成功、目录授权变化或手动刷新会让旧列表失效；重开进程后首次进入仍需要读取一次目录。
- **缩略图**：约 24 MiB 内存缓存、最多 64 MiB 磁盘缓存；进程重开后仍可复用磁盘预览。相同图片的并发请求合并，损坏缓存自动重新生成；只缓存预览，不改写下载原图。磁盘缓存位于应用缓存目录，系统可按空间需要清理。
- **外部增删或替换旧文件**：进入“已下载”点击“刷新已下载”（或此页菜单“刷新”），立即重新读取目录并更新缩略图；否则在五分钟缓存过期后重新进入时读取。切换页面会保留已加载页面和滚动位置。

更新检查由使用应用触发，不进行后台定时联网。

## 分辨率与命名

| 选项 | 文件名示例 |
| --- | --- |
| 1920×1080 | `20180101.jpg` |
| 1920×1200 | `20180101-1920x1200.jpg` |
| UHD 原图 | `20180101-3840x2160.jpg`，按实际尺寸命名 |
| 1080×1920 竖屏 | `20180101-1080x1920.jpg` |

日期采用 Bing 返回的 `enddate`（无该字段时使用 `startdate`），对应参考截图中的日期。中国区 `fullstartdate` 以 UTC 表示的起始时刻可能落在前一天，不能直接截取作为本地展示日。

2026-10-28 对 Bing 中国区当日图片实测：以上四种图片均为对应尺寸 JPEG，UHD 为 3840×2160；`2560x1440` 请求为 404，因此没有提供此选项。UHD 不保证每张都为 4K。应用校验真实像素和 JPEG 结束标记，尺寸不足或响应损坏会明确失败，不静默降级或放大。图片字节原样保存，水印情况取决于 Bing 原始图片。

重复下载会复用本应用可见的同名图片。不同地区同一天仍沿用用户要求的日期命名，因此同日同尺寸已存在时跳过；若需要保留不同地区同日图片，应先在系统相册重命名。应用不覆盖旧图片。重复检测在下载校验后进行，因此再次点击仍会产生网络流量。

## 显示旧软件保存的图片（1.0.2）

覆盖安装新版，打开“已下载”→**选择保存目录**，在系统文件选择器进入 `DCIM/BingWallpapers`，点击“使用此文件夹”并允许访问。目录授权会持久保存，重开应用不用重复选择。

1.0.1 的照片全授权只覆盖系统媒体库中的图片，无法保证读到旧文件。1.0.2 改用 Android Storage Access Framework（SAF）直接枚举授权目录的第一层文件，并直接读取文件内容生成缩略图；不扫描其他目录或子目录，不要求系统先建立图片索引，不复制或覆盖旧图片。

文件名过滤支持有效日期 `YYYYMMDD.jpg`、`YYYYMMDD-宽x高.jpg`，扩展名也接受 `.jpeg` 和大小写变体；排除无关文件、临时文件、无效日期和子目录。列表显示匹配总数，按日期文件名倒序、按可见行加载缩略图。授权被撤销时可重新选择目录。未选择目录时仅回退显示当前应用可见且命名匹配的媒体，仍可正常下载新图片。

## 构建与安装

使用 Android Studio 打开本目录，等待 Gradle 同步。需要 Java 17+、Android SDK Platform 36.1；仓库使用 AGP 9.2.1、Gradle 9.4.1。本机可使用 Android Studio 自带 JBR。

PowerShell 示例：

```powershell
$env:JAVA_HOME = 'C:\Program Files\Android\Android Studio\jbr'
# local.properties 为本机配置，不提交 Git；路径按 SDK 安装位置修改。
$sdkPath = "$env:USERPROFILE/AppData/Local/Android/Sdk" -replace '\\', '/' -replace ':', '\:'
"sdk.dir=$sdkPath" | Set-Content local.properties
.\gradlew.bat assembleDebug testDebugUnitTest lintDebug assembleDebugAndroidTest
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

若依赖下载需要本机代理，Gradle 使用 JVM 参数（单独设置 HTTP_PROXY 通常不足以配置 Gradle）：

```powershell
.\gradlew.bat assembleDebug '-Dhttps.proxyHost=127.0.0.1' '-Dhttps.proxyPort=7890' '-Dhttp.proxyHost=127.0.0.1' '-Dhttp.proxyPort=7890' '-Dhttp.nonProxyHosts=repo.maven.apache.org'
```

在本机 JBR 25 与沙箱环境组合下，若出现 `Unable to establish loopback connection`，堆栈包含 `UnixDomainSockets`，可使用短临时路径：

```powershell
New-Item -ItemType Directory -Force verification/tmp | Out-Null
$tmpPath = ((Resolve-Path verification/tmp).Path -replace '\\', '/')
$env:JAVA_TOOL_OPTIONS = "-Djdk.net.unixdomain.tmpdir=$tmpPath -Djava.io.tmpdir=$tmpPath"
.\gradlew.bat assembleDebug
```

输出：`app/build/outputs/apk/debug/app-debug.apk`。调试 APK 使用本机调试签名，适合当前设备验证；正式分发需要自己的固定发布签名，密钥不应进仓库。

## 设备检查

```powershell
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb install -r app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
adb shell am instrument -w io.github.cw5n1h2txyewy.bingwallpapers.test/io.github.cw5n1h2txyewy.bingwallpapers.DeviceChecks
adb shell am start -n io.github.cw5n1h2txyewy.bingwallpapers/.MainActivity
```

集成检查会访问真实 Bing 并下载当前图片的四种尺寸到目标相册，保留结果方便检查；验证路径、发布状态、非空文件、重复下载无新增条目、无效 JPEG 拒绝、日期序列化和图片地址边界。失败输出包含 `FAIL`，成功包含 `PASS`；此测试需要网络和设备，不计入离线单元测试。

目录读取回归支持 `am instrument -w -e gallery visible -e filename 20260907.jpg`（其余组件参数同上）。请将 filename 替换为测试设备上确实存在的旧图片名；这会验证目录枚举能找到真实旧文件、缩略图可直接读取，并报告匹配图片总数。`-e gallery hidden` 可验证指定文件不可见的场景。文件名规则和目录范围还有独立 JVM 测试。回归不会更改指定的旧文件。

缓存设备检查：使用同一 instrumentation 组件加 `-e cache true` 验证位图内存/磁盘复用、损坏恢复及磁盘上限；加 `-e cache_ui true` 验证真实页面连续八轮切换不重复枚举目录或请求元数据，手动刷新只重新枚举一次。后者会打开应用并操作标签页。

主题/语言设备检查：加 `-e appearance true` 验证中英日与浅色/深色六种组合、跟随系统、配色资源、服务翻译与格式参数，结束后恢复原设置。实际菜单切换仍需在设备上检查页面及弹窗。

通知回归：先允许下载通知，再加 `-e notifications true`。测试会通过真实服务提交空批次、错误输入和连续批次，不下载或改写图片；检查最终通知为静态图标、没有前台/持续/不可清除标记、可点击消失且无不确定进度。它会替换本应用自己的旧完成通知。

手工回归：进入图片 → 下载 → 选择尺寸，完成后再次下载；检查没有跳回首页或崩溃；切换已下载页；批量下载期间返回/重开界面。相册文件用系统相册或文件管理器检查。

## 数据来源与限制

- 直接访问 [Bing HPImageArchive](https://www.bing.com/HPImageArchive.aspx?format=js&idx=0&n=8&mkt=zh-CN) 及返回的 `/th?id=OHR...` 图片，未使用第三方壁纸接口。按用户要求，应用固定显示最近八天；该接口缺少公开稳定性保证，不能作为任意历史日期查询服务。
- 保存实现依据 [Android 官方共享媒体存储文档](https://developer.android.com/training/data-storage/shared/media)，使用 `RELATIVE_PATH`、`IS_PENDING`，只访问应用自身有权访问的媒体。
- 菜单“允许下载通知”可选开启 Android 13+ 通知权限，拒绝后仍可下载并在应用内查看进度；前台服务仍遵守系统规则。强制停止/进程终止后的队列不自动恢复，可重新点击下载；系统长时间下载超时会停止服务。
- 已下载页读取用户明确授权的保存目录，依据 [Android 目录授权文档](https://developer.android.com/training/data-storage/shared/documents-files#grant-access-directory)。卸载重装或手动撤销目录授权后，需要重新选择目录；目录内文件需要符合上述命名规则。
- 不包含自动换壁纸、桌面组件、无限历史图库和自动每日下载。图片版权归原作者及权利人，请遵守 Bing 使用条款及授权限制。

代码与验证记录见 `.agents/`；用户参考截图保持原样。
