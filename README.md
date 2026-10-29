# 简净桌面 · MiTV Quiet Launcher

**简体中文** | [English](README.en.md)

面向**小米电视 / 小米TV**（Xiaomi TV / Mi TV）的轻量开源桌面：保留常用应用、信号源入口和电视设置，配上天气、新闻与本地图片屏保。原生 Java，MIT 开源；通过 ADB 安装，无需 Root，不修改、不覆盖原厂 APK；Home 接管可随时撤销，原厂桌面始终保留。

界面设计尽量保留原厂桌面的导航布局与遥控器操作习惯：顶部状态栏、中央信号源预览与入口、底部应用栏。新闻和天气可独立关闭，让熟悉的电视导航也能保持极简。

普通用户从[上手指南](docs/quick-start.md)开始：在电视上开启无线 ADB，其余交给 agent 完成。给其他 agent 的开发入口：[AGENTS.md](AGENTS.md)。

## 界面预览

首页：

![简净桌面首页实机截图：新闻、信号源、常用应用和顶部状态栏](docs/images/home.png)

设置菜单（4列×3排）：

![简净桌面设置菜单实机截图：图标文字矩阵与遥控器焦点](docs/images/settings.png)

截图来源与版权边界见[图片说明](docs/images/README.md)。

## 使用

- 方向键移动，确定打开；信号源预览区确定进入原厂全屏信号源（实测固件仅注册 HDMI3 的 TvInputInfo，HDMI1/2 走原厂播放器入口）。
- 菜单键或右上角齿轮打开设置矩阵；电视设置在首项，返回／菜单键退出；右下角“关于”显示项目名、版本与源码地址。
- “桌面设置 → 首页配置 → 选择首页应用”勾选要显示的应用，“我的应用”始终保留，可查看本机已安装应用。长按首页应用直接进入移动模式，左右键连续调整，确定保存、返回取消；离开桌面也会取消未保存的排序。移除入口可在首页应用选择中取消勾选，或在“我的应用”中长按，均不卸载应用。
- “首页配置 → 信号源名称”可给 HDMI1/2/3 手动命名（最多 8 个字符），也可长按首页对应 HDMI 入口修改。侧栏显示名称及端口号，预览标题显示如“游戏机 · HDMI1”；留空或“恢复默认”回到端口名。名称只保存在本桌面，不修改系统端口设置，也不代表自动识别设备。
- “首页配置 → 外观与内容”打开独立配置页：左侧设置、右侧本地首页示意预览。背景色有炭灰、深海蓝、墨绿；新闻标题、应用与入口名称可选标准或大字，首页焦点可选白色描边或柔和底色。方向键选择，确定修改候选，选择“应用到首页”保存；返回、菜单键或“取消”均放弃未保存修改。默认保留原有外观，预览不请求新闻、天气或视频。
- 同页可独立开关“显示新闻”“显示天气”，默认均开启。关闭新闻后信号源预览容器补上左侧空位，视频仍按原比例显示；关闭天气后状态栏图标靠拢时钟。关闭的模块不再发起自动刷新请求，缓存与天气位置设置保留，可随时重新开启。
- 底部“原厂桌面”按钮暂停接管并打开原厂；显式重开简净桌面后恢复此前开启的接管。
- 天气栏显示摄氏度、简短描述和天气图标，不显示城市；确定刷新并打开小米天气，长按可选择位置、网络自动定位或立即刷新。
- 新闻栏有国内、国际、财经、科技、游戏、AI 六个分类，左右键切换并刷新；标题打开摘要，底部翻页。摘要中的“阅读”用系统 WebView 进入电视正文模式：关闭脚本、方向键滚动、可调字号，网络失败可重试；财经与 AI 快讯直接使用原站摘要。
- 天气约每 15 分钟、当前新闻分类约每 10 分钟刷新；天气失效显示“--”，新闻失效保留缓存并标记。科技新闻保留 7 天，其他 48 小时。

## Home 接管

实测固件没有标准无障碍设置页，首次启用或恢复接管在电脑运行：

```powershell
python tools/enable_home.py --serial TV_SERIAL --expected-model MODEL
```

工具先备份现有无障碍授权，再启用本项目的 Home 桥接服务并核验实际绑定；同时仅为本应用开启 GET_USAGE_STATS，用于弥补部分固件不发送原厂主页窗口事件。桥接服务只匹配原厂主页面，不读取屏幕内容、不保存应用使用记录；原厂设置、播放器等子页面不受影响。

## 本地图片屏保

设置菜单中开启，默认静置 12 分钟启动、每 30 秒切图，可调整或关闭。只在桌面前台且有焦点时计时，不打断应用、HDMI 和设置；按任意键退出，电源键交给系统。

- 手动“预览屏保”：左右键切图、返回键退出，底部显示操作提示和当前张数；两个预览入口操作一致。
- “图片管理与导入”：缩略图、批量选择、放大查看、批量移除；可从电视相册、文件夹、内部存储或 U 盘导入（应用内选择界面）。支持 JPG、PNG、WebP、BMP，最多 24 张、单张 32MB；导入的是私有副本，移除不删原文件。
- “电脑 / 手机传图”：同一局域网扫码打开上传页，连接码自动填入，也可手动输入地址和六位连接码；二维码在电视本地生成。接收器只在传图窗口前台时运行，关闭或切走即停止。
- 未选图片时使用内置风景和移动时钟。

### 原厂广告屏保

原厂广告屏保由固件设置 `screen_saver_timeout` 独立触发，与标准 Android 屏保开关、本项目本地屏保均无关。工具先备份再关闭，可恢复：

```powershell
python -B tools/screensaver_tv.py status --serial TV_SERIAL --expected-model MODEL
python -B tools/screensaver_tv.py disable --serial TV_SERIAL --expected-model MODEL --backup nor/backups/DEVICE/screensaver.json
python -B tools/screensaver_tv.py restore --serial TV_SERIAL --expected-model MODEL --backup nor/backups/DEVICE/screensaver.json
```

恢复 Home 接管与恢复原厂屏保是两个独立操作，详见[屏保验收](docs/screensaver-20261024.md)。

## 安装与构建

### 下载安装（无需编译环境）

1. 在 [Releases](https://github.com/Carton/mitv-quiet-launcher/releases) 下载最新版 `quiet-tv.apk`。
2. 用无线 ADB 安装：按[上手指南](docs/quick-start.md)在电视开启 ADB 后，`adb connect 电视IP:端口`，再 `adb -s 电视IP:端口 install -r quiet-tv.apk`。也可以把上手指南里那句话交给 agent，由它下载、安装并接管 Home。
3. 或将 APK 拷入 U 盘，用电视自带文件管理器打开安装（此路径未实测，视固件而定）。

### 自行构建

已验证 Windows、JDK 11、SDK 32、Build Tools 32.0.0，电脑端工具需 Python 3.11+。用 `QUIET_TV_JDK`／`JAVA_HOME` 指定 JDK，`ANDROID_HOME` 指定 SDK；`TV_SERIAL` 和 `MODEL` 为占位符。

```powershell
python -B tools/build.py --test
python -B -m unittest discover -s tests -p 'test_*.py' -v
python -B tools/build.py
adb -s TV_SERIAL install -r nor/dist/quiet-tv.apk
adb -s TV_SERIAL shell am start -W -n local.mitv.launcher/.MainActivity
```

- 产物为 `nor/dist/quiet-tv.apk`；构建直接使用现有 SDK，不下载 Gradle，并含 API 23 历史检查以阻止误用高版本接口。
- 保留本机 `nor/.local/launcher.keystore` 才能免卸载覆盖更新；新克隆会生成自己的开发密钥。更新必须与已安装版本同签名，卸载重装会丢失配置和屏保照片。
- 包名 `local.mitv.launcher` 与 APK 文件名 `quiet-tv.apk` 固定，不随项目名变化，不影响现有安装和签名。
- 另含 Android Studio Gradle 工程（AGP 7.4.2，需 Gradle 7.5+）；已验证的构建路径仍是上面的直接构建。

## 恢复

底部“原厂桌面”按钮直接回原厂；电脑端完整恢复并撤销接管：

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File tools/restore_tv.ps1 -Serial TV_SERIAL -ExpectedModel MODEL
```

脚本经应用自建的恢复入口禁用 Home 桥接并返回原厂，只移除本项目的无障碍条目和 GET_USAGE_STATS 授权，不动原厂包。加 `-EnableNewLauncher` 仅打开新桌面；重新启用接管再运行 `enable_home.py`。设备重启可能关闭网络 ADB，需在电视设置中重新开启。

## 新闻来源可以自己调整

默认分类、来源和游戏过滤规则按作者个人阅读偏好选择，可按自己的兴趣修改，不必沿用：

| 类别 | 当前来源 |
| --- | --- |
| 国内、国际 | 央视网 |
| 财经 | 华尔街见闻快讯 |
| 科技 | 科学网国际科研RSS＋科技日报国际科技／创新连线，交错展示 |
| 游戏 | 游民星空单机资讯，按标题／摘要关键词过滤与PC游戏无关的内容 |
| AI | [AIHOT RSS](https://aihot.news/feed.xml) |

更换来源目前需要修改源码，还没有电视端任意添加 RSS 的设置界面。主要修改位置：

- [NewsSources.java](app/src/main/java/local/mitv/launcher/NewsSources.java)：分类、来源名称、请求地址和文章域名。
- [DashboardRepository.java](app/src/main/java/local/mitv/launcher/DashboardRepository.java)：响应解析、摘要与缓存；不同接口格式需要对应解析，不能只替换 URL。
- [GameNews.java](app/src/main/java/local/mitv/launcher/GameNews.java)：游戏列表解析与关键词过滤，可修改或取消过滤。
- [ArticleHtml.java](app/src/main/java/local/mitv/launcher/ArticleHtml.java)、[LegacyTls.java](app/src/main/java/local/mitv/launcher/LegacyTls.java)：原站正文适配与固定主机的旧系统 TLS 兼容，保留证书和主机名校验。

改完重建安装，按需运行 `tools/check_dashboard.py --serial TV_SERIAL --expected-model MODEL --news` 验证。当前 UI 固定六个分类，增减分类要同步检查标签、数据与布局。

## 数据与开源

- 图标复用 MIT 许可的 [Tabler Icons](https://github.com/tabler/tabler-icons)（天气）与 [Lucide](https://github.com/lucide-icons/lucide)（设置），保留原始 SVG、许可与转换脚本。
- 天气使用 Open-Meteo API，位置搜索 Open-Meteo / GeoNames；Android 6 缺少部分较新的根证书，仅为固定天气／新闻主机补充公开 CA，保留证书链与主机名校验。
- 新闻直连央视网、华尔街见闻、科学网、科技日报、游民星空和 AIHOT；分类与阅读交互参考 NewsNow、Read You，未复制其代码。新闻内容不属于本项目开源范围。
- 源码采用 [MIT 许可证](LICENSE)，第三方与数据边界详见 [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md)。

实机检查工具（电视需开启 ADB）：

```powershell
python -B tools/check_dashboard.py --serial TV_SERIAL --expected-model MODEL --news
python -B tools/check_home.py --serial TV_SERIAL --max-seconds 8
python -B tools/research_news.py   # 电脑端新闻源调研，不连接电视
```

`nor/` 保存不提交的构建产物、签名密钥、原厂 APK 备份和取证资料，注意单独备份 `nor/.local/launcher.keystore` 与原厂 APK。维护者发布前运行 `python -B tools/audit_public.py --history`，流程见[发布前检查](docs/publishing.md)。

本项目是社区开源项目，与小米官方无关联。
