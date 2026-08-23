"""Full drive-verify sequence for v1.9.6 — re-run after relaunch."""
import re
import subprocess
import sys
import time
from io import open as iopen  # noqa

# Force UTF-8 on stdout/stderr (Windows PowerShell cp1252 chokes on U+2019)
try:
    sys.stdout.reconfigure(encoding="utf-8")
    sys.stderr.reconfigure(encoding="utf-8")
except Exception:
    pass

SERIAL = "emulator-5554"
BASE = r"C:\Users\Sampath\.minimax-agent\projects\baton-v170\docs\drive-verify"


def adb(*args, check=True):
    return subprocess.run(
        ["adb", "-s", SERIAL, *args], capture_output=True, text=True, check=check
    )


def dump(name):
    p = f"{BASE}\\v196-{name}.xml"
    with open(p, "wb") as f:
        subprocess.run(
            ["adb", "-s", SERIAL, "exec-out", "uiautomator", "dump", "/dev/tty"],
            stdout=f,
            check=True,
        )
    return p


def shot(name):
    p = f"{BASE}\\v196-{name}.png"
    with open(p, "wb") as f:
        subprocess.run(
            ["adb", "-s", SERIAL, "exec-out", "screencap", "-p"],
            stdout=f,
            check=True,
        )
    return p


def tap(x, y):
    adb("shell", "input", "tap", str(x), str(y))


def swipe(x1, y1, x2, y2, ms=300):
    adb("shell", "input", "swipe", str(x1), str(y1), str(x2), str(y2), str(ms))


def find_node(xml_text, predicate):
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


def print_texts(xml_path, label):
    with open(xml_path, "r", encoding="utf-8") as f:
        xml = f.read()
    texts = re.findall(r'text="([^"]+)"', xml)
    sep = "=" * 70
    sys.stdout.write(f"\n{sep}\n{label}\n{sep}\n")
    for t in texts:
        if t.strip():
            sys.stdout.write(f"  {repr(t)}\n")
    sys.stdout.flush()


# 1. Find Don't allow button and tap it
dump("perm")
with open(f"{BASE}\\v196-perm.xml", "r", encoding="utf-8") as f:
    xml = f.read()
text, bounds = find_node(xml, lambda t, b: "Don" in t and "allow" in t)
if bounds:
    cx, cy = center_of(bounds)
    print(f"Tapping 'Don't allow' at ({cx}, {cy})")
    tap(cx, cy)
    time.sleep(2)

# 2. Tap Today tab
print("Tapping Today tab at (540, 2280)")
tap(540, 2280)
time.sleep(2.5)
today_path = dump("today3")
shot("today3")
print_texts(today_path, "TODAY TAB")

# 3. Find first DecayRow (clickable+long-clickable, contains "haven't")
with open(today_path, "r", encoding="utf-8") as f:
    xml = f.read()
pattern = re.compile(
    r'<node[^>]*clickable="true"[^>]*long-clickable="true"[^>]*bounds="(\[\d+,\d+]\[\d+,\d+\])"',
)
row_bounds = None
for m in pattern.finditer(xml):
    b = m.group(1)
    pattern_int = re.compile(
        r'<node[^>]*text="(A\. Test SP|B\. Ramesh Naidu|K\. Mahesh|O.Brien Jr\.|B\. Srinivas|B\. Srinivasa|A\. Venkateshwarlu)"',
    )
    # crude: pick the first such row
    # need to check this bounds is a person row, not the search bar
    # search bar bounds is around [42,317][1038,464]
    bm = re.match(r"\[(\d+),(\d+)\]\[(\d+),(\d+)\]", b)
    if bm:
        y1 = int(bm.group(2))
        if y1 > 1000:  # below search bar
            row_bounds = b
            break

if not row_bounds:
    print("ERROR: no DecayRow found")
    sys.exit(1)

cx, cy = center_of(row_bounds)
print(f"\nSwiping DecayRow at center ({cx}, {cy})")
swipe(cx - 200, cy, cx + 400, cy, 250)
# Catch snackbar immediately (snackbar has 2-4s default duration)
time.sleep(0.5)
snack_path = dump("snack2")
shot("snack2")
print_texts(snack_path, "SNACKBAR (immediately after swipe)")

# 4. Also check the gesture hint was dismissed (since markRecent should flip the pref)
time.sleep(4)  # let snackbar dismiss
after_path = dump("after-swipe")
shot("after-swipe")
print_texts(after_path, "AFTER SNACKBAR DISMISSES (check hint gone)")
