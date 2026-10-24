# 精简小米电视桌面 Implementation Plan

**Goal:** 实现用户圈选截图的删减版桌面，保留信号源预览及可配置应用栏，并可恢复原厂。

**Architecture:** 原生 Android Activity 负责界面，TvView 负责 HDMI 预览，设备入口适配器调用导出的系统组件。应用顺序和接管暂停状态在本地持久化，AccessibilityService 仅识别原厂主窗口。

**Tech Stack:** Java 8 兼容字节码、Android API 23、SDK 32、现有 Java 11、Python 构建脚本。

执行方式：在当前会话顺序实施；空工作目录无既有 Git 分支。用户截图已经确认设计，不再重复请求布局确认。

## 1. 备份与入口取证

- [x] 保存包状态、属性、信号源状态和截图。
- [x] 开始导出 APK，tools/backup_tv.py 对远端 SHA-256、本地 SHA-256、字节数和 ZIP CRC 逐一验证。
- [x] 读取 settings、tvplayer、tvhome 的 AndroidManifest，保留在 nor/research/manifests/。
- [x] 核心 APK 全部验证完成后再安装新桌面。

## 2. 可运行的策略测试

Files: tests/LauncherPolicyTest.java, app/src/main/java/local/mitv/launcher/LauncherPolicy.java。

- [x] 测试去重保持应用顺序、配置为空时保留空配置、移除已卸载入口、不拦截原厂子页面和暂停接管。
- [x] 先执行测试并观察缺失功能造成失败，再实现策略并验证通过。
- [x] 命令：python tools/build.py --test；16 项断言通过。

## 3. 原生应用与构建

Files: app/src/main/AndroidManifest.xml, res/xml/home_accessibility.xml, res/values/styles.xml, MainActivity.java, Apps.java, DeviceActions.java, PreviewController.java, HomeBridgeService.java, tools/build.py。

- [x] 声明 HOME、LAUNCHER、LEANBACK_LAUNCHER，屏幕横向，兼容 API 23。
- [x] 实现截图布局、方向键焦点、应用选择和排序菜单。
- [x] 用 PackageManager 获取本机应用图标和可启动组件；SharedPreferences 存储顺序。
- [x] 使用 TvView.tune(inputId, TvContract.buildChannelUriForPassthroughInput(inputId)) 预览，处理连接失败/无信号并在暂停时 reset。
- [x] 入口从本机 Manifest 适配，不依赖被系统签名保护的 Activity。
- [x] 实现严格窗口匹配、原厂出口的持久暂停和显式重开后的恢复；连续 Home 最终验收未完成。
- [x] 使用 aapt、javac、D8、zipalign、apksigner 构建，python tools/build.py 输出 nor/dist/quiet-tv.apk 并校验签名。

## 4. 电视实测和恢复

Files: tools/restore_tv.ps1, README.md, docs/verification.md。

- [x] 记录无障碍原始状态；恢复脚本只移除本项目服务，保留其他服务；新增 4 项假 ADB 回归测试。
- [x] 安装并手动启动，截图检查布局，读取崩溃日志。
- [ ] 完整实测各信号源和所有应用；当前已验证 HDMI3 会话、网络设置、排序持久化和返回原厂，HDMI1/2 存在系统注册缺失。
- [x] 该固件无系统无障碍页面，改为配套 ADB 工具保留旧授权并核验真实绑定。
- [x] 0.3.2 Home功能回归、返回原厂/暂停恢复及同一最终APK连续两次完整重启验收；保留偶发3秒性能超限的实测记录。
- [x] 文档交付安装包、源码、备份和恢复方式，清楚列出尚未验证的项目。

2026-10-23曾按用户要求暂停，2026-10-24已明确恢复并完成0.3.2部署和两次重启验收。HDMI实际视频信号及HDMI1/2注册仍需外部设备条件验证，未声称这部分已通过。
