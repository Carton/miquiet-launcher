"""Release helper version bumping."""

from pathlib import Path
import sys
import unittest

sys.path.insert(0, str(Path(__file__).resolve().parents[1] / "tools"))
from release import gradle_version, version_bump


class VersionBumpTest(unittest.TestCase):
    def test_bumps_code_and_name(self):
        text = '<manifest package="local.mitv.launcher" android:versionCode="18" android:versionName="0.8.4">'
        new, code = version_bump(text, "0.8.5")
        self.assertEqual(code, 19)
        self.assertIn('android:versionCode="19"', new)
        self.assertIn('android:versionName="0.8.5"', new)
        self.assertNotIn("0.8.4", new)

    def test_rejects_bad_version(self):
        text = 'android:versionCode="1" android:versionName="0.1.0"'
        for bad in ("0.9", "abc", "0.8.5-beta", ""):
            with self.assertRaises(ValueError, msg=bad):
                version_bump(text, bad)

    def test_reuses_prepared_version_without_an_empty_commit(self):
        text = '<manifest android:versionCode="21" android:versionName="0.8.7">'
        updated, code = version_bump(text, "0.8.7")
        self.assertEqual(updated, text)
        self.assertEqual(code, 21)

    def test_synchronizes_stale_gradle_metadata(self):
        text = "defaultConfig {\n        versionCode 18\n        versionName '0.8.4'\n}\n"
        updated = gradle_version(text, "0.8.7", 21)
        self.assertEqual(updated, "defaultConfig {\n        versionCode 21\n        versionName '0.8.7'\n}\n")
        self.assertEqual(gradle_version(updated, "0.8.7", 21), updated)

    def test_preserves_gradle_quoting(self):
        text = 'versionCode 20\nversionName "0.8.6"\n'
        self.assertEqual(gradle_version(text, "0.8.7", 21), 'versionCode 21\nversionName "0.8.7"\n')

    def test_rejects_missing_or_duplicate_gradle_metadata(self):
        for text in ("", "versionCode 20\n", "versionCode 20\nversionCode 21\nversionName '0.8.7'\n"):
            with self.assertRaises(ValueError):
                gradle_version(text, "0.8.7", 21)

    def test_rejects_manifest_without_version(self):
        with self.assertRaises(ValueError):
            version_bump("<manifest></manifest>", "1.2.3")

    def test_real_manifest(self):
        path = Path(__file__).resolve().parents[1] / "app/src/main/AndroidManifest.xml"
        new, code = version_bump(path.read_text(encoding="utf-8"), "9.9.9")
        self.assertIn('android:versionName="9.9.9"', new)
        self.assertGreater(code, 0)


if __name__ == "__main__":
    unittest.main()
