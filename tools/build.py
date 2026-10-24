"""Build with the existing Android SDK and Studio JDK, without downloading Gradle."""

import argparse
import os
import pathlib
import subprocess
import zipfile


ROOT = pathlib.Path(__file__).resolve().parents[1]
SDK = pathlib.Path(os.environ.get("ANDROID_HOME", str(pathlib.Path.home() / "AppData/Local/Android/Sdk")))
JDK = pathlib.Path(os.environ.get("QUIET_TV_JDK", r"JDK_HOME"))
TOOLS = SDK / "build-tools/32.0.0"
ANDROID_JAR = SDK / "platforms/android-32/android.jar"


def run(args):
    subprocess.run([str(item) for item in args], check=True, cwd=ROOT)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--test", action="store_true")
    parser.add_argument("--home-tool", action="store_true")
    args = parser.parse_args()
    build = ROOT / "nor/build"
    build.mkdir(exist_ok=True)
    if args.home_tool:
        target = build / "home-tool/classes"
        output = build / "home-tool/dex"
        target.mkdir(parents=True, exist_ok=True)
        output.mkdir(parents=True, exist_ok=True)
        run([JDK / "bin/javac.exe", "-source", "8", "-target", "8", "-Xlint:-options", "-encoding", "UTF-8",
             "-bootclasspath", ANDROID_JAR, "-d", target, ROOT / "tools/android/HomeTool.java"])
        jar = build / "home-tool/classes.jar"
        with zipfile.ZipFile(jar, "w", zipfile.ZIP_DEFLATED) as archive:
            for file in sorted(target.rglob("*.class")):
                archive.write(file, file.relative_to(target).as_posix())
        run([JDK / "bin/java.exe", "-cp", TOOLS / "lib/d8.jar", "com.android.tools.r8.D8",
             "--min-api", "23", "--lib", ANDROID_JAR, "--output", output, jar])
        helper = ROOT / "nor/dist/quiet-home-tool.jar"
        helper.parent.mkdir(exist_ok=True)
        with zipfile.ZipFile(helper, "w", zipfile.ZIP_DEFLATED) as archive:
            archive.write(output / "classes.dex", "classes.dex")
        print("BUILT:", helper)
        return
    if args.test:
        target = build / "test-classes"
        target.mkdir(exist_ok=True)
        run([JDK / "bin/javac.exe", "--release", "8", "-encoding", "UTF-8", "-d", target,
             ROOT / "app/src/main/java/local/mitv/launcher/LauncherPolicy.java",
             ROOT / "app/src/main/java/local/mitv/launcher/ForegroundState.java",
             ROOT / "tests/LauncherPolicyTest.java"])
        run([JDK / "bin/java.exe", "-cp", target, "local.mitv.launcher.LauncherPolicyTest"])
        return
    generated = build / "generated"
    classes = build / "classes"
    dex = build / "dex"
    dist = ROOT / "nor/dist"
    for path in [generated, classes, dex, dist]:
        path.mkdir(exist_ok=True)
    resources = build / "resources.apk"
    run([TOOLS / "aapt.exe", "package", "-f", "-m", "-J", generated,
         "-M", ROOT / "app/src/main/AndroidManifest.xml",
         "-S", ROOT / "app/src/main/res", "-I", ANDROID_JAR, "-F", resources])
    sources = sorted((ROOT / "app/src/main/java").rglob("*.java")) + sorted(generated.rglob("*.java"))
    source_list = build / "sources.txt"
    source_list.write_text("\n".join('"' + str(path).replace('\\', '/') + '"' for path in sources), encoding="utf-8")
    run([JDK / "bin/javac.exe", "-source", "8", "-target", "8", "-Xlint:-options", "-encoding", "UTF-8",
         "-bootclasspath", str(ANDROID_JAR) + os.pathsep + str(TOOLS / "core-lambda-stubs.jar"),
         "-d", classes, "@" + str(source_list)])
    class_jar = build / "classes.jar"
    with zipfile.ZipFile(class_jar, "w", zipfile.ZIP_DEFLATED) as archive:
        for file in sorted(classes.rglob("*.class")):
            archive.write(file, file.relative_to(classes).as_posix())
    run([JDK / "bin/java.exe", "-cp", TOOLS / "lib/d8.jar", "com.android.tools.r8.D8",
         "--min-api", "23", "--lib", ANDROID_JAR, "--output", dex, class_jar])
    unsigned = build / "unsigned.apk"
    unsigned.write_bytes(resources.read_bytes())
    with zipfile.ZipFile(unsigned, "a", zipfile.ZIP_DEFLATED) as archive:
        archive.write(dex / "classes.dex", "classes.dex")
    aligned = build / "aligned.apk"
    run([TOOLS / "zipalign.exe", "-f", "4", unsigned, aligned])
    key = ROOT / "nor/.local/launcher.keystore"
    key.parent.mkdir(exist_ok=True)
    if not key.exists():
        run([JDK / "bin/keytool.exe", "-genkeypair", "-keystore", key, "-alias", "quiet-tv",
             "-storepass", "android", "-keypass", "android", "-keyalg", "RSA", "-keysize", "2048",
             "-validity", "10000", "-dname", "CN=Local Quiet TV", "-noprompt"])
    apk = dist / "quiet-tv.apk"
    signer = [JDK / "bin/java.exe", "-jar", TOOLS / "lib/apksigner.jar"]
    run(signer + ["sign", "--ks", key, "--ks-key-alias", "quiet-tv", "--ks-pass", "pass:android",
                  "--key-pass", "pass:android", "--out", apk, aligned])
    run(signer + ["verify", "--verbose", apk])
    print(f"BUILT: {apk} ({apk.stat().st_size:,} bytes)")


if __name__ == "__main__":
    main()
