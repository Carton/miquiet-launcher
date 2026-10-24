# 数据源与第三方许可

桌面本身从头编写，未修改或重新打包小米原厂 APK。现有 HDMI 无信号背景在设备运行时通过原厂资源包加载，不随本项目分发。

## Tabler Icons — MIT

实际复用了 [Tabler Icons v3.31.0](https://github.com/tabler/tabler-icons/tree/v3.31.0) 的八个 SVG：sun、moon、cloud、mist、cloud-rain、cloud-snow、cloud-storm、cloud-question。

源码与原始许可保存在 `third_party/tabler-icons/`；SHA-256 和版本源见该目录的 `manifest.json`。`tools/import_weather_icons.py` 将其转换为 Android VectorDrawable，调整颜色、描边宽度，并组合出晴间多云与夜间多云图标。转换后的十个图标位于 `app/src/main/res/drawable/weather_*.xml`，无需 SVG 渲染库。原始 MIT 许可也打包进 APK，并可从天气设置中的“数据与开源说明”查看。

Copyright (c) 2020-2024 Paweł Kuna。完整许可见 [LICENSE](third_party/tabler-icons/LICENSE)。

## Open-Meteo — 天气数据 CC BY 4.0

使用 [Open-Meteo Forecast API](https://open-meteo.com/en/docs) 的 current temperature_2m、weather_code、is_day；摄氏度由 API 显式指定，按 WMO 编号映射中文描述和图标。这里的“当前天气”来自天气模型，非电视传感器测量。

数据按 [CC BY 4.0](https://creativecommons.org/licenses/by/4.0/) 提供；应用的天气设置中保留 Open-Meteo 署名与来源网址。免费公开接口面向非商业使用，本项目的个人电视使用符合此用途；若将来商业分发，应重新检查 [服务条款](https://open-meteo.com/en/terms)。服务端项目 [open-meteo/open-meteo](https://github.com/open-meteo/open-meteo) 为 AGPL，客户端这里只调用 API，没有复制或打包服务端代码。

手动城市搜索使用 [Open-Meteo Geocoding API](https://open-meteo.com/en/docs/geocoding-api)，其位置数据来源为 GeoNames。城市名称仅出现在设置中，不在首页显示。

## IPWhois — 网络约略定位

默认从电视访问 [IPWhois 免费 HTTPS 接口](https://ipwhois.io/documentation)，只请求 success、latitude、longitude 字段。它根据电视的出口 IP 给出约略坐标；代理、VPN 或运营商出口可能导致偏差，用户可在天气设置中改为手动位置。没有申请 Android 精确定位权限，不上传应用列表或新闻阅读记录。

这是在线服务，未复制其客户端或服务端代码，不属于本项目的 MIT 代码。

## 央视网 — 新闻内容

来自央视网公开 [国内新闻](https://news.cctv.com/china/) 与 [国际新闻](https://news.cctv.com/world/) 页面实际使用的 `cmsdatainterface/page/{china,world}_1.jsonp` 数据。只作为 JSON 文本解析，不执行 JSONP JavaScript。展示标题、摘要、发布时间、来源和原文入口；不下载新闻图片或正文。旧版 RSS 的内容已停更，未使用。

新闻内容版权归原权利人所有，**不是 MIT 或其他开源许可内容**。这是网页数据接口而非有稳定承诺的开放 API；接口变动时可能需要更新解析器。

## 更新与缓存

天气约 15 分钟、新闻约 10 分钟刷新，失败后约 2 分钟重试；只在桌面可见时触发周期刷新。新闻最多缓存各栏目 12 条，天气缓存只保留最后一次可用响应。天气超过 6 小时、新闻超过 48 小时不再显示。缓存均存在本应用私有 SharedPreferences 中，不进入 Git。联网保留 Android 系统的 TLS 证书校验，不降级为 HTTP，也不允许任意重定向。

## Android 6 的 TLS 兼容

设备实测缺少当前天气服务使用的根证书，首轮天气请求出现 `Trust anchor for certification path not found`。仅针对 IPWhois、Open-Meteo 天气与地理搜索三个固定主机，在默认系统信任之外补充 [ISRG Root X1](https://letsencrypt.org/certificates/) 和 [GTS Root R4](https://pki.goog/repository/) 公开 CA 证书。证书与下载来源、DER SHA-256 清单见 `app/src/main/res/raw/` 和 `third_party/tls-roots/manifest.json`。

没有信任任意证书、修改全局信任库或替换主机名校验。已在电视 API 23 上验证三个服务的有效证书通过，自签名、主机名不匹配和已过期证书均被拒绝。
