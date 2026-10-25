"""Cover SVG forms that produced missing strokes on the Android-6 TV."""

from pathlib import Path
import sys
import unittest

sys.path.insert(0,str(Path(__file__).resolve().parents[1]/"tools"))
from import_settings_icons import explicit_moves


class SettingsIconTests(unittest.TestCase):
    def test_cross_segments_get_explicit_line_commands(self):
        self.assertEqual(explicit_moves("M18 6 6 18"),"M18,6 L6 18")
        self.assertEqual(explicit_moves("m6 6 12 12"),"m6,6 l12 12")

    def test_check_and_compact_gear_spokes_keep_relative_geometry(self):
        self.assertEqual(explicit_moves("m9 12 2 2 4-4"),"m9,12 l2 2 4 -4")
        self.assertEqual(explicit_moves("m14.305 7.53.923-.382"),"m14.305,7.53 l.923 -.382")

    def test_existing_curves_and_subpaths_are_preserved(self):
        self.assertEqual(explicit_moves("M12 17v4 M8 21h8"),"M12,17v4 M8,21h8")
        self.assertEqual(explicit_moves("M2 3c1 2 3 4 5 6z"),"M2,3c1 2 3 4 5 6z")


if __name__=="__main__":
    unittest.main()
