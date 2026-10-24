"""Export selected APKs without modifying packages; verify bytes against the TV."""

import argparse
import concurrent.futures
import datetime
import hashlib
import json
import pathlib
import re
import subprocess
import threading
import time
import zipfile


CORE = [
    "com.mitv.tvhome", "com.xiaomi.mitv.settings",
    "com.xiaomi.mitv.tvplayer", "com.droidlogic.tvinput", "mitv.service",
    "com.xiaomi.mitv.providers.settings", "com.xiaomi.mitv.systemui",
    "com.android.systemui", "com.xiaomi.mitv.mediaexplorer",
    "com.xiaomi.mitv.appstore", "com.duokan.airkan.tvbox",
    "com.xiaomi.upnp", "com.xiaomi.miplay", "com.xiaomi.mitv.tvmanager",
]
FAVORITES = [
    "com.xiaodianshi.tv.yst", "com.cibn.tv", "com.huya.nftv",
    "com.dangbeimarket", "com.gitvdemo.video",
]


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--serial", default="TV_IP:5555")
    parser.add_argument("--output", default="nor/backups/2026-10-22")
    parser.add_argument("--workers", type=int, default=2)
    args = parser.parse_args()
    root = pathlib.Path(args.output).resolve()
    (root / "apks").mkdir(parents=True, exist_ok=True)
    (root / "metadata").mkdir(exist_ok=True)
    lock = threading.Lock()
    verified = {}
    failures = {}

    def adb(*command, timeout=90):
        result = subprocess.run(
            ["adb", "-s", args.serial, *command],
            capture_output=True, timeout=timeout, check=True,
        )
        return result.stdout.decode("utf-8", "replace").strip()

    def save_manifest():
        payload = {
            "device": args.serial,
            "updated": datetime.datetime.now().isoformat(),
            "verification": "remote SHA-256, local SHA-256, size and ZIP CRC",
            "apks": list(verified.values()), "failures": dict(failures),
        }
        temporary = root / "manifest.json.tmp"
        temporary.write_text(json.dumps(payload, indent=2), encoding="utf-8")
        temporary.replace(root / "manifest.json")

    def export(item):
        package, remote, index = item
        name = package + ("" if index == 0 else f"-{index}")
        local = root / "apks" / (name + ".apk")
        partial = local.with_suffix(".apk.part")
        try:
            size = int(adb("shell", "stat", "-c", "%s", remote))
            remote_hash = adb("shell", "sha256sum", remote).split()[0]
            if not re.fullmatch(r"[a-fA-F0-9]{64}", remote_hash):
                raise ValueError("Invalid remote SHA-256")
            valid_existing = (
                local.exists() and local.stat().st_size == size
                and hashlib.file_digest(local.open("rb"), "sha256").hexdigest() == remote_hash
            )
            if not valid_existing:
                print(f"COPY {name} ({size / 1048576:.1f} MiB)", flush=True)
                with (root / "metadata" / (name + "-pull.log")).open("wb") as log:
                    proc = subprocess.Popen(
                        ["adb", "-s", args.serial, "pull", remote, str(partial)],
                        stdout=log, stderr=subprocess.STDOUT,
                    )
                    deadline = time.monotonic() + 1800
                    last_progress = time.monotonic()
                    while proc.poll() is None:
                        if time.monotonic() > deadline:
                            proc.kill()
                            proc.wait()
                            raise TimeoutError("APK transfer exceeded 30 minutes")
                        if time.monotonic() - last_progress >= 20:
                            transferred = partial.stat().st_size if partial.exists() else 0
                            print(f"PROGRESS {name} {transferred / 1048576:.1f}/{size / 1048576:.1f} MiB", flush=True)
                            last_progress = time.monotonic()
                        time.sleep(1)
                    if proc.returncode:
                        raise RuntimeError(f"adb pull exited {proc.returncode}")
                if partial.stat().st_size != size:
                    raise ValueError("Size mismatch")
                with partial.open("rb") as stream:
                    local_hash = hashlib.file_digest(stream, "sha256").hexdigest()
                if local_hash != remote_hash:
                    raise ValueError("Remote/local SHA-256 mismatch")
                with zipfile.ZipFile(partial) as archive:
                    if archive.testzip() is not None:
                        raise ValueError("ZIP CRC verification failed")
                partial.replace(local)
            else:
                with zipfile.ZipFile(local) as archive:
                    if archive.testzip() is not None:
                        raise ValueError("Existing APK ZIP CRC failed")
            with lock:
                verified[name] = {
                    "package": package, "remote": remote,
                    "file": local.relative_to(root).as_posix(),
                    "bytes": size, "sha256": remote_hash,
                    "remote_sha256": remote_hash, "zip_crc": "passed",
                }
                save_manifest()
                print(f"VERIFIED {name}", flush=True)
        except Exception as exc:
            with lock:
                failures[name] = str(exc)
                save_manifest()
                print(f"FAILED {name}: {exc}", flush=True)

    packages = adb("shell", "pm", "list", "packages", "-f")
    (root / "metadata" / "packages.txt").write_text(packages, encoding="utf-8")
    paths = {}
    for line in packages.splitlines():
        if line.startswith("package:"):
            remote, package = line[8:].rsplit("=", 1)
            paths[package] = remote
    items = [(pkg, paths[pkg], 0) for pkg in CORE + FAVORITES if pkg in paths]
    factory = "/system/vendor/app/TvHome/TvHome.apk"
    if adb("shell", "ls", factory).startswith(factory):
        items.insert(1, ("com.mitv.tvhome.factory", factory, 0))
    print(f"Backing up {len(items)} APKs; {args.workers} transfers", flush=True)
    with concurrent.futures.ThreadPoolExecutor(max_workers=args.workers) as pool:
        list(pool.map(export, items))
    print(f"COMPLETE: {len(verified)} verified; {len(failures)} failed", flush=True)
    raise SystemExit(1 if failures else 0)


if __name__ == "__main__":
    main()
