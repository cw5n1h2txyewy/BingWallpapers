# 任务

- [x] T-001 独立实现 Android Bing 图片下载应用（2026-10-28）
  - 范围：app、Gradle、README、AGENTS 和设备验证
  - 依赖：Android SDK、Bing HTTPS、已授权 adb 设备
  - 验收：编译、单元测试、Lint；Android 16 下载和重复保存；目录与像素核对

- [x] T-002 显示保存目录中旧应用创建的图片（2026-10-28）
  - 范围：相册权限、MainActivity、ImageStore、设备回归及 README
  - 依赖：Android 16 S23 Ultra、跨应用媒体测试文件
  - 验收：未授权回归可复现；完整授权可见且缩略图可读；拒绝和部分授权有正确提示；仅显示目标目录；构建、单元测试、Lint 通过
