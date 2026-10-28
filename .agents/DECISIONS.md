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
