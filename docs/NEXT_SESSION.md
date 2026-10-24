# 简净桌面交接（2026-10-24 更新）

## 当前状态：用户已明确恢复任务

用户于 2026-10-24 提供 IP TV_IP，并明确要求继续部署和设置，已取代下面历史记录中的暂停指令。

已部署 **0.3.2 / versionCode 7**，APK 37,414 字节，SHA-256：

`e01fd09f6816d5ff79df9da6770e6b00915244de05e5b5c1e8fd5e03c8a1f271`

本次修复了原厂不发送窗口事件的问题：保留 capabilities=0 的窄范围窗口监听，新增独立线程的 UsageEvents 前台状态检测，仅本项目启用 GET_USAGE_STATS；只在原厂主页面且未暂停时切回。最终决定在主线程再次检查 active/enabled/paused，避免退出接管竞态。应用图标仅在包发生变化时刷新，不再每次恢复都加载。

最新结果：26项策略断言、4项假ADB恢复测试通过；连续Home三次1.59/2.34/1.53秒通过。视频应用、HDMI播放器、网络设置保持前台；原厂出口、暂停、显式重开恢复接管和电脑恢复脚本已验证。电脑恢复只撤销本项目服务及 Usage appop，原有两项小米服务保留。

**部署与两次最终APK完整重启验收均已完成。** 两次用户均确认自动进入新桌面；重连只读验证版本7、三项服务绑定、Usage appop allow及原厂禁用状态，均未先运行激活工具。首次uptime=544.27秒，第二次154.44秒；第二次Home三次2.33/1.44/1.73秒通过。首次严格3秒性能检查曾有3.23/3.25秒超限，仍不承诺所有场景3秒内。电视最终留在新桌面，网络ADB目前已由用户开启；日常接管不依赖ADB持续在线。

原生默认Home路径仍受固件限制：PMS先按小米白名单过滤；虽然shell有SET_PREFERRED_APPLICATIONS，但属性persist.appcontrol_w_mask写入未生效，preferred仍为空。没有修改有效系统属性或默认桌面偏好。tools/android/HomeTool.java目前仅只读inspect。正式实现使用自建Home服务和前台状态后备检测。

本次详情：docs/session-20261024.md。证据：nor/research/session-20261024/，nor/backups/home-activation/，nor/research/accessibility-audit/。已安装APK与最终成品SHA-256一致，26项策略断言、4项恢复测试通过。当前版本已解决下面历史记录中的Home功能问题并通过两次冷启动；后续仅在用户提出新需求时继续，不需重复这些验收或再重启电视。

## 以下为 2026-10-23 历史交接（以以上更新为准）

## 用户最新要求

2026-10-23 用户表示要睡觉，电视已休眠，要求“把本地的改完后就先暂停”。**已暂停；不要继续连接、发按键、安装、重启或唤醒电视，直到用户明确继续。** 没有创建定时任务或后台监控。

## 已交付到本地

- 新桌面原生 Java 源码、Android Studio 工程配置、现有 SDK 直接构建脚本。
- nor/dist/quiet-tv.apk：已构建并验证签名；本机开发签名密钥在 nor/.local/，后续更新必须保留。
- nor/backups/2026-10-22/：20 个 APK，308.7 MiB，远端与本地 SHA-256、大小和 ZIP CRC 全部通过；原厂当前版/出厂版桌面均保留。
- tools/restore_tv.ps1：由应用关闭自己的 Home 服务、返回原厂，仅删除自身授权条目，保留其他服务；新增读取失败/空输出/读回保护。
- tools/enable_home.py：对本固件无系统无障碍 UI 的情况，保存原有授权，追加并核验服务真实绑定；不授予应用 WRITE_SECURE_SETTINGS。
- 最新本地检查：16 项 Java 策略断言 + 4 项假 ADB 恢复测试通过。后者不会接触真实电视。

## 电视当前状态

- 目标：TV_IP:5555，MiTV4-ANSM0，Android 6.0.1 / API 23。USB 连接另有一台手机，任何电视 ADB 命令都必须显式指定 serial。
- 已安装自建桌面 local.mitv.launcher；原厂包未卸载、禁用或覆盖。com.mitv.care 原本已禁用，状态未改。
- Home 服务通过真实绑定验证；保留原有小米麦克风和小爱同学服务。
- 用户已休眠电视。ADB 在首次完整重启后被关闭，用户已重新开启，但不应假定下次还开着。
- 首次完整重启：用户确认先出现原厂，再进入新桌面；重连后服务均绑定，证据在 nor/research/reboot-1/。
- 随后安装的最终版本已清理 boot_pending 残留，避免服务重绑时抢前台；**此版本尚未完成新的完整重启验收**。

## 下一次优先事项

1. 用户明确恢复后再检查 ADB 和电视电源状态。
2. 优先诊断连续 Home：`python tools/check_home.py` 的第二次按键在 3 秒内未回自建桌面。早期版本可见事件并最终切回；最新一次故障中未见后续接管日志，dumpsys accessibility 仍显示服务绑定，窗口列表却只显示系统栏。需区分窗口事件缺失、偏好状态和前台启动节流，不能直接认定 PendingIntent 已修好。
3. 在不读取界面内容的前提下，加入必要窗口事件/偏好和 onResume 的诊断日志，再做针对性测试。`canRetrieveWindowContent=false`，当前服务 capabilities=0，避免为了排查擅自扩大权限。
4. 复测普通 Home、快速连按、从视频应用回 Home、返回原厂的暂停和重开恢复。
5. 完成最终版本至少两次完整重启。每次网络 ADB 可能被关闭，需要用户在电视上再次允许，提前安排配合。
6. HDMI3 只验证了会话建立和无信号处理；需要外部设备实际输出画面再验证预览。HDMI1/2 没有 HW5/HW6 注册，原厂也是 input=23/24，不能把打开播放器当作成功切换。

## 环境与已知坑

- JDK：JDK_HOME（Java 11）。SDK：SDK_HOME，platform android-32 / build-tools 32.0.0。
- `python tools/build.py` 可直接构建；Java lambda 编译需要 core-lambda-stubs.jar，脚本已处理。
- 固件拒绝 shell 的 pm disable-user，甚至可能返回退出码 0；不要重新套用这种恢复方案。
- 安装 APK 会出现电视安装确认弹窗；仅确认本次安装，不选择“应用到全部”。更新后重新核验服务绑定。
- shell uiautomator dump 在本固件断开自动化会话时触发权限异常；已停止使用。使用截图、明确目标的 ADB 按键及系统状态取证。
- REA Android provider 缺 JADX MCP JAR；已用本地 baksmali 只读分析原厂 APK/odex。取证报告在 nor/research/system-entry-audit/、weather-audit/、tvinput-audit/，不公开上传整个 research 或 backups。
- 天气 Provider 有调用包名白名单，查询正确 URI 还可能联网；当前天气按钮只打开原厂天气，未伪造温度。
- 工作目录初始为空，尚非 Git 仓库，没有需要合并/提交的分支，不要默认创建 PR。
