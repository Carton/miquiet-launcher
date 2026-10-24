# 0.5.0 本地屏保

## 原因与替换范围

用户报告在新桌面静置后出现原厂广告屏保。实机读取到：

- `Settings.System.screen_saver_timeout=720000`，即 12 分钟。
- 标准 `Settings.Secure.screensaver_enabled=0`，`dumpsys dreams` 无该服务；这不是标准 Android DreamService 路径。
- 最近运行的原厂界面为 `com.xiaomi.tv.gallery/.view.ScreenSaverActivity`。

已有原厂框架取证 `nor/research/accessibility-audit/smali/com/android/server/power/MitvPowerManagerService.smali`：`getScreenSaverTimeoutLocked` 读取小米定时参数；负值且不是 `-1` 时将调度延迟设为 `Integer.MAX_VALUE`。`computeScreenSaver` 在参数为负时不会发送屏保广播。非负且条件满足时发送 `com.duokan.intent.action.show.screensaver`。

将等待临时缩短为 20 秒后，原厂日志出现 `it's time to start screen saver`，前台成为上述相册屏保页面，复现了触发链。随后改为 `-2`，日志确认 `setting change` 后延迟为 2147483647。未禁用、卸载或修改原厂 APK，未改屏幕休眠、电源键或标准 Android 屏保开关。

原设置与原厂相册、TVSmartScreenSaver APK 已保存到 `nor/backups/screensaver-20261024/`。两份 APK 的 ZIP CRC、大小、本地及设备 SHA-256 均核对通过。`tools/screensaver_tv.py restore` 恢复原有 720000，`disable` 再设 -2；两个方向均已实机读回验证。Home 恢复工具与原厂屏保恢复保持独立。

## 自建屏保

- 菜单 → 本地图片屏保；默认开启，等待 12 分钟、每 30 秒切换。
- 等待时间 1/2/5/10/12/15/30/60 分钟；图片间隔 10/15/30/60/120/300 秒。
- 新桌面前台、有窗口焦点、屏幕处于交互状态才启动。按键或触摸重置计时；对话框、应用和 HDMI 占据前台时取消计时。屏保按任意普通按键退出，电源键交给系统。
- 从电视或 U 盘目录选择，或用系统图片选择器。最多 24 张，每张最多 32MB，校验可解码图片后保存到应用私有目录，不依赖 U 盘持续连接。
- 管理图片只移除私有副本。解码缩小至屏幕所需且最长边不超过约 2048 像素，JPEG 方向信息会应用。
- 无自选图片时使用三个本地 Canvas 风景。图片降低亮度，时钟位置随轮播移动；屏保没有网络代码。新闻／天气定时刷新随主桌面暂停。

## 验收

- 26 项 Home 策略、22 项数据策略、13 项屏保策略断言通过；4 项既有恢复工具测试通过。
- API 23 上运行 `SaverDataTest`，10 项断言通过：无效图片拒绝、失败临时文件清理、PNG 导入、缩小解码与像素、24 张上限及上限后状态保留。测试只使用新建的 shell 临时目录，不读取用户图片。
- 实机预览显示本地风景与移动时钟，方向键退出后回到原来的桌面。
- 实际从电视存储目录导入一张测试 PNG，删除测试源文件后仍可播放；日志确认 `started images=1` 与 `local=true`。
- 通过设置将等待临时改为 1 分钟：从桌面静置至 62.72 秒的观察样本进入 `.ScreensaverActivity`，未出现原厂屏保。
- 在本地屏保等待为1分钟时，视频应用保持前台约70秒，没有被本地屏保或原厂相册打断。HDMI无信号这一轮约24秒后回到桌面，随后才重新计时并进入本地屏保，不能将该轮算作持续HDMI播放验收。观察与最终部署信息保存在 `nor/research/screensaver/`。
- 本次不重复完整重启；0.3.2 的冷启动验收仍是此前接管实现的历史证据。
- 最终版连续 Home 返回 2.30 / 1.56 / 1.67 秒；三项原有及自建无障碍服务均绑定，GET_USAGE_STATS 保留 allow，禁用包仍只有原有 com.mitv.care。
- 最终 APK 为 75,279 字节、versionCode 9，v1/v2/v3 签名有效，与已安装 APK 的 SHA-256 一致：`a07296811de0c1ec6529ac6a30d1d442e324bb631e789a9594192b5740e2f2b9`。最终预览截图为 `nor/dist/screensaver-final.png`。

测试图片已通过管理界面移除，恢复默认 12 分钟／30 秒，用户可自行添加照片。真实 U 盘设备未插入，已验证的是相同目录导入路径及源文件移除后的本地副本播放。

复测图片导入规则（先构建 APK，设备开启 ADB）：

```powershell
python -B tools/build.py --saver-test
adb -s TV_IP:5555 push nor/dist/quiet-tv.apk /data/local/tmp/quiet-saver-app.apk
adb -s TV_IP:5555 push nor/dist/quiet-saver-tests.jar /data/local/tmp/
adb -s TV_IP:5555 shell 'CLASSPATH=/data/local/tmp/quiet-saver-tests.jar:/data/local/tmp/quiet-saver-app.apk app_process /system/bin local.mitv.launcher.SaverDataTest'
```
