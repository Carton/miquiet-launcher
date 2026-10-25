"""Vendor the minimal MIT QR encoder, pinned to Nayuki v1.8.0; no build-time download."""

import concurrent.futures
import hashlib
import json
from pathlib import Path
import urllib.request

ROOT = Path(__file__).resolve().parents[1]
BASE = "https://raw.githubusercontent.com/nayuki/QR-Code-generator/v1.8.0/"
SOURCES = ["QrCode.java", "QrSegment.java", "BitBuffer.java", "DataTooLongException.java"]


def main():
    destination = ROOT / "app/src/main/java/io/nayuki/qrcodegen"
    notices = ROOT / "third_party/qrcodegen"
    destination.mkdir(parents=True, exist_ok=True)
    notices.mkdir(parents=True, exist_ok=True)

    def fetch(name):
        path = "Readme.markdown" if name == "Readme.markdown" else "java/src/main/java/io/nayuki/qrcodegen/" + name
        return name, path, urllib.request.urlopen(BASE + path, timeout=20).read()

    records = {}
    with concurrent.futures.ThreadPoolExecutor(max_workers=5) as pool:
        for name, path, data in pool.map(fetch, [*SOURCES, "Readme.markdown"]):
            # Normalize whitespace only; retain upstream copyright and full MIT headers.
            text = "\n".join(line.rstrip() for line in data.decode("utf-8").splitlines()) + "\n"
            target = notices / name if name == "Readme.markdown" else destination / name
            target.write_text(text, encoding="utf-8", newline="\n")
            records[name] = {"upstream_path": path, "upstream_sha256": hashlib.sha256(data).hexdigest(),
                             "bundled_sha256": hashlib.sha256(target.read_bytes()).hexdigest()}
            if name == "Readme.markdown":
                license_text = text.split("License\n-------\n", 1)[1].lstrip()
                for license_path in [notices / "LICENSE", ROOT / "app/src/main/res/raw/qrcodegen_license.txt"]:
                    license_path.write_text(license_text, encoding="utf-8", newline="\n")
    (notices / "manifest.json").write_text(json.dumps({"source": BASE,
        "changes": "Whitespace normalization only; no encoder logic changes", "files": records}, indent=2) + "\n",
        encoding="utf-8", newline="\n")
    print("Imported four QR encoder classes and full MIT notices")


if __name__ == "__main__":
    main()
