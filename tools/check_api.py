"""Check compiled platform references against the installed SDK's API history (no device)."""

import argparse
import os
import pathlib
import struct
import xml.etree.ElementTree as ET

ROOT = pathlib.Path(__file__).resolve().parents[1]
SDK = pathlib.Path(os.environ.get("ANDROID_HOME", str(pathlib.Path.home() / "AppData/Local/Android/Sdk")))


def class_references(path):
    data = memoryview(path.read_bytes())
    if bytes(data[:4]) != b"\xca\xfe\xba\xbe":
        raise ValueError(f"Invalid class file: {path}")
    offset = 8

    def read(fmt):
        nonlocal offset
        result = struct.unpack_from(fmt, data, offset)
        offset += struct.calcsize(fmt)
        return result[0] if len(result) == 1 else result

    pool = [None] * read(">H")
    index = 1
    while index < len(pool):
        tag = read(">B")
        if tag == 1:
            length = read(">H")
            pool[index] = (tag, bytes(data[offset:offset + length]).decode("utf-8", "replace"))
            offset += length
        elif tag in (3, 4):
            offset += 4
        elif tag in (5, 6):
            offset += 8
            index += 1
        elif tag in (7, 8, 16, 19, 20):
            pool[index] = (tag, read(">H"))
        elif tag in (9, 10, 11, 12, 17, 18):
            pool[index] = (tag, *read(">HH"))
        elif tag == 15:
            offset += 3
        else:
            raise ValueError(f"Unknown constant pool tag: {tag}")
        index += 1

    def classname(index):
        return pool[pool[index][1]][1]

    read(">H")  # access flags
    name = classname(read(">H"))
    parent = read(">H")
    parents = [classname(parent)] if parent else []
    parents += [classname(read(">H")) for _ in range(read(">H"))]
    refs = []
    for item in pool:
        if item and item[0] in (9, 10, 11):
            pair = pool[item[2]]
            refs.append((classname(item[1]), "field" if item[0] == 9 else "method",
                         pool[pair[1]][1] + ("" if item[0] == 9 else pool[pair[2]][1])))
    return name, parents, refs


def check(classes, minimum=23):
    api_file = SDK / "platforms/android-32/data/api-versions.xml"
    api = {node.attrib["name"]: node for node in ET.parse(api_file).getroot().findall("class")}
    compiled = [class_references(file) for file in sorted(classes.rglob("*.class"))]
    parents = {name: bases for name, bases, _ in compiled}
    refs = {ref for _, _, items in compiled for ref in items}

    def introduced(owner, kind, name, visited=None):
        visited = set() if visited is None else visited
        if owner in visited:
            return None
        visited.add(owner)
        if owner in parents:
            candidates = [introduced(base, kind, name, visited) for base in parents[owner]]
        elif owner in api:
            node = api[owner]
            candidates = [int(child.attrib.get("since", node.attrib.get("since", 1)))
                          for child in node.findall(kind) if child.attrib["name"] == name]
            # Constructors do not inherit.
            if not name.startswith("<init>"):
                candidates += [introduced(child.attrib["name"], kind, name, visited)
                               for child in node if child.tag in ("extends", "implements")]
        else:
            return None
        return min((value for value in candidates if value is not None), default=None)

    failures = []
    for owner, kind, name in sorted(refs):
        # This compiler bootstrap is lowered by D8 --min-api; it is not an APK runtime call.
        if owner.startswith("java/lang/invoke/"):
            continue
        added = introduced(owner, kind, name)
        if added is not None and added > minimum:
            failures.append(f"API {added}: {owner}.{name}")
    if failures:
        raise RuntimeError("Platform references exceed minSdk:\n" + "\n".join(failures))
    print(f"PASS: {len(refs)} platform/member references checked against API {minimum} history")


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--classes", type=pathlib.Path, default=ROOT / "nor/build/classes")
    parser.add_argument("--minimum", type=int, default=23)
    args = parser.parse_args()
    check(args.classes, args.minimum)
