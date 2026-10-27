# MiTV Quiet Launcher · 简净桌面

[简体中文](README.md) | **English**

A lightweight, open-source home screen for **Xiaomi TV / Mi TV**: keep your favorite apps, input shortcuts and TV settings, with weather, news and a local photo screensaver. Written in native Java under the MIT License; installed over ADB with no root and no modification or replacement of factory APKs. Home takeover is reversible at any time, and the factory launcher always remains available.

New users can start with the [quick-start guide (Chinese)](docs/quick-start.md): enable wireless ADB on the TV and hand the rest to an agent. Development entry point for agents: [AGENTS.md](AGENTS.md).

## Screenshots

Home screen:

![MiTV Quiet Launcher running on a TV: news, inputs, favorite apps and the status bar](docs/images/home.png)

Settings menu (4 columns × 3 rows):

![MiTV Quiet Launcher settings on a TV: icon-and-label grid with visible remote focus](docs/images/settings.png)

See the [image notes](docs/images/README.md) for provenance and rights.

## Everyday use

- Move with the directional pad and open with OK; select the input preview to open the factory full-screen source (the tested firmware registers TvInputInfo only for HDMI3; HDMI1/2 go through the factory player entry).
- Menu or the top-right rounded gear opens the settings grid; TV settings is the first tile. Back or Menu dismisses it; the bottom-right About tile shows the project name, version and source repository.
- “Configure home apps” picks the apps shown; “My Apps” is always kept. Hold a home shortcut to move or remove it — removing does not uninstall the app. Search covers installed apps.
- The bottom “factory launcher” button pauses takeover and opens the factory launcher; explicitly reopening MiTV Quiet Launcher resumes the previous takeover state.
- The weather bar shows Celsius, a short description and an icon, without the city; OK refreshes the weather and opens Xiaomi Weather; hold to choose a location, network locating or refresh now.
- News has six categories (China, World, Finance, Tech, Games, AI), switched with Left/Right; headlines open summaries with paging. “Read” in a summary opens a TV-friendly reader in the system WebView: scripts disabled, directional scrolling, adjustable text size, retry after network failures. Finance and AI flashes use the source summaries directly.
- Weather refreshes about every 15 minutes and the active news category every 10 minutes; stale weather shows “--” and failed news updates keep cached items marked. Science news keeps 7 days, other categories 48 hours.

## Home takeover

The tested firmware has no standard accessibility settings page; enable or restore takeover from a computer:

```powershell
python tools/enable_home.py --serial TV_SERIAL --expected-model MODEL
```

The tool backs up existing accessibility grants, enables this project's Home bridge service and verifies the actual binding; it also grants GET_USAGE_STATS to this app only, covering firmware that does not emit factory-Home window events. The bridge matches only the factory main home page — it does not read screen content or save usage history, and factory settings, the player and other sub-pages are unaffected.

## Local photo screensaver

Enable it in the settings menu; by default it starts after 12 idle minutes and changes images every 30 seconds, both adjustable. Timing runs only while the launcher is foreground and focused, without interrupting apps, HDMI or settings; any key exits, and the power key is left to the system.

- Manual preview: Left/Right cycles images and Back exits, with the control hint and image count shown at the bottom; both preview entries behave the same.
- Photo manager: thumbnails, batch selection, zoom and batch removal; import from the TV gallery, folders, internal storage or a USB drive through the in-app picker. JPG, PNG, WebP and BMP, up to 24 photos and 32 MB each; imports are private copies, and removal never deletes originals.
- Computer/phone upload: scan the QR code from the same LAN to open the upload page with the session code filled in; the address and six-digit code can also be entered manually, and the QR code is generated on the TV. The receiver runs only while the upload window is foreground and stops as soon as it closes or loses focus.
- Without selected photos, built-in landscapes and a moving clock are used.

### Factory advertising screensaver

The factory ad screensaver is triggered independently by the firmware setting `screen_saver_timeout`; it is unrelated to the standard Android screensaver switch and to this project's local screensaver. The tool backs up before disabling and can restore:

```powershell
python -B tools/screensaver_tv.py status --serial TV_SERIAL --expected-model MODEL
python -B tools/screensaver_tv.py disable --serial TV_SERIAL --expected-model MODEL --backup nor/backups/DEVICE/screensaver.json
python -B tools/screensaver_tv.py restore --serial TV_SERIAL --expected-model MODEL --backup nor/backups/DEVICE/screensaver.json
```

Restoring Home takeover and restoring the factory screensaver are two independent operations; see the [screensaver verification](docs/screensaver-20261024.md).

## Build and install

### Download and install (no build environment needed)

1. Download the latest `quiet-tv.apk` from [Releases](https://github.com/Carton/mitv-quiet-launcher/releases).
2. Install over wireless ADB: enable ADB as described in the [quick-start guide](docs/quick-start.md), run `adb connect TV_IP:PORT`, then `adb -s TV_IP:PORT install -r quiet-tv.apk`. You can also hand the quick-start instruction to an agent, which downloads, installs and takes over Home.
3. Alternatively, copy the APK to a USB drive and open it with the TV's built-in file manager (untested here; depends on firmware).

### Build it yourself

Verified on Windows with JDK 11, SDK 32 and Build Tools 32.0.0; host tools need Python 3.11+. Set `QUIET_TV_JDK`/`JAVA_HOME` to your JDK and `ANDROID_HOME` to your SDK; `TV_SERIAL` and `MODEL` are placeholders.

```powershell
python -B tools/build.py --test
python -B -m unittest discover -s tests -p 'test_*.py' -v
python -B tools/build.py
adb -s TV_SERIAL install -r nor/dist/quiet-tv.apk
adb -s TV_SERIAL shell am start -W -n local.mitv.launcher/.MainActivity
```

- The output is `nor/dist/quiet-tv.apk`; the build uses an existing SDK directly without downloading Gradle and includes an API 23 history check that blocks newer-API references.
- Keep `nor/.local/launcher.keystore` to update in place; a fresh clone creates its own development key. Updates must match the installed signature — uninstalling and reinstalling loses settings and screensaver photos.
- The application ID `local.mitv.launcher` and the APK name `quiet-tv.apk` are fixed and unaffected by the project name.
- An Android Studio Gradle project is included (AGP 7.4.2, Gradle 7.5+); the verified path remains the direct build above.

## Recovery

The bottom “factory launcher” button returns to the factory home; full recovery and takeover revocation from a computer:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File tools/restore_tv.ps1 -Serial TV_SERIAL -ExpectedModel MODEL
```

The script disables the Home bridge through the app's own recovery entry and returns to the factory launcher, removing only this project's accessibility entry and GET_USAGE_STATS grant; factory packages are untouched. Add `-EnableNewLauncher` to just open the launcher, and run `enable_home.py` again to re-enable takeover. A reboot may turn network ADB off — re-enable it in the TV settings.

## Adjust the news sources

The default categories, sources and game filters reflect the author's personal reading preferences; adapt them to your own:

| Category | Current source |
| --- | --- |
| China / World | CCTV News |
| Finance | Wallstreetcn live updates |
| Tech | ScienceNet international research RSS + Science and Technology Daily, interleaved |
| Games | GamerSky single-player news, filtered by title/summary keywords for PC-related content |
| AI | [AIHOT RSS](https://aihot.news/feed.xml) |

Changing sources currently requires editing the code; there is no TV interface for adding arbitrary RSS feeds yet. Main customization points:

- [NewsSources.java](app/src/main/java/local/mitv/launcher/NewsSources.java): categories, publisher labels, endpoints and allowed article domains.
- [DashboardRepository.java](app/src/main/java/local/mitv/launcher/DashboardRepository.java): response parsing, summaries and caching; a different API format needs a corresponding parser, not just a URL swap.
- [GameNews.java](app/src/main/java/local/mitv/launcher/GameNews.java): game-list parsing and keyword filters, which you can modify or remove.
- [ArticleHtml.java](app/src/main/java/local/mitv/launcher/ArticleHtml.java) and [LegacyTls.java](app/src/main/java/local/mitv/launcher/LegacyTls.java): article adaptation and legacy TLS compatibility for fixed hosts; keep certificate and hostname validation.

Rebuild and reinstall after changes, and verify with `tools/check_dashboard.py --serial TV_SERIAL --expected-model MODEL --news` when appropriate. The UI assumes six categories; changing the count also requires checking the tab, data and layout logic.

## Data and open source

- Icons reuse MIT-licensed [Tabler Icons](https://github.com/tabler/tabler-icons) (weather) and [Lucide](https://github.com/lucide-icons/lucide) (settings); original SVGs, licenses and conversion scripts are kept.
- Weather uses the Open-Meteo API with Open-Meteo / GeoNames location search; Android 6 lacks some newer CA roots, so public CAs are added for fixed weather/news hosts only, keeping chain and hostname validation.
- News connects directly to CCTV, Wallstreetcn, ScienceNet, Science and Technology Daily, GamerSky and AIHOT; the category and reader interactions reference NewsNow and Read You without copying their code. News content is not covered by this project's source license.
- The source code is under the [MIT License](LICENSE); see [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md) for third-party and data boundaries.

On-device check tools (ADB enabled on the TV):

```powershell
python -B tools/check_dashboard.py --serial TV_SERIAL --expected-model MODEL --news
python -B tools/check_home.py --serial TV_SERIAL --max-seconds 8
python -B tools/research_news.py   # desktop news-source research, no TV connection
```

`nor/` holds untracked build artifacts, the signing key, factory APK backups and research material; back up `nor/.local/launcher.keystore` and the factory APKs separately. Maintainers run `python -B tools/audit_public.py --history` before publishing; see the [publication checks](docs/publishing.md).

This is an independent community project, not affiliated with Xiaomi.
