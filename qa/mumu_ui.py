"""ADB UI driver. Uses actual view bounds, never fixed screen coordinates.

Examples: py -3 qa/mumu_ui.py snapshot main
          py -3 qa/mumu_ui.py tap --text Download --pane 1
          py -3 qa/mumu_ui.py tap --desc More
"""
import argparse
import json
import os
from pathlib import Path
import re
import subprocess
import sys
import time
import xml.etree.ElementTree as ET

sys.stdout.reconfigure(encoding="utf-8", errors="replace")
sys.stderr.reconfigure(encoding="utf-8", errors="replace")

ADB = os.environ.get("HEXORA_ADB", r"E:\AndroidStudioSDK\platform-tools\adb.exe")
SERIAL = os.environ.get("HEXORA_SERIAL", "127.0.0.1:16384")
PACKAGE = "app.hexora.manager"
ARTIFACTS = Path(__file__).resolve().parent / "artifacts"
ARTIFACTS.mkdir(exist_ok=True)
DUMP_PATH = "/sdcard/hexora-qa-" + str(os.getpid()) + ".xml"

def adb(*args, binary=False):
    result = subprocess.run([ADB, "-s", SERIAL, *map(str, args)], capture_output=True, timeout=45)
    if result.returncode:
        raise RuntimeError(result.stderr.decode(errors="replace"))
    return result.stdout if binary else result.stdout.decode(errors="replace").strip()

def tree():
    adb("shell", "rm", "-f", DUMP_PATH)
    try:
        adb("shell", "uiautomator", "dump", "--compressed", DUMP_PATH)
    except RuntimeError:
        # MuMu 15 sometimes crashes the uiautomator shutdown thread AFTER writing XML.
        # Read only a newly produced dump; missing/invalid output remains a test failure.
        with (ARTIFACTS / "uiautomator-notes.txt").open("a") as log:
            log.write("uiautomator exited abnormally; checking newly written XML\n")
    return ET.fromstring(adb("shell", "cat", DUMP_PATH))

def bounds(node):
    return tuple(map(int, re.findall(r"\d+", node.get("bounds", ""))))

def find(text=None, desc=None, rid=None, pane=None, root=None, actionable=False):
    root = root if root is not None else tree()
    width = max(bounds(n)[2] for n in root.iter("node") if len(bounds(n)) == 4)
    matches = []
    for n in root.iter("node"):
        if text is not None and n.get("text") != text: continue
        if desc is not None and n.get("content-desc", "").strip() != desc: continue
        if rid is not None and not n.get("resource-id", "").endswith(":id/" + rid): continue
        b = bounds(n)
        if len(b) != 4 or b[2] <= b[0] or b[3] <= b[1]: continue
        if pane == 1 and b[0] >= width / 2: continue
        if pane == 2 and b[0] < width / 2: continue
        matches.append(n)
    if matches:
        if actionable:
            matches.sort(key=lambda n: (n.get("clickable") == "true",
                                       n.get("class", "").endswith("Button")), reverse=True)
        return matches[0]
    raise AssertionError(f"View not found: {text or desc or rid}, pane={pane}")

def find_visible(text=None, desc=None, rid=None, pane=None, actionable=False):
    try:
        n = find(text, desc, rid, pane, actionable=actionable)
    except AssertionError:
        if pane is None or text is None: raise
        for _ in range(12):
            layout = tree()
            width = max(bounds(n)[2] for n in layout.iter("node") if len(bounds(n)) == 4)
            height = max(bounds(n)[3] for n in layout.iter("node") if len(bounds(n)) == 4)
            x = width // 4 if pane == 1 else 3 * width // 4
            adb("shell", "input", "swipe", x,int(height*.75),x,int(height*.45),400)
            try:
                n = find(text, desc, rid, pane, actionable=actionable)
                break
            except AssertionError: pass
        else: raise AssertionError(f"View not found after scrolling: {text}")
    return n

def tap(text=None, desc=None, rid=None, pane=None, long=False):
    n = find_visible(text, desc, rid, pane, actionable=True)
    x1,y1,x2,y2 = bounds(n)
    x,y = (x1+x2)//2,(y1+y2)//2
    if long: adb("shell", "input", "swipe", x,y,x,y,700)
    else: adb("shell", "input", "tap", x,y)
    time.sleep(.35)

def enter(value):
    adb("shell", "input", "keyevent", "KEYCODE_MOVE_END")
    adb("shell", "input", "keycombination", "113", "29")
    adb("shell", "input", "text", value.replace(" ", "%s"))

def snapshot(name):
    root = tree()
    ET.ElementTree(root).write(ARTIFACTS / (name + ".xml"), encoding="utf-8")
    (ARTIFACTS / (name + ".png")).write_bytes(adb("exec-out", "screencap", "-p", binary=True))
    rows = [{"text":n.get("text"), "desc":n.get("content-desc"),
             "id":n.get("resource-id"), "bounds":n.get("bounds")}
            for n in root.iter("node") if n.get("text") or n.get("content-desc")]
    print("\n".join(str((r['text'], r['desc'], r['bounds'])) for r in rows))

def launch(path=None):
    adb("shell", "am", "force-stop", PACKAGE)
    args = ["shell", "am", "start", "-W", "-n", PACKAGE + "/io.github.abdurazaaqmohammed.MPManager.MainActivity"]
    if path: args += ["--es", "locatePath", path]
    print(adb(*args))
    time.sleep(.7)

if __name__ == "__main__":
    p=argparse.ArgumentParser()
    p.add_argument("action", choices=["snapshot","tap","long","enter","launch","back"])
    p.add_argument("value", nargs="?")
    p.add_argument("--text"); p.add_argument("--desc"); p.add_argument("--rid")
    p.add_argument("--pane", type=int)
    a=p.parse_args()
    if a.action=="snapshot": snapshot(a.value or "screen")
    elif a.action in ("tap","long"): tap(a.text,a.desc,a.rid,a.pane,a.action=="long")
    elif a.action=="enter": enter(a.value)
    elif a.action=="launch": launch(a.value)
    else: adb("shell","input","keyevent",4)
