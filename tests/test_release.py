"""Release helper version bumping."""

from pathlib import Path
import sys
import unittest

sys.path.insert(0, str(Path(__file__).resolve().parents[1] / "tools"))
from release import version_bump


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
