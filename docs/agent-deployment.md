# 给 agent 的小米电视部署、适配与恢复指南

目标：在保留原厂桌面、设置和信号源组件的前提下，安装简净桌面，并让Home和开机进入它。优先使用这里已经验证的方案，不需要重新尝试历史文章中的系统属性修改或批量禁用包。

已实测基线为MiTV4-ANSM0、Android6.0.1/API23。其他小米电视可以按同一流程检查和适配，但不能只因品牌相同就跳过原厂入口核对。文中的 `TV_IP`、`TV_SERIAL`、`MODEL`、`JDK_HOME`、`SDK_HOME` 是占位符，由目标用户或环境提供，不是仓库默认值。

## 1. 准备与最小兼容性检查

电脑需要ADB、Python3.11+、PowerShell；从源码构建还需JDK11、SDK32和Build Tools32.0.0。路径用环境变量传入：

```powershell
$env:QUIET_TV_JDK = 'JDK_HOME'
$env:ANDROID_HOME = 'SDK_HOME'
python -B tools/build.py --test
python -B tools/build.py
```

已有同签名APK时可直接使用，无须重建。新克隆生成的是自己的开发签名：不要卸载已有应用来掩盖签名不一致，否则会丢失其配置和私有屏保照片。更新包必须保持原签名和不降低versionCode；若维护者分发APK，应单独妥善管理发布密钥。

让用户在电视设置中开启网络ADB，按电视显示的地址和端口连接；不要扫描整个局域网或把5555当作所有机型必然使用的端口。

```powershell
$tv = 'TV_SERIAL' # 例如用户提供的TV_IP:PORT，或adb devices中的USB序列号
adb connect $tv  # 网络ADB使用；USB序列号跳过本行
adb -s $tv get-state
adb -s $tv shell getprop ro.product.model
adb -s $tv shell getprop ro.build.version.sdk
adb -s $tv shell getprop ro.build.fingerprint
adb -s $tv shell pm path com.mitv.tvhome
adb -s $tv shell dumpsys package com.mitv.tvhome
```

必须达到 `device` 状态，API至少23，型号与用户指定目标一致。`unauthorized`由用户在电视允许调试；`offline`可先 `adb disconnect $tv` 再连接，仍不恢复就让用户将ADB调试关／开一次。不要在同一台电脑连有手机时省略 `-s`，也不要反复 `adb kill-server` 打断其他设备。

核对适配要点（只需目标固件确认一次，记录在私有设备档案中）：

| 范围 | 当前实现的适配 |
| --- | --- |
| 原厂Home | 包`com.mitv.tvhome`，主页面`com.mitv.tvhome.MainActivityUserMode`或`com.mitv.tvhome.MainActivity` |
| 返回原厂 | 普通应用可调用的`com.mitv.tvhome/.MainActivity`别名 |
| 设置 | `com.xiaomi.mitv.settings`的导出入口／SettingsBroadcastReceiver |
| 文件管理 | `com.xiaomi.mitv.mediaexplorer/.NewScraperMainEntryActivity`，动作`duokan.mediaexplorer.intent.action.BROWSE_WITH_PATH` |
| 全屏HDMI | `com.xiaomi.mitv.tvplayer/.ExternalSourceActivity`，动作`com.xiaomi.mitv.tvplayer.EXTSRC_PLAY`，当前固件input=23/24/25 |
| 实时预览 | `TvInputManager`实际枚举到的输入，不能按另一个型号的硬件ID盲目套用 |

包名／主页面类名不符时，先适配`LauncherPolicy`、原厂返回入口及必要的`DeviceActions`／`PreviewController`，补对应测试并重建。`--expected-model`只是目标身份确认，**不是适配开关**。ADB shell能打开某个页面，也不能证明普通应用UID有权打开它；安装后还要从新桌面内点击验证。只缺某个设置入口时允许显示不可用提示，不为此禁用系统包或扩大权限。

如果目标固件确实提供默认Home选择，可以先用系统选择器选新桌面；本指南的已验证主路径是下面的无障碍接管，适用于原厂限制第三方Home的情况。不盲跑目标系统不存在的`set-home-activity`命令，也不修改`persist.appcontrol_w_mask`等属性。

## 2. 安装前备份

以下命令只读取电视并写入本地忽略目录：

```powershell
$model = 'MODEL' # 填前一步核对过的型号
$backup = Join-Path 'nor/backups' (Get-Date -Format 'yyyyMMdd-HHmmss')
python -B tools/backup_tv.py --serial $tv --output $backup
if ($LASTEXITCODE -ne 0) { throw '备份校验未通过，停止安装' }
```

脚本备份已存在的原厂桌面、设置、信号源、媒体等相关包，并尝试保存已知的出厂桌面；逐个核对远端／本地SHA-256、字节数和ZIP CRC。它不是整机镜像，不包含完整应用私有数据，也不保证发现其他固件改名后的系统包。对于新固件，先把核对到的必要包补入备份范围，确认manifest中至少有实际原厂Home、设置和信号源。不要把“0个失败”误当成“所有预期包都存在”。

记录原有无障碍服务、accessibility_enabled、默认Home、原厂广告屏保值和原包禁用状态到同一私有设备档案，敏感输出不要提交。后面的激活工具还会单独保存它将修改的服务设置。

## 3. 安装并先手动检查

```powershell
adb -s $tv install -r nor/dist/quiet-tv.apk
if ($LASTEXITCODE -ne 0) { throw '安装失败，先处理签名、版本或电视确认弹窗' }
adb -s $tv shell am start -W -n local.mitv.launcher/.MainActivity
if ($LASTEXITCODE -ne 0) { throw '启动失败，停止接管' }
```

部分固件会在电视显示“从其他设备安装应用”的确认框，此时 `adb install` 会停在 "Performing Push Install" 等待。用户允许本次即可，不需要勾选以后全部允许；用户不在电视旁时，agent 可用焦点键注入确认：先 `adb -s $tv shell input keyevent 22`（或 21）把焦点移到“确认／继续安装”，再 `input keyevent 23` 选中。仍不要跨机型用固定屏幕坐标盲点安装确认。若安装中途设备变 offline，先 `adb disconnect` 后重连；不恢复就让用户把「ADB调试」关／开一次再继续。

先从桌面内验证：遥控器焦点和返回、电视设置、文件管理、至少一个常用应用、原厂桌面快捷按钮；点原厂按钮后应留在原厂，再显式重开新桌面。它们可用后才启用接管。对同一固件的后续同签名更新，这些已确认入口可做简要回归，不必重复全部取证。

## 4. 启用Home接管并验收

```powershell
python -B tools/enable_home.py --serial $tv --expected-model $model
if ($LASTEXITCODE -ne 0) { throw '服务未确认绑定，按恢复章节检查' }
python -B tools/check_home.py --serial $tv --max-seconds 8
if ($LASTEXITCODE -ne 0) { throw 'Home回归失败，先恢复并排查，不做重启验收' }
```

这就是已适配固件上可以连贯执行的主流程：**备份 → 安装 → 手动打开检查 → enable_home → check_home**。需要无障碍接管时，工具会调用本应用自己的受系统DUMP权限保护的开关，仅给本应用启用GET_USAGE_STATS，保留并追加既有服务名单，验证真实绑定和读回，再打开桌面。应用本身没有WRITE_SECURE_SETTINGS权限。名单读取失败、空输出或格式异常必须停止，不能清空其他服务。

不要只看`settings`里有本项目名字，或`am broadcast`退出码为0就算成功。必须看到广播`result=1`、`dumpsys accessibility`中真实绑定，以及Home实际返回。某些Android6原厂主页不发窗口事件，UsageEvents后备路径就是为此保留的；不要为了调试请求读取窗口内容。

功能验收使用8秒窗口，记录实测延时；已验证设备存在3秒以上样本，因此不能承诺任意状态3秒内。再检查从视频应用／设置返回、原厂出口暂停、显式重开恢复，确认不会抢走其他应用前台。`pm disable-user`在部分固件会被拒绝且可能退出码仍为0；本项目通过自己的恢复组件关闭接管，不依赖禁用原厂包。

首次目标设备部署成功后，用户方便配合时再做两次完整重启，记录确实自动进入新桌面、服务绑定和授权保留。重启可能被固件关闭网络ADB，需用户重新开启；日常Home接管不依赖ADB常开。重连后先只读验证，不要先重新激活，否则不能证明配置在重启后保留。单纯文案／新闻源更新无须重复重启。

## 5. 原厂广告屏保与自己的照片

这是独立可选步骤，不与Home恢复混为一谈。先看当前值：

```powershell
python -B tools/screensaver_tv.py status --serial $tv --expected-model $model
```

在已验证固件上，原厂广告屏保使用`system:screen_saver_timeout`，标准Android的`screensaver_enabled=0`并不能关闭它。**仅在确认目标固件使用同一计时器语义后**执行：

```powershell
$saverBackup = Join-Path $backup 'screensaver-settings.json'
python -B tools/screensaver_tv.py disable --serial $tv --expected-model $model --backup $saverBackup
```

工具保存原值并将计时器设为`-2`，验证读回；已有备份不覆盖，带设备标识的备份不可用于另一个目标。键不存在或没有该固件的依据时停止这一步，先通过原厂屏保设置或受控短时闲置确认触发链，不批量禁用相册、广告、推送或服务包。历史无设备标识的备份只能在确认确属原设备后使用，不能当作通用默认值。`-1`在部分小米固件含“使用默认值”的含义，不把它当关闭值。

自己的图片入口：“本地图片屏保 → 图片管理与导入”。可从电视相册／目录导入，或“电脑／手机传图”后在同一局域网浏览器打开电视展示的临时地址，输入六位码。最多24张，单张32MB；关闭窗口或切应用即停服。导入保留私有副本，删副本不删原图，无图片则用内置风景。

验收一次多张上传／目录导入、坏图反馈、竖图预览、轮播、退出；临时把等待设1分钟验证实际闲置触发，并确认其他应用前台不被打断。恢复原来的等待和间隔，清理自有测试副本；不要删除用户照片。真正USB插拔与HDMI画面只在有对应硬件时验收，不以目录模拟、无信号背景或播放器打开代替。

## 6. 恢复与退出接管

日常回原厂：首页底部“原厂桌面”按钮。彻底撤销本项目接管：

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File tools/restore_tv.ps1 -Serial $tv -ExpectedModel $model
```

它先通过本应用关闭自己的服务并打开原厂，再只删除本项目服务条目、读回校验，撤销本项目Usage appop，保留其他应用授权。不卸载原厂APK。恢复失败时先查看它的明确错误和私有备份，不把整个服务名单设空，也不要手工禁用原厂桌面。未知固件若原厂返回类不符，先修正适配并验证；备份APK只用于确实需要重装时，不是日常回退步骤。

再次启用：显式打开新桌面后运行`enable_home.py --serial ... --expected-model ...`。`restore_tv.ps1 -EnableNewLauncher`只打开新桌面，不会重新授予接管。

恢复原厂屏保计时器是另一条命令，会恢复原厂屏保行为：

```powershell
python -B tools/screensaver_tv.py restore --serial $tv --expected-model $model --backup $saverBackup
```

## 7. 常见固件差异只做必要处理

- 文件管理：部分固件虽然声明`BROWSE_DEVICE_LIST`，代码却忽略它；已验证版本用`BROWSE_WITH_PATH`加新任务进入设备页。不能把空“视频”页判成存储为空，也不能保证小米路由NAS服务仍可用。
- HDMI：实际枚举输入；已验证固件只注册了HDMI3，HDMI1/2使用原厂同样的input参数也不能凭空创建TvInputInfo。适配依据是目标固件和真实信号，不伪造成功。
- 截图：部分旧固件`exec-out screencap -p`在PNG前输出诊断文本，应从PNG签名开始保存；安全视频页面可能不能截图。这与桌面崩溃不是同一结论。
- 自动化：本机曾在`uiautomator dump`断开时发生系统权限错误，不依赖它做激活；普通按键、截图和有限的`dumpsys`足够验证这些步骤。
- 新闻与天气：需要时运行`tools/check_dashboard.py --serial ... --expected-model ...`及`--news`；只检查科技两源及其正文可用`--science`。这会临时推送shell测试辅助文件，但不安装测试应用。科技类使用7天、其他类别48小时的有效期；过期或无内容不能伪造成网络成功，不用过期消息填充。

最初参考的三篇微博：[Home思路一](https://weibo.com/1655253920/RnoSYhIUm)、[思路二](https://weibo.com/1655253920/RnrZ0BGgQ)、[思路三](https://weibo.com/1655253920/Rnxh6fZzf)。本指南以本项目实际实现和验收为准，文章只是背景，不能直接照搬其中针对其他固件的包名和禁用操作。
