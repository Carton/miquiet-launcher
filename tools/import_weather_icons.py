"""Reproduce the small Android vector subset of Tabler Icons v3.31.0 (MIT)."""

import hashlib
import copy
import json
from pathlib import Path
import urllib.request
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[1]
BASE = "https://raw.githubusercontent.com/tabler/tabler-icons/v3.31.0/"
NAMES = ["sun", "moon", "cloud", "mist",
         "cloud-rain", "cloud-snow", "cloud-storm", "cloud-question"]
ANDROID = "http://schemas.android.com/apk/res/android"


def main():
    original = ROOT / "third_party/tabler-icons"
    original.mkdir(parents=True, exist_ok=True)
    raw = ROOT / "app/src/main/res/raw"
    raw.mkdir(parents=True, exist_ok=True)
    license_text = urllib.request.urlopen(BASE + "LICENSE", timeout=20).read()
    (original / "LICENSE").write_bytes(license_text)
    (raw / "tabler_license.txt").write_bytes(license_text)
    hashes = {}
    vectors = {}
    for name in NAMES:
        data = urllib.request.urlopen(BASE + "icons/outline/" + name + ".svg", timeout=20).read()
        (original / (name + ".svg")).write_bytes(data)
        hashes[name + ".svg"] = hashlib.sha256(data).hexdigest()
        svg = ET.fromstring(data)
        ET.register_namespace("android", ANDROID)
        attrs = {"width": "32dp", "height": "32dp", "viewportWidth": "24", "viewportHeight": "24"}
        vector = ET.Element("vector", {"{" + ANDROID + "}" + k: v for k, v in attrs.items()})
        color = "#FFD166" if name in ["sun", "cloud-sun"] else "#8ECFFF" if name in ["moon", "cloud-moon", "cloud-rain", "cloud-snow"] else "#E0E6F0"
        for node in svg:
            tag = node.tag.split("}")[-1]
            if tag == "path":
                path = node.attrib["d"]
            elif tag == "circle":
                x, y, r = (float(node.attrib[key]) for key in ["cx", "cy", "r"])
                path = f"M {x-r:g},{y:g} a {r:g},{r:g} 0 1,0 {r*2:g},0 a {r:g},{r:g} 0 1,0 {-r*2:g},0"
            elif tag == "line":
                path = f'M {node.attrib["x1"]},{node.attrib["y1"]} L {node.attrib["x2"]},{node.attrib["y2"]}'
            elif tag == "polyline":
                path = "M " + node.attrib["points"].replace(" ", " L ")
            else:
                raise ValueError(f"Unsupported SVG element: {name}/{tag}")
            values = {"pathData": path, "fillColor": "#00000000", "strokeColor": color,
                      "strokeWidth": "1.6", "strokeLineCap": "round", "strokeLineJoin": "round"}
            ET.SubElement(vector, "path", {"{" + ANDROID + "}" + k: v for k, v in values.items()})
        ET.indent(vector)
        target = ROOT / "app/src/main/res/drawable" / ("weather_" + name.replace("-", "_") + ".xml")
        target.write_text('<!-- Adapted from Tabler Icons v3.31.0, MIT; see third_party/tabler-icons/LICENSE. -->\n'
                          + ET.tostring(vector, encoding="unicode") + "\n", encoding="utf-8", newline="\n")
        vectors[name] = vector
    for suffix, celestial in [("sun", "sun"), ("moon", "moon")]:
        vector = ET.Element("vector", dict(vectors["cloud"].attrib))
        for name, scale, x, y in [(celestial, "0.66", "8", "-1"), ("cloud", "0.84", "0", "5")]:
            values = {"scaleX": scale, "scaleY": scale, "translateX": x, "translateY": y}
            group = ET.SubElement(vector, "group", {"{" + ANDROID + "}" + k: v for k, v in values.items()})
            for path in vectors[name]:
                path = copy.deepcopy(path)
                if name == "cloud": path.set("{" + ANDROID + "}fillColor", "#1A1C20")
                group.append(path)
        ET.indent(vector)
        target = ROOT / "app/src/main/res/drawable" / ("weather_cloud_" + suffix + ".xml")
        target.write_text('<!-- Composed from Tabler Icons v3.31.0 sun/moon and cloud, MIT. -->\n'
                          + ET.tostring(vector, encoding="unicode") + "\n", encoding="utf-8", newline="\n")
    (original / "manifest.json").write_text(json.dumps({"source": BASE, "sha256": hashes}, indent=2) + "\n", encoding="utf-8", newline="\n")
    print("Imported", len(NAMES) + 2, "weather vectors with the upstream MIT license")


if __name__ == "__main__":
    main()
