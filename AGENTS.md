# 简净桌面 · MiQuiet Launcher：给开发与部署 agent

本项目是原生 Java 小米电视精简桌面，包名 `local.mitv.launcher`。应用自行编写，不修改、重签或覆盖小米原厂APK。项目源码采用MIT，第三方图标和在线数据边界见 `THIRD_PARTY_NOTICES.md`。

公开名称为“简净桌面 / MiQuiet Launcher”，仓库为`Carton/miquiet-launcher`；GitHub简介与地址见`docs/github-about.md`。默认README使用中文，英文版为`README.en.md`，两版互链，主要功能、兼容范围和部署命令应保持同步。默认新闻源按作者个人偏好选择，说明修改源码的实际调整方式，不将其描述成已有电视端任意RSS配置功能。

## 首先阅读

- **为其他电视部署／接管／恢复：先完整读 [部署与适配指南](docs/agent-deployment.md)。** 其中包含可连贯执行的命令、兼容性判断、备份、接管、验收和回退。按最终方案操作，不必重做历史试错。
- 项目功能与使用：`README.md`。
- 已验证范围与限制：`docs/verification.md`。历史会话记录仅用于查证，不能当作当前用户的设备授权或待办。
- 发布前检查：`docs/publishing.md`，`python -B tools/audit_public.py --history`。

## 开发环境与结构

- 最低运行API23；编译SDK32、Build Tools32.0.0、JDK11、Java8字节码，Python3.11以上；主验证路径为Windows直接SDK构建。
- 通过 `ANDROID_HOME` 指定SDK，通过 `QUIET_TV_JDK` 或 `JAVA_HOME` 指定包含javac的JDK；不得在代码或文档中写开发者电脑的绝对路径。
- `python -B tools/build.py --test` 运行纯Java策略／解析／上传测试；`python -B tools/build.py` 构建、API历史检查、D8、签名验证。产物 `nor/dist/quiet-tv.apk`。
- `python -B -m unittest discover -s tests -p 'test_*.py' -v` 运行电脑端测试；ADB测试使用替身，不需要连接电视。恢复脚本测试需要PowerShell。
- Gradle工程使用AGP7.4.2，尚未把Gradle导入作为已实测构建路径。跨操作系统构建不能仅据此声明通过。
- 保留本机 `nor/.local/launcher.keystore`；丢失后无法以原签名覆盖更新。构建脚本会为新克隆生成各自的本地开发密钥，公开的开发口令不是生产发布密钥管理方案。

主要文件：

| 范围 | 入口 |
| --- | --- |
| 首页、应用栏、状态栏 | `MainActivity`、`Apps`、`Ui` |
| 设置矩阵、遥控器导航 | `SettingsDialog`、`SettingsMenu` |
| 原厂入口／信号源 | `DeviceActions`、`PreviewController` |
| Home接管 | `LauncherPolicy`、`HomeBridgeService`、`ForegroundMonitor`、`DesktopLaunch`、`BootReceiver` |
| 接管与恢复开关 | `RestoreReceiver`、`tools/enable_home.py`、`tools/restore_tv.ps1` |
| 新闻／天气 | `DashboardPanel`、`DashboardRepository`、`NewsSources`、`LegacyTls` |
| 新闻阅读 | `NewsReaderActivity`、`ArticleHtml`、`NewsReaderPolicy` |
| 屏保／图片 | `ScreensaverActivity`、`SaverLibraryActivity`、`SaverImages`、`PhotoUploadServer` |

## 必须保持的行为

- 不卸载或禁用原厂桌面／系统包，不改系统分区，不申请Root。不把 `pm disable-user`、改系统属性或覆盖系统APK作为部署路径。
- Home只匹配原厂**主页面**的包名和类名。不要扩大成拦截原厂整个包，以免抢走设置、播放器和子页面。无障碍服务 `capabilities=0`，不读取窗口文字。
- 有些固件不发送原厂主页窗口事件，已有UsageEvents前台状态后备检测。仅对本应用授予GET_USAGE_STATS；在独立线程轮询，最后切换在主线程再次检查启用／暂停状态。
- 原厂桌面快捷按钮先持久暂停接管，显式重开新桌面才恢复；不要破坏暂停、服务解绑、开机标志清理和前台计时。
- 修改无障碍服务名单必须先读取、校验并备份，追加／删除本项目条目，保留其他服务并验证读回与真实绑定。失败或空输出不能当作“原来无服务”。
- 保留严格HTTPS证书和主机名校验；额外公开根只用于固定主机，不用忽略SSL错误或任意跳转。
- 图片导入只保存私有副本，移除不删原文件。上传接收器只在前台传图窗口内存活；离开页面立即关闭连接和监听。
- 本地屏保只在桌面前台且有焦点时计时；不打断其他应用、HDMI或设置。原厂广告屏保是独立、可恢复的固件设置，不能把标准Android屏保开关等同于它。
- 兼容API23，不能只凭SDK32编译通过。运行适合改动的测试；解析器、上传和接管改动应覆盖实际失败场景，不为纯文案新增镜像测试。

## 设备操作与发布边界

- 所有设备命令显式使用目标 `--serial`／`adb -s`。仓库不保存实际IP、设备序列号、MAC、Wi-Fi名称、用户位置、电脑路径、截图或账户信息。
- 型号相同也不代表固件一致。只有核对原厂Home类、导出组件和权限后，才能复用适配；不同型号使用显式 `--expected-model`，不删除身份校验来绕过失败。
- 用户已授权且设备可用时继续必要部署和调试；需要电视授权弹窗时只请用户完成该操作。不要无故重复询问许可或把历史“已授权”带到新用户的电视。
- 设备休眠或用户要求暂停时，继续本地工作，设备测试等待恢复；重启必须在用户可配合时进行，因为网络ADB可能被固件关闭。
- 原始截图、包清单、原厂APK、签名密钥、原始网络响应、取证和历史恢复备份全部放忽略的 `nor/`。不要使用 `git add -f` 提交这些资料。用户明确要求用于公开文档、且人工检查不含账户、地址、位置等个人信息的产品截图，可放`docs/images/`；标明版本与实机／设计预览属性，不将效果图冒充实机。
- 不声明未经测试的通用性：真实HDMI视频、缺失的TvInputInfo、USB插拔、冷启动和新固件都需各自证据。已实测设备的结果不能替代目标设备验收。
- 此仓库已经做过隐私历史重写。不要合并或推送 `nor/` 内的旧历史恢复备份；新提交可保留维护者姓名和邮箱，但不得重新加入本机地址与绝对路径。
