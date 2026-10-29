# 界面语言与回归检查

界面使用 Android 标准资源：中文默认文案在 `res/values/strings.xml`，英文在 `res/values-en/strings.xml`，调用处使用 `getString(R.string.xxx)`。Android 根据电视系统语言选择资源；未支持的语言回退中文，不另存桌面语言偏好，也不拦截系统语言变更后的 Activity 重建。

菜单的资源 ID 数组集中在 `UiLabels`；导航、天气分类和存储错误仍使用独立于 Android 的策略／枚举。用户输入的 HDMI 名称、已安装应用名、文章标题、正文和来源署名保留原文。`UiLocale` 只提供日期格式和少量英文排版判断，不查找或翻译字符串。

传图页面分别放在 `res/raw/upload_page.html` 和 `res/raw-en/upload_page.html`。`UploadStrings` 读取 Android 所选资源，再将页面与消息枚举对应的文案交给纯 Java 接收器。修改页面时，两版的连接码自动填入、hashchange、POST 校验和文件限制必须一致。

## 自动检查

```powershell
python -B -m unittest discover -s tests -p 'test_*.py' -v
python -B tools/build.py --test
python -B tools/build.py
python -B tools/build.py --locale-test
```

在已启动的 API 23 模拟器上检查实际打包资源（`EMULATOR_SERIAL` 替换为该模拟器的序列号）：

```powershell
adb -s EMULATOR_SERIAL push nor/dist/quiet-tv.apk /data/local/tmp/quiet-test.apk
adb -s EMULATOR_SERIAL push nor/dist/quiet-locale-tests.jar /data/local/tmp/quiet-locale-tests.jar
adb -s EMULATOR_SERIAL shell 'CLASSPATH=/data/local/tmp/quiet-test.apk:/data/local/tmp/quiet-locale-tests.jar app_process -Xmx64m /system/bin local.mitv.launcher.LocaleDataTest /data/local/tmp/quiet-test.apk'
```

辅助程序在独立资源上下文中检查中文、英文及未支持语言的回退，不更改设备语言或应用偏好。

## 人工验收

在模拟器系统设置里切换中英文，再返回桌面，检查首页、设置矩阵、外观预览、HDMI 命名、新闻摘要／阅读、照片库／传图及手动屏保。标准／大字、新闻／天气开关都需检查。保存、取消、方向键导航和最近 HDMI 排序应与界面语言无关。

模拟器适合验证资源、布局和通用交互；AOSP 镜像没有小米原厂入口与 HDMI 硬件，不能据此证明原厂设置、全屏切源、Home 接管或真实视频显示已通过。相关验收仍使用目标电视；截图和日志留在忽略的 `nor/`。
