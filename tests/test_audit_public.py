"""PNG pixel bytes are not text; compressed metadata and malformed files are."""

from pathlib import Path
import struct
import sys
import unittest
import zlib

sys.path.insert(0, str(Path(__file__).resolve().parents[1] / "tools"))
from audit_public import findings


def chunk(kind, data):
    return struct.pack(">I", len(data)) + kind + data + struct.pack(">I", zlib.crc32(kind + data))


def png(*chunks):
    return (b"\x89PNG\r\n\x1a\n" + chunk(b"IHDR", struct.pack(">IIBBBBB", 1, 1, 8, 2, 0, 0, 0))
            + b"".join(chunks) + chunk(b"IEND", b""))


class PublicAuditTests(unittest.TestCase):
    path = b"C" + b":/private-machine/file"

    def test_pixel_bytes_are_not_text(self):
        self.assertEqual(findings(png(chunk(b"IDAT", self.path))), [])

    def test_plain_and_compressed_metadata_are_scanned(self):
        for kind, payload in [(b"tEXt", b"Comment\0" + self.path),
                              (b"zTXt", b"Comment\0\0" + zlib.compress(self.path)),
                              (b"iTXt", b"Comment\0\1\0\0\0" + zlib.compress(self.path)),
                              (b"eXIf", self.path), (b"abcd", self.path)]:
            with self.subTest(kind=kind):
                self.assertIn("windows_absolute_path", findings(png(chunk(kind, payload))))

    def test_invalid_crc_and_trailing_bytes_are_scanned(self):
        data = png(chunk(b"IDAT", b"\n" + self.path))
        self.assertIn("windows_absolute_path", findings(data + self.path))
        self.assertIn("windows_absolute_path", findings(data.replace(b"IEND", b"oops")))
        self.assertIn("windows_absolute_path", findings(self.path))


if __name__ == "__main__":
    unittest.main()
