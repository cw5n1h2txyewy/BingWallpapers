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

- 状态：accepted
- 背景：用户在 S23 Ultra 上反馈同目录旧软件图片不可见，要求已下载页显示保存目录图片。
- 决策：已下载页按需请求照片读取权限；Android 13+ 使用 READ_MEDIA_IMAGES，Android 10–12L 使用 READ_EXTERNAL_STORAGE；Android 14+ 显式处理选定照片访问；完整授权后仍只查询 DCIM/BingWallpapers/。
- 原因：MediaStore 对其他应用创建的图片执行读取权限过滤，文件处于同目录不等于应用拥有它；旧版本未申请读取权限。
- 影响：GalleryAccess、MainActivity、Manifest；下载仍无需读取权限；完整授权后的重复检测可以复用可见的旧图片。
- 适用范围：API 29+，S23 Ultra SM-S9180 Android 16 已验证完整授权和未授权状态。
- 验证：DeviceChecks 跨应用媒体回归先 FAIL（expected true, actual false），完整授权后 PASS 且缩略图可读；目录外测试图片被排除。
- 替代方案：所有文件访问权限范围过大；仅选择个别照片无法满足显示整个保存目录的默认需求，但允许用户保留部分授权。
