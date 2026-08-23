"""Drive-verify helper: find first DecayRow, swipe it right, dump snackbar text."""
import re
import subprocess
import sys
import time

SERIAL = "emulator-5554"
XML_PATH = r"C:\Users\Sampath\.minimax-agent\projects\baton-v170\docs\drive-verify\v196-today.xml"
DUMP_AFTER = r"C:\Users\Sampath\.minimax-agent\projects\baton-v170\docs\drive-verify\v196-after-swipe.xml"
SHOT_AFTER = r"C:\Users\Sampath\.minimax-agent\projects\baton-v170\docs\drive-verify\v196-after-swipe.png"


def adb(*args, check=True):
    return subprocess.run(["adb", "-s", SERIAL, *args], capture_output=True, text=True, check=check)


def dump_ui(path):
    with open(path, "wb") as f:
        subprocess.run(
            ["adb", "-s", SERIAL, "exec-out", "uiautomator", "dump", "/dev/tty"],
            stdout=f,
            check=True,
        )


def screencap(path):
    with open(path, "wb") as f:
        subprocess.run(
            ["adb", "-s", SERIAL, "exec-out", "screencap", "-p"],
            stdout=f,
            check=True,
        )


def find_node(xml_text, predicate):
    """Find a node by predicate(text, bounds_str)."""
    pattern = re.compile(r'<node[^>]*text="([^"]*)"[^>]*bounds="(\[\d+,\d+\]\[\d+,\d+\])"')
    for m in pattern.finditer(xml_text):
        t = m.group(1)
        b = m.group(2)
        if predicate(t, b):
            return t, b
    return None, None


def center_of(bounds):
    m = re.match(r"\[(\d+),(\d+)\]\[(\d+),(\d+)\]", bounds)
    if not m:
        return None
    x1, y1, x2, y2 = map(int, m.groups())
    return (x1 + x2) // 2, (y1 + y2) // 2


def main():
    # Load current Today dump
    with open(XML_PATH, "r", encoding="utf-8") as f:
        xml = f.read()

    # Find the first DecayRow by looking for "haven't touched in" or "Quiet a while" with a person name above it
    # Easier: find clickable rows that contain "haven't touched in" inside
    # Just find the first row whose text contains a person name and "haven't touched"
    pattern = re.compile(
        r'<node[^>]*clickable="true"[^>]*long-clickable="true"[^>]*bounds="(\[\d+,\d+\]\[\d+,\d+\])"[^>]*>(.*?)</node>',
        re.DOTALL,
    )
    candidates = []
    for m in pattern.finditer(xml):
        bounds = m.group(1)
        inner = m.group(2)
        if "haven" in inner and "touched" in inner:
            candidates.append(bounds)

    if not candidates:
        print("No DecayRow found, falling back to A. Test SP")
        text, bounds = find_node(xml, lambda t, b: t == "A. Test SP")
        if not bounds:
            print("ERROR: cannot find A. Test SP either")
            sys.exit(1)
    else:
        bounds = candidates[0]

    cx, cy = center_of(bounds)
    print(f"Swiping DecayRow at center ({cx}, {cy})")
    # Swipe right: start 200px left of center, end 400px right of center, 300ms
    adb("shell", "input", "swipe", str(cx - 200), str(cy), str(cx + 400), str(cy), "300")
    time.sleep(1.5)

    # Dump and screenshot
    dump_ui(DUMP_AFTER)
    screencap(SHOT_AFTER)

    # Extract all visible text
    with open(DUMP_AFTER, "r", encoding="utf-8") as f:
        after = f.read()
    texts = re.findall(r'text="([^"]+)"', after)
    print("=" * 60)
    print("VISIBLE TEXT AFTER SWIPE:")
    for t in texts:
        if t.strip():
            print(f"  {t!r}")
    print("=" * 60)
    print(f"XML  -> {DUMP_AFTER}")
    print(f"PNG  -> {SHOT_AFTER}")


if __name__ == "__main__":
    main()
