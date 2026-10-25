# MiQuiet Launcher · 简净桌面

[简体中文](README.md) | **English**

A lightweight, open-source launcher for **Xiaomi TV / Mi TV**. Keep your favorite apps, input shortcuts and TV settings on a simple home screen, with weather, news and a local photo screensaver.

Written in native Java and released under the MIT License. Install through ADB and, on supported firmware, use the accessibility Home bridge to take over the Home button. The factory launcher remains available, and takeover can be reversed. No root or modification of Xiaomi system APKs is required. This is an independent community project, not an official Xiaomi product.

The default README and detailed deployment guide are in Simplified Chinese, reflecting the project's primary audience in mainland China.

## Features

- **Simple home screen:** no account cards, subscription promotions or channel navigation; retain volume, Wi-Fi, time and common settings shortcuts.
- **Remote-friendly settings:** a 4-column × 3-row grid of icons and labels, with a standard rounded gear, predictable directional navigation and focus restoration on close.
- **Choose your apps:** configure favorites, hold an app to reorder or remove its shortcut, search installed apps, and keep access to all apps and the factory launcher.
- **Inputs and settings:** HDMI shortcuts, the factory input selector, file manager and TV settings. Live preview depends on the inputs actually exposed by the firmware.
- **Weather:** Celsius temperature, Chinese condition text and icons. Select to open Xiaomi Weather; hold to change location, use approximate network location or refresh. The home screen does not show the city.
- **News:** six horizontally scrolling categories with a remote-friendly internal WebView reader. A separate browser is not required.
- **Local photo screensaver:** thumbnail selection, preview and batch removal, with imports from TV folders, the media library or a temporary LAN upload page on your computer or phone.

## Compatibility comes first

The current version is **0.8.3**, adding Left/Right image navigation and Back to exit manual screensaver previews. It has been tested on **MiTV4-ANSM0 running Android 6.0.1 / API 23**. The minimum runtime API is 23. Other Xiaomi TVs require firmware checks and possibly adaptation; matching the brand or Android version alone does not establish compatibility. See the [preview controls record](docs/saver-preview-083.md), [QR upload record](docs/qr-upload-082.md), [science-source record](docs/science-news-081.md) and [settings-grid record](docs/settings-grid-080.md).

## Screenshots

Home screen:

![MiQuiet Launcher running on a TV: news, inputs, favorite apps and the status bar](docs/images/home.png)

Settings menu (4 columns × 3 rows):

![MiQuiet Launcher settings on a TV: icon-and-label grid with visible remote focus](docs/images/settings.png)

These are actual version 0.8.0 screenshots. Apps, news and weather vary with configuration and time. HDMI was disconnected, so these images do not demonstrate live video preview. See the [image notes](docs/images/README.md) for provenance and rights.

Read the complete [deployment, adaptation and recovery guide (Chinese)](docs/agent-deployment.md) before using the device tools. It is also intended for agents helping other users deploy the launcher.

Known limits:

- Factory Home, settings, media and input components vary between firmware versions. Verify their exported entry points and behavior from this app.
- HDMI preview requires valid system `TvInputInfo` entries and a real signal. The tested firmware exposed only HDMI3; HDMI1/2 switching cannot be promised when their system registrations are missing.
- Some firmware turns network ADB off after reboot. Users may need to enable it again; normal Home takeover does not require ADB to stay connected.
- Disabling the factory advertising screensaver is firmware-specific. Physical USB insertion/removal and HDMI preview with a real signal still require hardware acceptance checks.

See [verification records](docs/verification.md) for results and their scope. Version 0.3.2 passed two full reboots with the same takeover implementation. In 0.7.1, three Home checks took 3.67, 3.61 and 2.89 seconds; the project does not promise takeover within three seconds in every situation. See [0.7.1 acceptance](docs/acceptance-20261025.md) and the [0.7.2 source update](docs/news-sources-072.md).

## Build and install

Tested host environment: Windows, Python 3.11+, JDK 11, Android SDK 32 and Build Tools 32.0.0. Set `QUIET_TV_JDK` or `JAVA_HOME` to your JDK and `ANDROID_HOME` to your SDK. The direct build does not download Gradle.

```powershell
python -B tools/build.py --test
python -B -m unittest discover -s tests -p 'test_*.py' -v
python -B tools/build.py
```

The output is `nor/dist/quiet-tv.apk`. The application ID remains `local.mitv.launcher`; the public project name does not change existing installations or signing.

Enable ADB on the TV, verify the target, back up its packages and check firmware compatibility as described in the deployment guide, then install and launch:

```powershell
adb -s TV_SERIAL install -r nor/dist/quiet-tv.apk
adb -s TV_SERIAL shell am start -W -n local.mitv.launcher/.MainActivity
```

Manually check apps, settings and the factory-launcher shortcut before enabling takeover:

```powershell
python -B tools/enable_home.py --serial TV_SERIAL --expected-model MODEL
python -B tools/check_home.py --serial TV_SERIAL --max-seconds 8
```

Replace `TV_SERIAL` and `MODEL` with the confirmed target values. Always select the device explicitly. Initial installation may require accepting debugging or installation prompts on the TV; see the guide for preflight checks and failure handling.

Keep `nor/.local/launcher.keystore` safe. A fresh clone creates its own development key; updates must use the same signature as the installed app. Do not uninstall to work around a signature mismatch, as that removes app settings and private screensaver photos. A Gradle project is included (AGP 7.4.2, requiring Gradle 7.5 or a compatible version), but the direct SDK build above is the verified path.

## Everyday use and recovery

Use the directional pad and OK button to navigate; Menu or the top-right rounded gear opens the settings grid. Move through its rows and columns with the directional pad. Back, Menu or the close tile dismisses it and restores focus to the gear; TV settings is the first tile. Configure favorites in the settings menu and hold an app shortcut to move or remove it. Removing a favorite does not uninstall its app. The factory-launcher shortcut pauses takeover; explicitly reopening MiQuiet resumes takeover if it was previously enabled.

The bridge preserves existing accessibility services and grants GET_USAGE_STATS only to this app. It uses narrow factory-Home window matching plus a background UsageEvents fallback for firmware that omits window events. It polls a recent three-second window every 400 ms and keeps only the current package/class state in memory. It does not read screen text or save application usage history. The app does not request WRITE_SECURE_SETTINGS.

To revoke this project's takeover and return to the factory launcher:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File tools/restore_tv.ps1 -Serial TV_SERIAL -ExpectedModel MODEL
```

The recovery tool removes only this project's accessibility entry and foreground-detection authorization, preserving other services. It does not uninstall factory packages. Use `enable_home.py` to reactivate takeover; `restore_tv.ps1 -EnableNewLauncher` only opens the app.

## News sources reflect personal preferences

**The default categories, sources and game-news filtering rules were chosen for the author's personal reading interests. You can adapt them to your own preferences; these defaults are not required.**

| Category | Current source |
| --- | --- |
| China / World | CCTV News |
| Finance | Wallstreetcn live updates |
| Technology | ScienceNet international research RSS + Science and Technology Daily science / innovation reports, interleaved |
| Games | GamerSky single-player news, with title/summary keyword rules to filter some content unrelated to PC gaming |
| AI | [AIHOT RSS](https://aihot.news/feed.xml) |

Changing sources currently requires editing the code. **There is no TV settings interface for adding arbitrary RSS feeds yet.** Main customization points:

- [NewsSources.java](app/src/main/java/local/mitv/launcher/NewsSources.java): categories, publisher labels, endpoints and allowed article domains.
- [DashboardRepository.java](app/src/main/java/local/mitv/launcher/DashboardRepository.java): response parsing, summaries, caching and supplied reader content. A different API format needs a corresponding parser; changing only the URL is not sufficient.
- [GameNews.java](app/src/main/java/local/mitv/launcher/GameNews.java): game-list parsing and keyword filters, which you can modify or remove.
- [ArticleHtml.java](app/src/main/java/local/mitv/launcher/ArticleHtml.java) and [LegacyTls.java](app/src/main/java/local/mitv/launcher/LegacyTls.java): article extraction and legacy TLS compatibility for fixed hosts when needed. Keep certificate and hostname validation intact.

Rebuild and reinstall after changes. When appropriate, run `tools/check_dashboard.py --serial TV_SERIAL --expected-model MODEL --news`. The UI currently assumes six categories, so changing their number also requires checking the tab, data and layout logic.

Select a category to switch and refresh, select a headline for its summary, and use the page control to browse more. The reader supports directional scrolling and text-size controls. It may show feed summaries or part of a publisher article and labels supplied content as potentially excerpted. Not every site template is supported; unavailable or confirmed unsupported reader entries are hidden, while network failures can be retried.

Weather refreshes approximately every 15 minutes and the active news category every 10 minutes. Failed updates retain recent cached data with a visible status; weather older than 6 hours is hidden. Science reporting has a seven-day window; other news has a 48-hour window. Science sources are interleaved and deduplicated by URL and normalized title, without semantic deduplication or subject-keyword guessing. Individual articles retain publisher and publication time; one source can keep working when the other fails. Network-based location can be inaccurate because of ISP routing or proxies; it can be changed manually in the weather menu.

## Local photo screensaver

Open the local screensaver settings and its photo library/import page. Import from the media library or TV folders, or choose computer/phone upload. Scan the QR code with a phone on the same LAN to open the upload page with its session code filled automatically. The TV generates the QR locally; it uses no external QR service. Manual address and six-digit code entry remain available. Closing the upload dialog or leaving the activity stops the server. Uploads use local HTTP; there is no interface for downloading existing photos.

In a manual screensaver preview, Left/Right cycles through images and Back exits. The control hint and image count remain visible; other directional keys and OK do not exit. Automatic slide rotation continues at the configured interval, which restarts after each manual switch. Both preview entry points use these controls. The idle-triggered screensaver still exits on any key.

Supported formats: JPG, PNG, WebP and BMP, up to 24 photos and 32 MB per photo. Imports are private copies; removing them does not delete the originals. Without selected photos, locally drawn landscapes and a moving clock are used. Defaults are 12 minutes of idle time and a 30-second slide interval, adjustable on the TV. The timer runs only while the launcher is foreground and focused, without interrupting apps, HDMI or settings. Any key exits the screensaver; the power key is left to the system. The screensaver itself has no networking code.

**The local screensaver and the factory advertising screensaver are separate settings.** On the tested firmware, the factory timer is `system:screen_saver_timeout`; the standard Android screensaver switch did not disable it. Follow the [deployment guide](docs/agent-deployment.md) to confirm firmware behavior, back up the original timer and verify changes. Do not bulk-disable system packages.

```powershell
python -B tools/screensaver_tv.py status --serial TV_SERIAL --expected-model MODEL
python -B tools/screensaver_tv.py disable --serial TV_SERIAL --expected-model MODEL --backup nor/backups/DEVICE/screensaver.json
python -B tools/screensaver_tv.py restore --serial TV_SERIAL --expected-model MODEL --backup nor/backups/DEVICE/screensaver.json
```

The last command restores the original factory screensaver behavior from that device's backup. It is independent of restoring Home takeover. Browser uploads, TV-folder/media imports, previews, removal and slide rotation have been tested. Physical USB insertion/removal remains unverified.

## Development, licensing and publication

- [AGENTS.md](AGENTS.md): development setup, module map, behavioral constraints and the deployment entry point for agents.
- [Third-party notices](THIRD_PARTY_NOTICES.md): Tabler Icons under MIT, weather attribution, public CA certificates and news sources. Weather uses Open-Meteo, with Open-Meteo / GeoNames location search and approximate IPWhois network location. News content and Xiaomi factory resources are not covered by this project's source-code license.
- [MIT License](LICENSE): project source-code license.
- [Publication checks](docs/publishing.md): privacy scanning, sanitized history and local-only files; [GitHub name and description](docs/github-about.md) contains ready-to-use repository metadata.

`nor/` holds ignored build artifacts, factory APK backups, signing keys, device configuration and research evidence. Back up your signing key and required factory APKs separately. APK exports are checked against remote/local SHA-256, size and ZIP CRC; they are not full-device images or backups of all private app data. The repository and its history have been sanitized to remove the original device address and development-machine paths. Never publish the pre-cleanup history recovery bundle. Before committing, run:

```powershell
python -B tools/audit_public.py --history
```

Optional on-device data/TLS checks, after building the APK and explicitly selecting an awake, authorized TV:

```powershell
python -B tools/check_dashboard.py --serial TV_SERIAL --expected-model MODEL
python -B tools/check_dashboard.py --serial TV_SERIAL --expected-model MODEL --news
python -B tools/check_home.py --serial TV_SERIAL --max-seconds 8
```

The application supplements missing public CA roots only for fixed weather/news service hosts and preserves chain, hostname and HTTPS validation. The device TLS suite checks valid connections and rejection of self-signed, wrong-host and expired certificates. Helpers run as temporary shell programs, not installed test apps. The build also checks platform references against API 23 history; this does not replace real-device testing.

For desktop-only source research, without connecting to a TV:

```powershell
python -B tools/research_news.py
```
