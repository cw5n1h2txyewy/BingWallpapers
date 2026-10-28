# 兼容性决策

## DEC-20261028-01：原图保存与日期命名

- 状态：accepted
- 背景：用户要求兼容 Android 16，保留旧软件命名并增加更高分辨率。
- 决策：最低 Android 10；MediaStore 保存到 DCIM/BingWallpapers；1080p 无尺寸后缀，其他尺寸使用实际宽高后缀；UHD 不静默降级。中国区使用 Bing enddate 作为截图对应的展示日。
- 原因：避免旧式存储权限；Bing 中国区 fullstartdate 的 UTC 日期比本地展示日早一天；UHD 实际尺寸不能仅靠 URL 推测。
- 影响：Resolution、Wallpaper、ImageStore、下载界面。
- 适用范围：Android API 29+；Bing 当前 HPImageArchive 响应。
- 验证：2026-10-28 当前中国区 JPEG 实测 UHD=3840x2160，1920x1080、1920x1200、1080x1920 均匹配；2560x1440 返回 404。
- 替代方案：外部存储直写和静默分辨率回退无法满足兼容与命名要求。

## DEC-20261028-02：按需读取旧应用创建的目录图片

- 状态：superseded（由 DEC-20261028-03 替代）
- 背景：用户在 S23 Ultra 上反馈同目录旧软件图片不可见，要求已下载页显示保存目录图片。
- 决策：已下载页按需请求照片读取权限；Android 13+ 使用 READ_MEDIA_IMAGES，Android 10–12L 使用 READ_EXTERNAL_STORAGE；Android 14+ 显式处理选定照片访问；完整授权后仍只查询 DCIM/BingWallpapers/。
- 原因：MediaStore 对其他应用创建的图片执行读取权限过滤，文件处于同目录不等于应用拥有它；旧版本未申请读取权限。
- 影响：GalleryAccess、MainActivity、Manifest；下载仍无需读取权限；完整授权后的重复检测可以复用可见的旧图片。
- 适用范围：API 29+，S23 Ultra SM-S9180 Android 16 已验证完整授权和未授权状态。
- 验证：DeviceChecks 跨应用媒体回归先 FAIL（expected true, actual false），完整授权后 PASS 且缩略图可读；目录外测试图片被排除。
- 替代方案：所有文件访问权限范围过大；仅选择个别照片无法满足显示整个保存目录的默认需求，但允许用户保留部分授权。

## DEC-20261028-03：直接枚举授权目录并按旧文件名过滤

- 状态：accepted
- 背景：用户反馈照片全授权仍看不到旧文件；S23 Ultra 中实际旧文件存在，但 1.0.1 对 20260907.jpg 的回归仍失败，之前的已索引测试文件不足以证明真实旧文件兼容。
- 决策：使用系统 ACTION_OPEN_DOCUMENT_TREE 一次性授予 DCIM/BingWallpapers 目录读取权限，持久保存，只枚举该目录的第一层子文件。按有效日期 YYYYMMDD.jpg / YYYYMMDD-宽x高.jpg 过滤，扩展名兼容 jpeg 和大小写。缩略图直接从文档流采样解码，不依赖媒体库索引。移除全相册读取权限。
- 原因：目录文件存在不代表系统已将其作为媒体暴露给应用；目录授权可读取未收录的真实文件，也把访问范围限制在用户选择的目录。
- 影响：ImageStore、WallpaperFiles、MainActivity、Manifest、README；新图片仍通过 MediaStore 保存；授权目录中的同名旧文件不覆盖。
- 适用范围：API 29+；本地 ExternalStorage DocumentsProvider；S23 Ultra Android 16 已验证。
- 验证：20180210.jpg 和 20260907.jpg 实际旧文件枚举与缩略图读取 PASS；初始 3001 张匹配文件，下载测试后 3004 张，与实际目录文件数一致；进程重开后授权和列表保留；11 项 JVM 测试及 Lint 通过。
- 替代方案：继续请求全照片权限无法解决未收录文件；所有文件访问范围过大；强制扫描用户旧目录会改变系统媒体索引，且不能满足用户要求的直接目录读取。
- 替代关系：替代 DEC-20261028-02 的媒体库读取方案，保留 DEC-20261028-01 的保存和命名契约。

## DEC-20261028-04：按更新频率复用页面与图片缓存

- 状态：accepted
- 背景：用户反馈反复切换最近八天/已下载读取慢，要求缓存，明确不扩展到十五天。
- 决策：保留八天接口请求；每地区当天复用持久元数据，跨天先显示缓存再后台刷新，失败保留缓存并按分钟节流；Activity 内保留两页 View 和滚动位置；目录快照复用五分钟；授权目录、成功保存的修订号或显式刷新使快照失效。
- 原因：Bing 每日更新，而约三千张本地文件不应在每次切换时枚举；外部文件变更需保留手动刷新和短有效期。
- 影响：MainActivity、CachePolicy、SnapshotCache、ThumbnailCache、ImageStore、DownloadService；预览内存约 24 MiB、磁盘最多 64 MiB，原图不改写。
- 适用范围：API 29+；S23 Ultra Android 16 已验证；元数据日界线采用设备本地日期。
- 验证：18 项 JVM 测试通过；设备位图缓存复用、损坏恢复和大小上限检查 PASS；真实界面八轮切换未新增目录扫描或元数据请求，手动刷新恰好扫描一次。
- 替代方案：长期不失效会遗漏外部增删文件；仅增加内存而继续重建页面不能消除目录枚举和进程重开后的重复读图。
