"""Bump the app version, commit, tag and push; CI then builds and publishes the Release.

Usage: python -B tools/release.py 0.8.5 [--dry-run]
"""

import argparse
import pathlib
import re
import subprocess
import sys

ROOT = pathlib.Path(__file__).resolve().parents[1]
MANIFEST = ROOT / "app/src/main/AndroidManifest.xml"


def version_bump(manifest_text, new_version):
    """Return (updated_text, new_version_code); raise ValueError on malformed input."""
    if not re.fullmatch(r"\d+\.\d+\.\d+", new_version):
        raise ValueError(f"版本号需为 x.y.z 形式，收到: {new_version}")
    code = re.search(r'android:versionCode="(\d+)"', manifest_text)
    name = re.search(r'android:versionName="([^"]+)"', manifest_text)
    if not code or not name:
        raise ValueError("AndroidManifest.xml 中未找到 versionCode/versionName")
    new_code = int(code.group(1)) + 1
    text = manifest_text.replace(code.group(0), f'android:versionCode="{new_code}"', 1)
    text = text.replace(name.group(0), f'android:versionName="{new_version}"', 1)
    return text, new_code


def sh(*args, check=True):
    return subprocess.run([str(item) for item in args], check=check, capture_output=True,
                          text=True, encoding="utf-8", cwd=ROOT)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("version", help="新版本号，如 0.8.5；对应 tag v0.8.5")
    parser.add_argument("--dry-run", action="store_true", help="只显示将要做的修改，不改文件、不提交")
    args = parser.parse_args()

    try:
        text, code = version_bump(MANIFEST.read_text(encoding="utf-8"), args.version)
    except ValueError as error:
        sys.exit(str(error))
    print(f"versionName -> {args.version}, versionCode -> {code}, tag v{args.version}")
    if args.dry_run:
        print("dry-run：不修改文件")
        return

    if sh("git", "status", "--porcelain").stdout.strip():
        sys.exit("工作区不干净，先处理其他修改")
    branch = sh("git", "rev-parse", "--abbrev-ref", "HEAD").stdout.strip()
    if branch != "master":
        sys.exit(f"当前分支是 {branch}，请在 master 上发布")
    sh("git", "fetch", "-q", "origin", "master")
    if sh("git", "rev-parse", "master").stdout.strip() != sh("git", "rev-parse", "origin/master").stdout.strip():
        sys.exit("master 与 origin/master 不一致，先同步")
    tag = f"v{args.version}"
    if sh("git", "rev-parse", "-q", "--verify", f"refs/tags/{tag}", check=False).returncode == 0:
        sys.exit(f"tag {tag} 已存在")

    MANIFEST.write_text(text, encoding="utf-8")
    sh("git", "add", MANIFEST)
    sh("git", "commit", "-m", f"chore: bump version to {args.version}")
    sh("git", "push", "origin", "master")
    sh("git", "tag", tag)
    sh("git", "push", "origin", tag)
    print(f"已推送 {tag}，GitHub Actions 将自动构建并创建 Release，进度见:")
    print("https://github.com/Carton/mitv-quiet-launcher/actions")


if __name__ == "__main__":
    main()
