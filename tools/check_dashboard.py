"""Exercise the built APK's parsers and TLS validation on the explicitly selected TV."""

import argparse
import pathlib
import subprocess
import sys

ROOT = pathlib.Path(__file__).resolve().parents[1]


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--serial", required=True, help="Explicit adb device serial or TV_IP:PORT")
    parser.add_argument("--expected-model", default="MiTV4-ANSM0", help="Model confirmed during device inspection")
    mode = parser.add_mutually_exclusive_group()
    mode.add_argument("--news", action="store_true", help="Check all six live categories on the TV instead of the weather TLS suite")
    mode.add_argument("--science", action="store_true", help="Check both science publishers and their article bodies on the TV")
    parser.add_argument("--english", action="store_true", help="Check the English-edition foreign sources instead of the Chinese ones")
    parser.add_argument("--proxy", default=None, help="Optional host:port HTTP proxy the TV/emulator test traffic should use (emulator: the built-in host alias plus your local proxy port)")
    args = parser.parse_args()

    def adb(*command):
        result = subprocess.run(["adb", "-s", args.serial, *map(str, command)],
                                capture_output=True, check=True, timeout=120)
        return result.stdout.decode("utf-8", "replace")

    if adb("shell", "getprop", "ro.product.model").strip() != args.expected_model:
        raise RuntimeError("Device identity mismatch")
    subprocess.run([sys.executable, "-B", ROOT / "tools/build.py", "--data-test"], check=True)
    # Package Installer removes its input APK after install; use a distinct test copy.
    adb("push", ROOT / "nor/dist/quiet-tv.apk", "/data/local/tmp/quiet-data-app.apk")
    for path in [ROOT / "nor/dist/quiet-data-tests.jar", ROOT / "app/src/main/res/raw/isrg_root_x1.pem",
                 ROOT / "app/src/main/res/raw/gts_root_r4.pem",
                 ROOT / "app/src/main/res/raw/digicert_global_root_g3.pem",
                 ROOT / "app/src/main/res/raw/usertrust_rsa_root.pem",
                 ROOT / "app/src/main/res/raw/starfield_root_g2.pem"]:
        adb("push", path, "/data/local/tmp/")
    flag = (" --science-en" if args.english else " --science") if args.science else \
           (" --news-en" if args.english else " --news") if args.news else ""
    if args.proxy:
        flag += " --proxy=" + args.proxy
    output = adb("shell", "CLASSPATH=/data/local/tmp/quiet-data-tests.jar:/data/local/tmp/quiet-data-app.apk "
                 "app_process /system/bin local.mitv.launcher.DashboardDataTest "
                 "/data/local/tmp/isrg_root_x1.pem /data/local/tmp/gts_root_r4.pem"
                 " /data/local/tmp/digicert_global_root_g3.pem /data/local/tmp/usertrust_rsa_root.pem"
                 " /data/local/tmp/starfield_root_g2.pem" + flag)
    destination = ROOT / ("nor/research/en-news/english-news-device-tests.txt" if args.english else
                          "nor/research/science-sources-081/device-tests.txt" if args.science else
                          "nor/research/update-070/news-device-tests.txt" if args.news else "nor/research/dashboard/data-tests.txt")
    destination.parent.mkdir(parents=True, exist_ok=True)
    destination.write_text(output, encoding="utf-8")
    print(output)
    expected = ["PASS: 39 Android data assertions"]
    if args.news:
        expected += ["NEWS LIVE: " + category + " count=" for category in ["china","world","finance","tech","game","ai"]]
    elif not args.science:
        expected += ["TLS VALID: " + host for host in ["ipwho.is", "api.open-meteo.com", "geocoding-api.open-meteo.com"]]
        expected += ["TLS REJECTED: " + host for host in ["self-signed.badssl.com", "wrong.host.badssl.com", "expired.badssl.com"]]
    if args.news or args.science:
        publishers = ["Phys.org", "ScienceDaily"] if args.english else ["科学网", "科技日报"]
        expected += ["SCIENCE LIVE: " + source + " count=" for source in publishers]
        # Phys.org article pages reject the app user agent, so the English reader check covers ScienceDaily only.
        expected += ["SCIENCE ARTICLE: " + source + " chars=" for source in publishers[1:]]
    if not all(marker in output for marker in expected):
        raise AssertionError("Incomplete device test; inspect " + str(destination))


if __name__ == "__main__":
    main()
