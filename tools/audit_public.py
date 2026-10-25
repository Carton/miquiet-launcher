"""Scan tracked files and optionally every local Git object for private machine data."""

import argparse
import io
import json
from pathlib import Path
import re
import subprocess

ROOT = Path(__file__).resolve().parents[1]
RULES = {
    "private_ipv4": re.compile(rb"\b(?:192\.168\.\d{1,3}\.\d{1,3}|10\.\d{1,3}\.\d{1,3}\.\d{1,3}|172\.(?:1[6-9]|2\d|3[01])\.\d{1,3}\.\d{1,3})\b"),
    "windows_absolute_path": re.compile(rb"(?<![A-Za-z0-9])[A-Za-z]:[\\/]"),
    "unix_user_path": re.compile(rb"/(?:Users|home)/[A-Za-z0-9_.-]+/"),
    "private_key": re.compile(rb"-----BEGIN (?:RSA |EC |OPENSSH |ENCRYPTED )?PRIVATE KEY-----"),
    "credential_pattern": re.compile(rb"(?:gh[pousr]_[A-Za-z0-9]{30,}|github_pat_[A-Za-z0-9_]{30,}|AKIA[0-9A-Z]{16}|sk-[A-Za-z0-9_-]{32,})"),
}


def findings(data):
    return [label for label, expression in RULES.items() if expression.search(data)]


def private_file(name):
    path = Path(name)
    return (name.startswith(("nor/", ".git/", ".playwright-mcp/"))
            or path.name == ".env" or (path.name.startswith(".env.") and path.name != ".env.example")
            or path.suffix.lower() in {".keystore", ".jks", ".p12", ".pfx", ".apk", ".apks", ".aab", ".bundle", ".log"})


def git(*args):
    return subprocess.check_output(["git", *args], cwd=ROOT)


def scan(history=False):
    issues = []
    names = [name for name in git("ls-files", "-z").decode("utf-8").split("\0") if name]
    for name in names:
        if private_file(name):
            issues.append({"location": name, "rules": ["private_file"]})
        path = ROOT / name
        if path.is_file():
            labels = findings(path.read_bytes())
            if labels:
                issues.append({"location": name, "rules": labels})
    objects = 0
    if history:
        # Include unreachable objects too: changing HEAD alone does not remove private history.
        ids = git("cat-file", "--batch-all-objects", "--batch-check=%(objectname)")
        batch = subprocess.run(["git", "cat-file", "--batch"], input=ids, capture_output=True, check=True, cwd=ROOT)
        stream = io.BytesIO(batch.stdout)
        while True:
            header = stream.readline()
            if not header:
                break
            oid, kind, size = header.strip().split()
            data = stream.read(int(size))
            if stream.read(1) != b"\n":
                raise ValueError("Unexpected git object stream")
            objects += 1
            if kind in (b"blob", b"commit", b"tag"):
                labels = findings(data)
                if labels:
                    issues.append({"location": "git-object:" + oid.decode("ascii"), "rules": labels})
    return {"tracked_files": len(names), "git_objects": objects, "issues": issues,
            "scope": "heuristic machine-data/key scan; author names/emails deliberately retained"}


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--history", action="store_true", help="Include reachable and unreachable local Git objects")
    parser.add_argument("--report", type=Path, help="Optional private JSON output under nor/")
    args = parser.parse_args()
    report = scan(args.history)
    if args.report:
        destination = args.report.resolve()
        if not destination.is_relative_to((ROOT / "nor").resolve()):
            raise ValueError("Keep private audit reports inside nor/")
        destination.parent.mkdir(parents=True, exist_ok=True)
        destination.write_text(json.dumps(report, indent=2) + "\n", encoding="utf-8")
    print("Scanned", report["tracked_files"], "tracked files and", report["git_objects"], "Git objects")
    for issue in report["issues"]:
        print(issue["location"], ":", ", ".join(issue["rules"]))
    print("FAIL" if report["issues"] else "PASS", "- privacy pattern scan (does not prove absence of every kind of personal information)")
    raise SystemExit(bool(report["issues"]))


if __name__ == "__main__":
    main()
