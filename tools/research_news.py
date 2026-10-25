"""Download and inspect the six publisher feeds from this PC; never connects to the TV."""

import concurrent.futures
import datetime as dt
import email.utils
import hashlib
import json
import pathlib
import re
import subprocess
import urllib.request
import xml.etree.ElementTree as ET

ROOT = pathlib.Path(__file__).resolve().parents[1]
DESTINATION = ROOT / "nor/research/update-070/feeds"
SHANGHAI = dt.timezone(dt.timedelta(hours=8))


class NoRedirect(urllib.request.HTTPRedirectHandler):
    def redirect_request(self, request, response, code, message, headers, target):
        return None


def inspect(source):
    category, url = source
    request = urllib.request.Request(url, headers={"User-Agent": "QuietTV/0.7 (personal Android TV launcher)"})
    with urllib.request.build_opener(NoRedirect).open(request, timeout=15) as response:
        data = response.read(512 * 1024 + 1)
        status = response.status
    if len(data) > 512 * 1024:
        raise RuntimeError(f"Oversize {category} feed")
    text = data.decode("utf-8-sig")
    if category in ("china", "world"):
        wrapper = re.fullmatch(r"\s*" + category + r"\s*\((.*)\)\s*;?\s*", text, re.S)
        if not wrapper:
            raise RuntimeError(f"Unexpected {category} wrapper")
        rows = json.loads(wrapper.group(1))["data"]["list"]
        dates = [dt.datetime.strptime(row["focus_date"], "%Y-%m-%d %H:%M:%S").replace(tzinfo=SHANGHAI)
                 for row in rows if row.get("focus_date")]
    elif category == "finance":
        rows = json.loads(text)["data"]["items"]
        dates = [dt.datetime.fromtimestamp(row["display_time"], dt.timezone.utc) for row in rows]
        if not all(row.get("uri") and (row.get("title") or row.get("content_text")) for row in rows):
            raise RuntimeError("Finance feed has missing story fields")
    elif category == "game":
        wrapper = re.fullmatch(r"\s*\((.*)\);\s*", text, re.S)
        if not wrapper:
            raise RuntimeError("Unexpected game wrapper")
        payload = json.loads(wrapper.group(1))
        if payload.get("status") != "ok":
            raise RuntimeError("Game source unavailable")
        rows = re.findall(r'<div\s+class="time">(.*?)</div>', payload["body"], re.S)
        dates = [dt.datetime.strptime(value.strip(), "%Y-%m-%d %H:%M").replace(tzinfo=SHANGHAI) for value in rows]
    else:
        if "<!DOCTYPE" in text.upper() or "<!ENTITY" in text.upper():
            raise RuntimeError("Unexpected XML declaration")
        rows = ET.fromstring(text).findall(".//item")
        dates = [email.utils.parsedate_to_datetime(row.findtext("pubDate")) for row in rows]
        if not all(row.findtext("title") and row.findtext("link") for row in rows):
            raise RuntimeError(f"Missing {category} fields")
    now = dt.datetime.now(dt.timezone.utc)
    recent = [date for date in dates if dt.timedelta(minutes=-5) <= now - date <= dt.timedelta(hours=48)]
    if not recent:
        raise RuntimeError(f"No recent {category} stories")
    file = DESTINATION / (category + (".xml" if category in ("tech", "ai") else ".json"))
    file.write_bytes(data)
    return {"category": category, "url": url, "status": status, "bytes": len(data),
            "sha256": hashlib.sha256(data).hexdigest(), "items": len(rows), "recent_48h": len(recent),
            "newest": max(recent).astimezone(SHANGHAI).isoformat(), "file": file.name}


def main():
    # Read the actual application endpoint table rather than maintain a second list.
    source = (ROOT / "app/src/main/java/local/mitv/launcher/NewsSources.java").read_text(encoding="utf-8")
    ids = re.findall(r'"([a-z]+)"', re.search(r'IDS\s*=\s*\{(.*?)\}', source, re.S).group(1))
    urls = re.findall(r'"(https://[^\"]+)"', re.search(r'URLS\s*=\s*\{(.*?)\}', source, re.S).group(1))
    if len(ids) != 6 or len(urls) != 6:
        raise RuntimeError("Unexpected source table")
    DESTINATION.mkdir(parents=True, exist_ok=True)
    with concurrent.futures.ThreadPoolExecutor(max_workers=3) as worker:
        results = list(worker.map(inspect, zip(ids, urls)))
    manifest = {"checked_at": dt.datetime.now(SHANGHAI).isoformat(), "origin": "development PC, not the TV", "sources": results}
    (DESTINATION / "manifest.json").write_text(json.dumps(manifest, ensure_ascii=False, indent=2), encoding="utf-8")
    for row in results:
        print(f"PC FEED: {row['category']} HTTP {row['status']} items={row['items']} recent={row['recent_48h']} newest={row['newest']}")
    classes = ROOT / "nor/build/test-classes"
    if (classes / "local/mitv/launcher/RssFeedTest.class").exists():
        from build import JDK
        subprocess.run([str(JDK / "bin/java.exe"), "-cp", str(classes), "local.mitv.launcher.RssFeedTest",
                        *[str(DESTINATION / (category + ".xml")) for category in ("tech", "ai")]], check=True)


if __name__ == "__main__":
    main()
