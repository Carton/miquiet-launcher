"""Enable this TV's Home bridge, preserving every existing accessibility service."""

import argparse
import datetime
import json
import pathlib
import subprocess
import sys
import time


def main():
    sys.stdout.reconfigure(encoding="utf-8")
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--serial", default="TV_IP:5555")
    args = parser.parse_args()

    def adb(*command):
        result = subprocess.run(["adb", "-s", args.serial, *command], capture_output=True, check=True)
        return result.stdout.decode("utf-8", "replace").strip()

    if adb("shell", "getprop", "ro.product.model") != "MiTV4-ANSM0":
        raise RuntimeError("Device identity mismatch")
    original = adb("shell", "settings", "get", "secure", "enabled_accessibility_services")
    enabled = adb("shell", "settings", "get", "secure", "accessibility_enabled")
    log = pathlib.Path(__file__).resolve().parents[1] / "nor/backups" / "home-activation"
    log.mkdir(parents=True, exist_ok=True)
    (log / (datetime.datetime.now().strftime("%Y%m%d-%H%M%S") + ".json")).write_text(
        json.dumps({"serial": args.serial, "services": original, "accessibility_enabled": enabled}, indent=2), encoding="utf-8")
    result = adb("shell", "am", "broadcast", "-a", "local.mitv.launcher.ENABLE_HOME", "-n", "local.mitv.launcher/.RestoreReceiver")
    if "result=1" not in result:
        raise RuntimeError("Launcher did not confirm activation: " + result)
    adb("shell", "appops", "set", "local.mitv.launcher", "GET_USAGE_STATS", "allow")
    usage_access = adb("shell", "appops", "get", "local.mitv.launcher", "GET_USAGE_STATS")
    if "allow" not in usage_access:
        raise RuntimeError("Foreground fallback access was not enabled: " + usage_access)
    services = [] if original in ("null", "") else original.split(":")
    others = [item for item in services if item and not item.startswith("local.mitv.launcher/")]
    component = "local.mitv.launcher/local.mitv.launcher.HomeBridgeService"
    updated = ":".join(others + [component])
    adb("shell", "settings", "put", "secure", "enabled_accessibility_services", updated)
    adb("shell", "settings", "put", "secure", "accessibility_enabled", "1")
    for _ in range(10):
        state = adb("shell", "dumpsys", "accessibility")
        if "Service[label=简净桌面 Home 接管" in state:
            current = adb("shell", "settings", "get", "secure", "enabled_accessibility_services")
            if not all(item in current.split(":") for item in others):
                raise RuntimeError("An existing accessibility service is missing")
            (log / "accessibility-bound.txt").write_text(state, encoding="utf-8")
            adb("shell", "am", "start", "-n", "local.mitv.launcher/.MainActivity")
            print("Home bridge is bound; preserved", len(others), "existing services")
            return
        time.sleep(.5)
    raise RuntimeError("Service did not bind. Use tools/restore_tv.ps1 to return to the factory launcher.")


if __name__ == "__main__":
    main()
