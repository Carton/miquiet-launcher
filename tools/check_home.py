"""Device regression check for Home takeover and foreground-switch latency."""

import argparse
import pathlib
import subprocess
import time


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--serial", default="TV_IP:5555")
    parser.add_argument("--max-seconds", type=float, default=3.0)
    args = parser.parse_args()

    def adb(*command):
        result = subprocess.run(["adb", "-s", args.serial, *command], capture_output=True, check=True, timeout=20)
        return result.stdout.decode("utf-8", "replace")

    adb("shell", "am", "start", "-W", "-n", "local.mitv.launcher/.MainActivity")
    time.sleep(.5)
    samples = []
    for cycle in range(3):
        started = time.monotonic()
        adb("shell", "input", "keyevent", "3")
        while True:
            state = adb("shell", "dumpsys", "activity", "activities")
            top = next((line for line in state.splitlines() if "mResumedActivity:" in line), "")
            elapsed = time.monotonic() - started
            if "local.mitv.launcher/.MainActivity" in top:
                samples.append(round(elapsed, 2))
                print("HOME", cycle + 1, "settled in", samples[-1], "seconds", flush=True)
                break
            if elapsed > args.max_seconds:
                raise AssertionError(f"Home takeover exceeded {args.max_seconds}s: {top.strip()}")
            time.sleep(.15)
        if elapsed > args.max_seconds:
            raise AssertionError(f"Home takeover took {elapsed:.2f}s")
    output = pathlib.Path(__file__).resolve().parents[1] / "nor/research/home-latency.txt"
    output.parent.mkdir(exist_ok=True)
    output.write_text("seconds=" + str(samples) + "\n", encoding="utf-8")
    print("PASS: 3 Home takeovers within", args.max_seconds, "seconds")


if __name__ == "__main__":
    main()
