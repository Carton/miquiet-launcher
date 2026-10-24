# 简净桌面

为这台 MiTV4-ANSM0（Android 6.0.1 / API 23）从头编写的原生精简桌面。去掉账户、订购、频道导航、推广和 GITV 标识，保留信号源区域、底部应用栏和常用设置。电视已部署版本仍为 0.6.0；本地 **0.7.0 候选版已构建，尚未安装、尚未实机验收**。候选版精简设置菜单，调整天气入口，增加六分类横向新闻标签和统一的屏保图片管理。用户已休眠电视，需等用户开机并恢复操作后再部署。详见 [0.7.0 本地进度与待验收项](docs/desktop-070.md)。

## 使用

- 遥控器方向键移动，确定打开；预览区域确定进入原厂全屏信号源。
- 遥控器菜单键或右上角齿轮打开设置。
- “配置首页应用”：勾选要显示的应用并保存。“我的应用”始终保留。
- 长按首页应用可左移、右移或从首页移除。移除首页入口不卸载应用。
- 搜索覆盖本机已安装应用。
- 底部“原厂桌面”快捷按钮暂停接管并打开原厂；显式重开简净桌面后恢复此前开启的接管。按钮，位于常用应用和“我的应用”之间。
- 天气栏显示摄氏度、简短描述和天气图标，不显示城市。点击／确定直接打开小米天气；长按显示“选择天气位置”“使用网络自动定位”“立即刷新”三个选项。
- 左侧新闻有国内、国际、财经、科技、游戏、AI 六个固定标签，一次显示约三个；左右方向键移动标签焦点，鼠标可在标签栏滚轮横向查看。点击／确定切换并尝试刷新该分类，无须长按。标题打开摘要，底部翻页，长按页码也可刷新当前分类。每栏最多 12 条。
- 摘要中的阅读按钮使用电视系统 WebView，进入适合电视的正文阅读模式，无需浏览器。订阅源提供足够内容时直接排版并标明可能为节选；短摘要尝试获取原站正文。方向键滚动，上方按钮调整字号，返回键退出。WebView 不可用或文章在本次进程中已确认不可解析时隐藏入口；网络加载失败可在阅读页重试。
- 天气约每 15 分钟、当前分类新闻约每 10 分钟刷新；更新失败标记缓存，天气超过 6 小时、新闻超过 48 小时停止显示。位置可因运营商出口或代理而偏移，能在天气设置中手动更改。

## Home 接管

这台固件未提供标准无障碍设置页，“帐号与安全”中也没有该入口。首次启用或恢复接管，在电脑运行：

```powershell
python tools/enable_home.py
```

工具先保存现有授权，再通过应用自己的系统受限入口启用服务，向系统服务名单追加本项目，并检查实际绑定及原有两项小米服务是否仍在。它同时仅为本项目开启 GET_USAGE_STATS，用于弥补原厂主页缺失的窗口事件。应用没有 WRITE_SECURE_SETTINGS 权限。

服务监听原厂主桌面的窗口事件，并在独立线程每 400ms 检查最近 3 秒的应用前台/后台事件。内存中只保留当前包名和页面类名，仅在确认原厂主页且接管未暂停时打开新桌面；不读取屏幕文字，不保存应用使用历史。返回原厂、暂停或服务解绑后，最终切换决定会在主线程再次检查状态。另有开机广播恢复路径。是否已通过实际重启验收，以 docs/verification.md 为准。

## 本地图片屏保

遥控器菜单键 → 向下找到“本地图片屏保”。默认静置 12 分钟启动，每 30 秒切换；可调整等待时间、图片间隔，或关闭本地屏保。只在新桌面有焦点且前台时计时，打开应用、HDMI、对话框或设置时取消，按任意键退出屏保。电源键仍交给系统。

“图片管理与导入”统一显示缩略图和勾选状态，支持批量选择、放大查看、导入进度与批量移除。“添加图片”可进入电视相册、下载、图片文件夹、内部存储或可读取的 U 盘；按返回键逐级回退。这里使用应用内选择界面，不依赖系统图片选择器。支持 JPG、PNG、WebP、BMP，最多 24 张、单张最多 32MB。图片导入本应用私有目录，拔掉 U 盘后仍可播放；移除的只是屏保副本。未选图片时使用三幅本地绘制的风景和移动时钟，屏保没有联网代码。

也可点击“电脑 / 手机传图”，在同一局域网的浏览器打开电视显示的地址，输入六位连接码，选择多张图片保存。临时上传接收器仅在传图窗口打开且页面前台时运行，关闭窗口或切到其他应用即停止；不提供查看或下载照片的接口。传图内容通过本地 HTTP 发送。电脑回环传输测试已通过，电视端收图、USB 和遥控器操作仍待验收。

这台固件的原厂广告屏保由 `screen_saver_timeout` 独立触发，标准 Android 的 `screensaver_enabled` 原本已关闭。已备份后将原厂定时器设置为 `-2`，并核对实际读回；未禁用或卸载原厂相册包。

```powershell
python -B tools/screensaver_tv.py status
python -B tools/screensaver_tv.py disable
# 恢复备份中的原厂 12 分钟屏保设置（会恢复原厂广告屏保）
python -B tools/screensaver_tv.py restore
```

备份在 `nor/backups/screensaver-20261024/`，包括原设置和两个原厂 APK。恢复原厂桌面的工具与恢复原厂屏保是两个独立操作；在原厂设置重新选择屏保等待时间也可能覆盖关闭值。详见 [屏保验收](docs/screensaver-20261024.md)。

## 安装与构建

现有环境：Android Studio 位于 `ANDROID_STUDIO_HOME`，SDK 位于 `%LOCALAPPDATA%\Android\Sdk`，Java 11、SDK 32、Build Tools 32.0.0 已验证可构建。

```powershell
python -B tools/build.py --test
python -B -m unittest discover -s tests -p test_restore.py -v
python -B tools/build.py
adb -s TV_IP:5555 install -r nor/dist/quiet-tv.apk
adb -s TV_IP:5555 shell am start -n local.mitv.launcher/.MainActivity
```

构建脚本直接使用现有 SDK，不依赖 Gradle 下载。APK 在 `nor/dist/quiet-tv.apk`，本机开发签名密钥在 `nor/.local/launcher.keystore`；保留该密钥才能无卸载更新。可设置环境变量 `ANDROID_HOME`、`QUIET_TV_JDK` 改变路径。

也提供 Android Studio Gradle 工程配置（AGP 7.4.2，需 Gradle 7.5 或兼容版本）；先用上述脚本生成本机签名密钥。Gradle 导入路径未代替已验证的直接构建流程。

## 恢复

正常回原厂：底部“原厂桌面”按钮。电脑端恢复：

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File tools/restore_tv.ps1
```

本固件不允许 shell 用 `pm disable-user` 修改应用状态；脚本调用自建桌面的恢复入口，由应用禁用自己的 Home 服务并返回原厂，只有具备系统 DUMP 权限的调用方（ADB shell）可触发该入口。随后只从服务名单移除本项目，并撤销本项目的前台应用检测授权，保留其他服务和其他应用的权限。不会卸载或修改原厂包。

再次打开新桌面：

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File tools/restore_tv.ps1 -EnableNewLauncher
```

该命令只打开桌面。若要重新启用 Home 接管，运行 `python tools/enable_home.py`。设备重启可能关闭网络 ADB，需在电视设置中重新开启。

## 备份与边界

`nor/backups/2026-10-22/manifest.json` 记录 20 个 APK（约 308.7 MiB），包括当前原厂桌面和出厂版、设置、信号源、系统 UI、媒体管理、投屏及五个常用应用。每个文件已核对远端/本地 SHA-256、字节数和 ZIP CRC。备份还有包状态、系统设置和截图；这不是整机镜像，不包含完整应用私有数据。

原厂桌面和系统包均保留。HDMI 真实视频依赖外部设备提供信号；没有信号时显示明确状态和原厂蓝色背景。当前固件只注册了 HDMI3 的 TvInputInfo，HDMI1/2 走与原厂一致的播放器入口，但系统缺失输入时无法保证完成切换；具体实测记录见 docs/verification.md。

2026-10-24 的 0.3.2 通过了同一 APK 连续两次完整重启：自动进入新桌面、服务和授权均保留。已部署的 0.6.0 沿用同一接管实现，新增布局和阅读的实机验收见 [0.6.0 记录](docs/layout-reader-20261024.md)。0.7.0 当前仅完成本地验证，不能据此视为已部署或已通过实机验收。严格 3 秒性能检查仍有偶发超限，外部应用退出时可能更慢，不承诺所有场景 3 秒内。完整接管记录见 docs/verification.md。

取证文档在 nor/research/，涉及本机状态和原厂反汇编，不应整目录公开上传。

## 本地资料

Git 记录源码、工具、测试和文档；`nor/` 保存不提交的构建产物、原厂 APK 备份、签名密钥和取证资料。历史取证记录中的旧根目录路径均对应 `nor/` 下的同名目录。请单独备份 `nor/.local/launcher.keystore` 与原厂 APK；Git 不含这些文件。

## 数据源与开源复用

实际复用 MIT 许可的 Tabler Icons 天气图标，已保留原始 SVG、许可、版本及转换脚本。天气用 Open-Meteo API，位置搜索用 Open-Meteo / GeoNames；国内／国际新闻读取央视网页面数据；财经使用华尔街见闻快讯，科技、游戏、AI 分别使用 IT之家、机核、量子位的官方 RSS。分类聚合与订阅阅读交互参考 NewsNow、Read You，未打包其应用代码。新闻内容不属于开源代码。详见 [第三方说明](THIRD_PARTY_NOTICES.md)。

实机数据和 TLS 校验测试（先构建 APK，电视需开启 ADB）：

```powershell
python -B tools/check_dashboard.py
python -B tools/check_dashboard.py --news
python -B tools/check_home.py --max-seconds 8
```

Android 6 缺少较新的天气服务根证书，应用仅对固定天气／新闻数据服务补充公开 CA；保留系统链校验、主机名校验和 HTTPS。实机测试同时检查正常连接和三类无效证书拒绝。测试只运行临时 shell 程序，不安装测试应用。

电脑端数据源调研（不会连接电视）：

```powershell
python -B tools/research_news.py
```

直接构建会依据 SDK API 历史检查平台方法引用，阻止误用 API 24 以上接口；这不能替代 Android 6 实机验证。
