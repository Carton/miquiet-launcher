"""Disable or restore this Xiaomi firmware's advertising screensaver timer, with readback."""

import argparse
import json
from pathlib import Path
import subprocess

ROOT = Path(__file__).resolve().parents[1]


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("mode", choices=["status", "disable", "restore"])
    parser.add_argument("--serial", required=True, help="Explicit adb device serial or TV_IP:PORT")
    parser.add_argument("--expected-model", default="MiTV4-ANSM0", help="Model confirmed during device inspection")
    parser.add_argument("--backup", type=Path, help="Per-device settings backup; required for disable/restore")
    args = parser.parse_args()

    def adb(*command):
        return subprocess.check_output(["adb", "-s", args.serial, *command], timeout=20).decode("utf-8", "replace").strip()

    if args.mode != "status" and args.backup is None:
        parser.error("--backup is required for disable/restore")
    if adb("shell", "getprop", "ro.product.model") != args.expected_model:
        raise RuntimeError("Device identity mismatch")
    current = adb("shell", "settings", "get", "system", "screen_saver_timeout")
    if args.mode == "status":
        print("Original screensaver timeout:", current, "(negative other than -1 means disabled)")
        return
    if args.mode == "disable":
        if current == "null":
            raise RuntimeError("This firmware has no such timer; inspect its screensaver implementation first")
        if not args.backup.exists():
            if current == "-2":
                raise RuntimeError("Already disabled, but no original backup exists; cannot invent a restore value")
            args.backup.parent.mkdir(parents=True, exist_ok=True)
            args.backup.write_text(json.dumps({"device": args.serial, "model": args.expected_model,
                "system:screen_saver_timeout": current}, indent=2) + "\n", encoding="utf-8")
        value = "-2"
    else:
        value = json.loads(args.backup.read_text(encoding="utf-8"))["system:screen_saver_timeout"]
        if value != "null":
            value = str(int(value))
    saved = json.loads(args.backup.read_text(encoding="utf-8"))
    if saved.get("device", args.serial) != args.serial or saved.get("model", args.expected_model) != args.expected_model:
        raise RuntimeError("Backup belongs to another device/model")
    if value == "null":
        adb("shell", "settings", "delete", "system", "screen_saver_timeout")
    else:
        adb("shell", "settings", "put", "system", "screen_saver_timeout", value)
    result = adb("shell", "settings", "get", "system", "screen_saver_timeout")
    if result != value:
        raise RuntimeError("Setting readback mismatch: " + result)
    print("VERIFIED:", args.mode, "screen_saver_timeout=" + result, "backup=" + str(args.backup))


if __name__ == "__main__":
    main()
