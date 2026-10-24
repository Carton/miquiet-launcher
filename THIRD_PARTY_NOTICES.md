# 数据源与第三方许可

桌面本身从头编写，未修改或重新打包小米原厂 APK。现有 HDMI 无信号背景在设备运行时通过原厂资源包加载，不随本项目分发。

## Tabler Icons — MIT

实际复用了 [Tabler Icons v3.31.0](https://github.com/tabler/tabler-icons/tree/v3.31.0) 的八个 SVG：sun、moon、cloud、mist、cloud-rain、cloud-snow、cloud-storm、cloud-question。

源码与原始许可保存在 `third_party/tabler-icons/`；SHA-256 和版本源见该目录的 `manifest.json`。`tools/import_weather_icons.py` 将其转换为 Android VectorDrawable，调整颜色、描边宽度，并组合出晴间多云与夜间多云图标。转换后的十个图标位于 `app/src/main/res/drawable/weather_*.xml`，无需 SVG 渲染库。原始 MIT 许可也打包进 APK，并可从桌面设置底部的“数据与开源说明”查看。

Copyright (c) 2020-2024 Paweł Kuna。完整许可见 [LICENSE](third_party/tabler-icons/LICENSE)。

## Open-Meteo — 天气数据 CC BY 4.0

使用 [Open-Meteo Forecast API](https://open-meteo.com/en/docs) 的 current temperature_2m、weather_code、is_day；摄氏度由 API 显式指定，按 WMO 编号映射中文描述和图标。这里的“当前天气”来自天气模型，非电视传感器测量。

数据按 [CC BY 4.0](https://creativecommons.org/licenses/by/4.0/) 提供；应用的“数据与开源说明”中保留 Open-Meteo 署名与来源网址。免费公开接口面向非商业使用，本项目的个人电视使用符合此用途；若将来商业分发，应重新检查 [服务条款](https://open-meteo.com/en/terms)。服务端项目 [open-meteo/open-meteo](https://github.com/open-meteo/open-meteo) 为 AGPL，客户端这里只调用 API，没有复制或打包服务端代码。

手动城市搜索使用 [Open-Meteo Geocoding API](https://open-meteo.com/en/docs/geocoding-api)，其位置数据来源为 GeoNames。城市名称仅出现在设置中，不在首页显示。

## IPWhois — 网络约略定位

默认从电视访问 [IPWhois 免费 HTTPS 接口](https://ipwhois.io/documentation)，只请求 success、latitude、longitude 字段。它根据电视的出口 IP 给出约略坐标；代理、VPN 或运营商出口可能导致偏差，用户可在天气设置中改为手动位置。没有申请 Android 精确定位权限，不上传应用列表或新闻阅读记录。

这是在线服务，未复制其客户端或服务端代码，不属于本项目的 MIT 代码。

## 央视网 — 新闻内容

首页来自央视网公开 [国内新闻](https://news.cctv.com/china/) 与 [国际新闻](https://news.cctv.com/world/) 页面实际使用的 `cmsdatainterface/page/{china,world}_1.jsonp` 数据。只作为 JSON 文本解析，不执行 JSONP JavaScript。首页获取标题、摘要、发布时间、来源。旧版 RSS 的内容已停更，未使用。

0.6.0 起，用户点击“阅读原文”后，通过电视自带 WebView 载入对应央视文章，读取原文并在本机排版为大字号正文，可能加载文章内的图片。保留央视来源，不打包或提交新闻正文。原站模板脚本仅在取正文阶段启用；阅读文档关闭脚本、文件和 Content URI 访问，没有 JavaScript 与本地代码的桥接，证书错误会取消连接。API 行为依据 [Android WebView 文档](https://developer.android.com/develop/ui/views/layout/webapps/webview) 与 [WebViewClient 文档](https://developer.android.com/reference/android/webkit/WebViewClient)。

新闻内容版权归原权利人所有，**不是 MIT 或其他开源许可内容**。这是网页数据接口而非有稳定承诺的开放 API；接口变动时可能需要更新解析器。

## 0.7.0 新增新闻来源

- 财经：[华尔街见闻](https://wallstreetcn.com/)公开快讯，直连 `api-one.wallstcn.com/apiv1/content/lives?channel=global-channel&limit=30`，使用发布时间、标题、正文摘要及原站链接。
- 科技：[IT之家 RSS](https://www.ithome.com/rss/)。
- 游戏：[机核 RSS](https://www.gcores.com/rss)。该订阅含游戏与游戏文化内容。
- AI：[量子位 RSS](https://www.qbitai.com/feed)。该订阅可能只有简短摘要；点击原文时尝试从量子位静态文章页提取正文。

电脑端已确认六个来源均 HTTP 200，当前有 48 小时内内容；原始响应和 SHA-256 只保存在忽略的 `nor/research/update-070/feeds/`。这不代表 Android 6 上已联网验收。桌面显示实际来源与文章时间，不把抓取时间冒充发布时间。量子位等来源的更新频率可能低于综合新闻，因此条数可能不足一页。

RSS 由 Android 自带 SAX XML 解析器读取，不执行脚本，不允许 DTD 或外部实体。订阅中较长的内容直接送入内置阅读器，明确标为“订阅内容，可能为节选”；短摘要在用户打开时才尝试获取原站。静态正文请求使用相同的 HTTPS 校验和定长上限，模板不支持则保留摘要。阅读文档关闭脚本，并移除脚本、外部样式和嵌入表单等元素。原文和图片版权仍归原权利人，不属于本项目开源代码，不打包进安装文件、不提交到 Git。

## 聚合与阅读交互参考

研究了 [NewsNow](https://github.com/ourongxing/newsnow) 的分类和来源卡片组织，以及 [Read You](https://github.com/ReadYouApp/ReadYou) 的 RSS、摘要和正文阅读流程。NewsNow 是 [MIT](https://github.com/ourongxing/newsnow/blob/main/LICENSE)，Read You 是 GPL-3.0。只参考交互和公开接口结构，没有复制其 TypeScript / Kotlin 应用代码，也没有接入其公共聚合代理；当前解析器及电视界面自行编写。原有实际复用的图标仍按前文 MIT 许可署名。

## 更新与缓存

天气约 15 分钟、当前新闻分类约 10 分钟刷新，失败后约 2 分钟重试；只在桌面可见时触发周期刷新。新闻每个栏目解析后最多展示 12 条；为恢复缓存保留有界原始响应，每个来源不超过 512KiB，天气缓存只保留最后一次可用响应。天气超过 6 小时、新闻超过 48 小时不再显示。缓存均存在本应用私有 SharedPreferences 中，不进入 Git。联网保留 Android 系统的 TLS 证书校验，不降级为 HTTP，也不允许任意重定向。

## Android 6 的 TLS 兼容

设备实测缺少当前天气服务使用的根证书，首轮天气请求出现 `Trust anchor for certification path not found`。针对 IPWhois、Open-Meteo 天气与地理搜索，以及新增新闻来源的固定数据主机，在默认系统信任之外补充 [ISRG Root X1](https://letsencrypt.org/certificates/) 和 [GTS Root R4](https://pki.goog/repository/) 公开 CA 证书。证书与下载来源、DER SHA-256 清单见 `app/src/main/res/raw/` 和 `third_party/tls-roots/manifest.json`。

没有信任任意证书、修改全局信任库或替换主机名校验。已在电视 API 23 上验证三个服务的有效证书通过，自签名、主机名不匹配和已过期证书均被拒绝。

0.7.0 将同一组额外公开根证书限定扩展到 `api-one.wallstcn.com`、`www.ithome.com`、`www.gcores.com`、`www.qbitai.com` 的应用数据请求，保留默认主机名校验。WebView 的证书错误仍会取消，不使用 `SslErrorHandler.proceed()`。这些新增主机的 Android 6 TLS 验证已准备好，等电视开机后执行。
