"""Baton screen-wise QA driver.

Drives the running Baton app on ZD2232FCR5 through every
screen, dumps the UI after each navigation, taps every
interactive element, and writes findings to
.sdd/qa-findings.md.

Usage:
  python .sdd/qa-drive.py           # full drive
  python .sdd/qa-drive.py home     # single screen
"""
import subprocess
import sys
import time
import re
import xml.etree.ElementTree as ET
from pathlib import Path
from collections import Counter

DEVICE = "ZD2232FCR5"
ADB = "adb"
PKG = "com.baton.app"
PKG_DEBUG = f"{PKG}.debug"
ACT = f"{PKG}/.MainActivity"

DUMP_DIR = Path(r"C:\Users\Sampath\.minimax-agent\projects\baton-v2-integration\.sdd")
FINDINGS_PATH = DUMP_DIR / "qa-findings.md"
QA_DUMP_DIR = DUMP_DIR / "qa-dumps"
QA_DUMP_DIR.mkdir(exist_ok=True)


def adb(*args, check=True, text=True):
    cmd = [ADB, "-s", DEVICE, *args]
    r = subprocess.run(cmd, capture_output=True, text=text)
    if check and r.returncode != 0:
        print(f"[adb] {' '.join(args[:3])}... -> rc={r.returncode}", file=sys.stderr)
    return r


def adb_shell(*args, **kwargs):
    return adb("shell", *args, **kwargs)


def dump(name):
    adb_shell("uiautomator", "dump", f"/sdcard/qa-{name}.xml")
    out = QA_DUMP_DIR / f"{name}.xml"
    adb("pull", f"/sdcard/qa-{name}.xml", str(out))
    return out


def parse(xml_path):
    tree = ET.parse(xml_path)
    elems = []
    for n in tree.iter("node"):
        t = n.get("text", "")
        c = n.get("content-desc", "")
        cl = n.get("clickable", "") == "true"
        b = n.get("bounds", "")
        pkg = n.get("package", "")
        if t or c:
            elems.append({
                "text": t,
                "cdesc": c,
                "clickable": cl,
                "bounds": b,
                "package": pkg,
            })
    return elems


def find_one(elements, *, text=None, cdesc=None, contains=False):
    """Find the first matching element."""
    for el in elements:
        if text is not None and el["text"]:
            if (contains and text in el["text"]) or (not contains and el["text"] == text):
                return el
        if cdesc is not None and el["cdesc"]:
            if (contains and cdesc in el["cdesc"]) or (not contains and el["cdesc"] == cdesc):
                return el
    return None


def tap(bounds):
    m = re.match(r"\[(\d+),(\d+)\]\[(\d+),(\d+)\]", bounds)
    if not m:
        return False
    x1, y1, x2, y2 = map(int, m.groups())
    cx, cy = (x1 + x2) // 2, (y1 + y2) // 2
    adb_shell("input", "tap", str(cx), str(cy))
    return True


def back():
    adb_shell("input", "keyevent", "4")
    time.sleep(0.3)


def home_kill():
    adb_shell("am", "force-stop", PKG)
    adb_shell("am", "force-stop", PKG_DEBUG)
    adb_shell("am", "force-stop", "org.mindanchor")
    time.sleep(0.5)
    adb_shell("am", "start", "-n", ACT)
    time.sleep(2.5)


def find_screen(name):
    """Dump + parse, return elements."""
    path = dump(name)
    return parse(path)


def drive_home():
    """Home / People screen."""
    home_kill()
    els = find_screen("01-home")
    log("**Home / People**")
    log(f"  elements: {len(els)}")
    summary(els)

    # Tap each person row
    for name in ["Bhanu", "Inba", "Sampath", "Uma"]:
        # Re-dump because previous tap may have navigated
        els = find_screen(f"01-home-{name.lower()}")
        person = find_one(els, text=name)
        if person:
            log(f"  tap person: {name}")
            tap(person["bounds"])
            time.sleep(1.0)
            # Person detail screen
            els2 = find_screen(f"02-person-{name.lower()}")
            log(f"  -> person detail: {len(els2)} elements")
            summary(els2)
            back()
            time.sleep(0.7)
    # Search bar
    els = find_screen("01-home-search")
    search = find_one(els, text="Search people and instructions", contains=True)
    if search:
        log("  tap search bar")
        tap(search["bounds"])
        time.sleep(0.5)
        adb_shell("input", "text", "temple")
        time.sleep(0.5)
        els2 = find_screen("01-home-search-temple")
        summary(els2)
        back()
        time.sleep(0.5)
        back()
    return els


def drive_today():
    """Today screen."""
    home_kill()
    # Tap "Today" in bottom nav
    els = find_screen("03-today-nav")
    today = find_one(els, text="Today")
    if today:
        tap(today["bounds"])
        time.sleep(1.0)
    els = find_screen("03-today")
    log("**Today**")
    log(f"  elements: {len(els)}")
    summary(els)
    # Tap any "carried over" or instruction row if present
    for el in els:
        if el["clickable"] and el["text"] and (
            "temple" in el["text"].lower() or
            "fir" in el["text"].lower() or
            "seizure" in el["text"].lower() or
            "brief" in el["text"].lower() or
            "birthday" in el["text"].lower()
        ):
            log(f"  tap row: {el['text'][:40]}")
            tap(el["bounds"])
            time.sleep(1.0)
            els2 = find_screen(f"03-today-detail")
            summary(els2)
            back()
            time.sleep(0.7)
    return els


def drive_settings():
    """Settings sheet (bottom nav)."""
    home_kill()
    els = find_screen("04-settings-nav")
    settings = find_one(els, text="Settings")
    if settings:
        tap(settings["bounds"])
        time.sleep(1.0)
    els = find_screen("04-settings")
    log("**Settings**")
    log(f"  elements: {len(els)}")
    summary(els)
    # Try every row
    rows = [el for el in els if el["clickable"] and el["text"]]
    for el in rows[:8]:
        log(f"  tap row: {el['text'][:40]}")
        tap(el["bounds"])
        time.sleep(0.8)
        els2 = find_screen(f"04-settings-{el['text'][:20].lower().replace(' ', '-')}")
        summary(els2)
        back()
        time.sleep(0.5)
    return els


def drive_capture():
    """Capture sheet (tap note bar)."""
    home_kill()
    els = find_screen("05-capture-nav")
    note_bar = find_one(els, cdesc="Add note")
    if not note_bar:
        note_bar = find_one(els, text="Tap to add a note", contains=True)
    if note_bar:
        log("**Capture**")
        log("  tap note bar")
        tap(note_bar["bounds"])
        time.sleep(1.0)
    els = find_screen("05-capture-sheet")
    log(f"  elements: {len(els)}")
    summary(els)
    # Try Extract
    extract = find_one(els, text="Extract")
    if extract:
        log("  tap Extract (no text yet)")
        tap(extract["bounds"])
        time.sleep(1.0)
        els2 = find_screen("05-capture-extract")
        summary(els2)
    return els


def log(s):
    print(s)
    with open(FINDINGS_PATH, "a", encoding="utf-8") as f:
        f.write(s + "\n")


def summary(els, max_lines=40):
    """Print a compact summary of a UI dump."""
    # text or content-desc, with bounds
    seen = set()
    for el in els:
        key = el["text"] or el["cdesc"]
        if not key or key in seen:
            continue
        seen.add(key)
        marker = " *" if el["clickable"] else ""
        log(f"    [{el['bounds']}] {key!r}{marker}")
        if len(seen) >= max_lines:
            break


def main():
    if FINDINGS_PATH.exists():
        FINDINGS_PATH.unlink()
    log("# Baton v1.6.0.1 screen-wise QA findings\n")
    log(f"Date: {time.strftime('%Y-%m-%d %H:%M:%S')}")
    log(f"Device: {DEVICE}\n")
    if len(sys.argv) > 1:
        cmd = sys.argv[1]
        globals()[f"drive_{cmd}"]()
    else:
        drive_home()
        drive_today()
        drive_settings()
        drive_capture()
    log("\n# Done")


if __name__ == "__main__":
    main()
