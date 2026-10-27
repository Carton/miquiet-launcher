"""Build with the existing Android SDK and Studio JDK, without downloading Gradle."""

import argparse
import os
import pathlib
import shutil
import subprocess
import zipfile
from check_api import check as check_api


ROOT = pathlib.Path(__file__).resolve().parents[1]
SDK = pathlib.Path(os.environ.get("ANDROID_HOME", str(pathlib.Path.home() / "AppData/Local/Android/Sdk")))
def java_home():
    explicit = os.environ.get("QUIET_TV_JDK") or os.environ.get("JAVA_HOME")
    if explicit:
        return pathlib.Path(explicit)
    compiler = shutil.which("javac")
    if compiler:
        return pathlib.Path(compiler).resolve().parent.parent
    studio = pathlib.Path(os.environ.get("ProgramFiles", "")) / "Android/Android Studio"
    for runtime in (studio / "jbr", studio / "jre"):
        if (runtime / "bin/javac.exe").exists():
            return runtime
    raise RuntimeError("Set QUIET_TV_JDK or JAVA_HOME to a JDK with javac (tested with JDK 11)")


JDK = java_home()
TOOLS = SDK / "build-tools/32.0.0"
ANDROID_JAR = SDK / "platforms/android-32/android.jar"
BIN = ".exe" if os.name == "nt" else ""


def run(args):
    subprocess.run([str(item) for item in args], check=True, cwd=ROOT)


def reset_generated(path):
    """Drop stale compiler outputs, only inside this workspace's explicit build directory."""
    intended = (ROOT / "nor/build").absolute()
    resolved = path.resolve()
    if resolved == intended or not resolved.is_relative_to(intended) or path.is_symlink():
        raise RuntimeError(f"Unexpected generated output path: {resolved}")
    if path.exists():
        shutil.rmtree(path)
    path.mkdir(parents=True)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--test", action="store_true")
    parser.add_argument("--home-tool", action="store_true")
    parser.add_argument("--data-test", action="store_true", help="Build Android data/TLS test helper after building the APK")
    parser.add_argument("--saver-test", action="store_true", help="Build Android local-image test helper after building the APK")
    args = parser.parse_args()
    build = ROOT / "nor/build"
    build.mkdir(parents=True, exist_ok=True)
    if args.home_tool or args.data_test or args.saver_test:
        folder = build / ("saver-test" if args.saver_test else "data-test" if args.data_test else "home-tool")
        target = folder / "classes"
        output = folder / "dex"
        reset_generated(target)
        reset_generated(output)
        source = ROOT / ("tests/SaverDataTest.java" if args.saver_test else "tests/DashboardDataTest.java" if args.data_test else "tools/android/HomeTool.java")
        run([JDK / ("bin/javac" + BIN), "-source", "8", "-target", "8", "-Xlint:-options", "-encoding", "UTF-8",
             "-bootclasspath", ANDROID_JAR, "-classpath", build / "classes", "-d", target, source])
        jar = folder / "classes.jar"
        with zipfile.ZipFile(jar, "w", zipfile.ZIP_DEFLATED) as archive:
            for file in sorted(target.rglob("*.class")):
                archive.write(file, file.relative_to(target).as_posix())
        classpath = ["--classpath", build / "classes.jar"] if args.data_test or args.saver_test else []
        run([JDK / ("bin/java" + BIN), "-cp", TOOLS / "lib/d8.jar", "com.android.tools.r8.D8",
             "--min-api", "23", "--lib", ANDROID_JAR, *classpath, "--output", output, jar])
        helper = ROOT / "nor/dist" / ("quiet-saver-tests.jar" if args.saver_test else "quiet-data-tests.jar" if args.data_test else "quiet-home-tool.jar")
        helper.parent.mkdir(exist_ok=True)
        with zipfile.ZipFile(helper, "w", zipfile.ZIP_DEFLATED) as archive:
            archive.write(output / "classes.dex", "classes.dex")
        print("BUILT:", helper)
        return
    if args.test:
        target = build / "test-classes"
        reset_generated(target)
        run([JDK / ("bin/javac" + BIN), "--release", "8", "-encoding", "UTF-8", "-d", target,
             ROOT / "app/src/main/java/local/mitv/launcher/LauncherPolicy.java",
             ROOT / "app/src/main/java/local/mitv/launcher/ForegroundState.java",
             ROOT / "app/src/main/java/local/mitv/launcher/DashboardPolicy.java",
             ROOT / "app/src/main/java/local/mitv/launcher/SaverPolicy.java",
             ROOT / "tests/SaverPolicyTest.java",
             ROOT / "app/src/main/java/local/mitv/launcher/NewsReaderPolicy.java",
             ROOT / "app/src/main/java/local/mitv/launcher/NewsSources.java",
             ROOT / "app/src/main/java/local/mitv/launcher/ScienceNews.java",
             ROOT / "tests/ScienceNewsTest.java",
             ROOT / "app/src/main/java/local/mitv/launcher/SettingsMenu.java",
             ROOT / "tests/SettingsMenuTest.java",
             ROOT / "app/src/main/java/local/mitv/launcher/GameNews.java",
             ROOT / "app/src/main/java/local/mitv/launcher/RssFeed.java",
             ROOT / "app/src/main/java/local/mitv/launcher/ArticleHtml.java",
             ROOT / "app/src/main/java/local/mitv/launcher/SaverBrowser.java",
             ROOT / "app/src/main/java/local/mitv/launcher/PhotoUploadServer.java",
             ROOT / "tests/RssFeedTest.java",
             ROOT / "tests/GameNewsTest.java",
             ROOT / "tests/ArticleHtmlTest.java",
             ROOT / "tests/SaverBrowserTest.java",
             ROOT / "tests/PhotoUploadServerTest.java",
             ROOT / "tests/NewsReaderPolicyTest.java",
             ROOT / "tests/DashboardPolicyTest.java",
             ROOT / "tests/LauncherPolicyTest.java"])
        run([JDK / ("bin/java" + BIN), "-cp", target, "local.mitv.launcher.LauncherPolicyTest"])
        run([JDK / ("bin/java" + BIN), "-cp", target, "local.mitv.launcher.DashboardPolicyTest"])
        run([JDK / ("bin/java" + BIN), "-cp", target, "local.mitv.launcher.SaverPolicyTest"])
        run([JDK / ("bin/java" + BIN), "-cp", target, "local.mitv.launcher.NewsReaderPolicyTest"])
        run([JDK / ("bin/java" + BIN), "-cp", target, "local.mitv.launcher.RssFeedTest"])
        run([JDK / ("bin/java" + BIN), "-cp", target, "local.mitv.launcher.ScienceNewsTest"])
        run([JDK / ("bin/java" + BIN), "-cp", target, "local.mitv.launcher.GameNewsTest"])
        run([JDK / ("bin/java" + BIN), "-cp", target, "local.mitv.launcher.SettingsMenuTest"])
        run([JDK / ("bin/java" + BIN), "-cp", target, "local.mitv.launcher.ArticleHtmlTest"])
        run([JDK / ("bin/java" + BIN), "-cp", target, "local.mitv.launcher.SaverBrowserTest"])
        run([JDK / ("bin/java" + BIN), "-cp", target, "local.mitv.launcher.PhotoUploadServerTest"])
        return
    generated = build / "generated"
    classes = build / "classes"
    dex = build / "dex"
    dist = ROOT / "nor/dist"
    for path in [generated, classes, dex]:
        reset_generated(path)
    dist.mkdir(exist_ok=True)
    resources = build / "resources.apk"
    run([TOOLS / ("aapt" + BIN), "package", "-f", "-m", "-J", generated,
         "-M", ROOT / "app/src/main/AndroidManifest.xml",
         "-S", ROOT / "app/src/main/res", "-I", ANDROID_JAR, "-F", resources])
    sources = sorted((ROOT / "app/src/main/java").rglob("*.java")) + sorted(generated.rglob("*.java"))
    source_list = build / "sources.txt"
    source_list.write_text("\n".join('"' + str(path).replace('\\', '/') + '"' for path in sources), encoding="utf-8")
    run([JDK / ("bin/javac" + BIN), "-source", "8", "-target", "8", "-Xlint:-options", "-encoding", "UTF-8",
         "-bootclasspath", str(ANDROID_JAR) + os.pathsep + str(TOOLS / "core-lambda-stubs.jar"),
         "-d", classes, "@" + str(source_list)])
    check_api(classes)
    class_jar = build / "classes.jar"
    with zipfile.ZipFile(class_jar, "w", zipfile.ZIP_DEFLATED) as archive:
        for file in sorted(classes.rglob("*.class")):
            archive.write(file, file.relative_to(classes).as_posix())
    run([JDK / ("bin/java" + BIN), "-cp", TOOLS / "lib/d8.jar", "com.android.tools.r8.D8",
         "--min-api", "23", "--lib", ANDROID_JAR, "--output", dex, class_jar])
    unsigned = build / "unsigned.apk"
    unsigned.write_bytes(resources.read_bytes())
    with zipfile.ZipFile(unsigned, "a", zipfile.ZIP_DEFLATED) as archive:
        archive.write(dex / "classes.dex", "classes.dex")
    aligned = build / "aligned.apk"
    run([TOOLS / ("zipalign" + BIN), "-f", "4", unsigned, aligned])
    key = ROOT / "nor/.local/launcher.keystore"
    key.parent.mkdir(exist_ok=True)
    if not key.exists():
        run([JDK / ("bin/keytool" + BIN), "-genkeypair", "-keystore", key, "-alias", "quiet-tv",
             "-storepass", "android", "-keypass", "android", "-keyalg", "RSA", "-keysize", "2048",
             "-validity", "10000", "-dname", "CN=Local Quiet TV", "-noprompt"])
    apk = dist / "quiet-tv.apk"
    signer = [JDK / ("bin/java" + BIN), "-jar", TOOLS / "lib/apksigner.jar"]
    run(signer + ["sign", "--ks", key, "--ks-key-alias", "quiet-tv", "--ks-pass", "pass:android",
                  "--key-pass", "pass:android", "--out", apk, aligned])
    run(signer + ["verify", "--verbose", apk])
    print(f"BUILT: {apk} ({apk.stat().st_size:,} bytes)")


if __name__ == "__main__":
    main()
