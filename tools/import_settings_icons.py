"""Import a pinned set of Lucide outline icons as API-23 Android vectors."""

import concurrent.futures
import hashlib
import json
import re
from pathlib import Path
import urllib.request
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[1]
BASE = "https://raw.githubusercontent.com/lucide-icons/lucide/1.17.0/"
NAMES = ["settings", "monitor-cog", "layout-grid", "wifi", "bluetooth", "monitor",
         "volume-2", "folder-open", "cast", "images", "shield-check", "info", "x"]
ANDROID = "http://schemas.android.com/apk/res/android"


def explicit_moves(value):
    # This TV's API-23 vector renderer drops implicit line segments after M/m.
    # Keep the same geometry but emit the L/l command explicitly.
    def replace(match):
        numbers = re.findall(r"[-+]?(?:\d*\.\d+|\d+\.?\d*)(?:[eE][-+]?\d+)?",match.group(2))
        if len(numbers) < 2 or len(numbers) % 2:
            raise ValueError("Invalid SVG move coordinates")
        result = match.group(1) + ",".join(numbers[:2])
        if len(numbers) > 2:
            result += (" L" if match.group(1) == "M" else " l") + " ".join(numbers[2:])
        return result
    return re.sub(r"([Mm])([^AaCcHhLlMmQqSsTtVvZz]*)",replace,value)


def path_data(node):
    tag = node.tag.split("}")[-1]
    a = node.attrib
    if tag == "path":
        return explicit_moves(a["d"])
    if tag == "circle":
        x, y, r = (float(a[k]) for k in ("cx", "cy", "r"))
        return f"M{x-r:g},{y:g} a{r:g},{r:g} 0 1,0 {2*r:g},0 a{r:g},{r:g} 0 1,0 {-2*r:g},0"
    if tag == "line":
        return f'M{a["x1"]},{a["y1"]} L{a["x2"]},{a["y2"]}'
    if tag in ("polyline", "polygon"):
        return "M " + a["points"].strip().replace(" ", " L ") + (" Z" if tag == "polygon" else "")
    if tag == "rect":
        x, y = float(a.get("x", 0)), float(a.get("y", 0))
        w, h = float(a["width"]), float(a["height"])
        rx = min(float(a.get("rx", a.get("ry", 0))), w/2)
        ry = min(float(a.get("ry", a.get("rx", 0))), h/2)
        return (f"M{x+rx:g},{y:g} L{x+w-rx:g},{y:g} Q{x+w:g},{y:g} {x+w:g},{y+ry:g} "
                f"L{x+w:g},{y+h-ry:g} Q{x+w:g},{y+h:g} {x+w-rx:g},{y+h:g} "
                f"L{x+rx:g},{y+h:g} Q{x:g},{y+h:g} {x:g},{y+h-ry:g} "
                f"L{x:g},{y+ry:g} Q{x:g},{y:g} {x+rx:g},{y:g} Z")
    raise ValueError("Unsupported SVG primitive: " + tag)


def main():
    original = ROOT / "third_party/lucide"
    original.mkdir(parents=True, exist_ok=True)
    raw = ROOT / "app/src/main/res/raw"
    license_text = urllib.request.urlopen(BASE + "LICENSE", timeout=20).read()
    (original / "LICENSE").write_bytes(license_text)
    (raw / "lucide_license.txt").write_bytes(license_text)

    def fetch(name):
        return name, urllib.request.urlopen(BASE + "icons/" + name + ".svg", timeout=20).read()

    hashes = {}
    with concurrent.futures.ThreadPoolExecutor(max_workers=5) as pool:
        for name, data in pool.map(fetch, NAMES):
            (original / (name + ".svg")).write_bytes(data)
            hashes[name + ".svg"] = hashlib.sha256(data).hexdigest()
            svg = ET.fromstring(data)
            ET.register_namespace("android", ANDROID)
            vector = ET.Element("vector", {"{" + ANDROID + "}" + k: v for k, v in
                {"width": "32dp", "height": "32dp", "viewportWidth": "24", "viewportHeight": "24"}.items()})
            for node in svg:
                fill = node.attrib.get("fill", "none")
                if fill not in ("none", "currentColor"):
                    raise ValueError("Unexpected icon fill")
                stroke = node.attrib.get("stroke", "currentColor")
                values = {"pathData": path_data(node), "fillColor": "#BDC7D1" if fill == "currentColor" else "#00000000",
                          "strokeColor": "#00000000" if stroke == "none" else "#BDC7D1",
                          "strokeWidth": str(float(node.attrib.get("stroke-width", "2")) * .85),
                          "strokeLineCap": "round", "strokeLineJoin": "round"}
                ET.SubElement(vector, "path", {"{" + ANDROID + "}" + k: v for k, v in values.items()})
            ET.indent(vector)
            target = ROOT / "app/src/main/res/drawable" / ("menu_" + name.replace("-", "_") + ".xml")
            target.write_text('<!-- Adapted from Lucide 1.17.0; see third_party/lucide/LICENSE. -->\n'
                + ET.tostring(vector, encoding="unicode") + "\n", encoding="utf-8", newline="\n")
    (original / "manifest.json").write_text(json.dumps({"source": BASE, "sha256": hashes,
        "license_sha256": hashlib.sha256(license_text).hexdigest()}, indent=2) + "\n", encoding="utf-8", newline="\n")
    print("Imported", len(NAMES), "Lucide vectors and the upstream ISC / Feather MIT notices")


if __name__ == "__main__":
    main()
