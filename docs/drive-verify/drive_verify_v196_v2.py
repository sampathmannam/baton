"""Clean drive-verify v1.9.6 — careful version."""
import re
import subprocess
import sys
import time

SERIAL = "emulator-5554"
BASE = r"C:\Users\Sampath\.minimax-agent\projects\baton-v170\docs\drive-verify"

try:
    sys.stdout.reconfigure(encoding="utf-8")
except Exception:
    pass


def adb(*args):
    return subprocess.run(
        ["adb", "-s", SERIAL, *args], capture_output=True, text=True, check=True
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


def get_top_pkg():
    out = adb("shell", "dumpsys", "window", "windows").stdout
    m = re.search(r"mCurrentFocus=.*?(\S+/\S+)", out)
    return m.group(1) if m else "?"


# Re-launch Baton and BSA-stop
adb("shell", "am", "force-stop", "com.bsa.dummies.debug")
adb("shell", "am", "force-stop", "com.baton.app")
time.sleep(1)
adb("shell", "am", "start", "-n", "com.baton.app/.MainActivity")
time.sleep(4)
print("Top:", get_top_pkg())

# Dismiss notification dialog
xml = open(dump("perm2"), "r", encoding="utf-8").read()
m = re.search(
    r'<node[^>]*text="(Don[^"]*t allow|Don.t allow)"[^>]*bounds="(\[\d+,\d+\]\[\d+,\d+\])"',
    xml,
)
if m:
    b = m.group(2)
    bm = re.match(r"\[(\d+),(\d+)\]\[(\d+),(\d+)\]", b)
    cx = (int(bm.group(1)) + int(bm.group(3))) // 2
    cy = (int(bm.group(2)) + int(bm.group(4))) // 2
    print(f"Dismissing notif perm at ({cx}, {cy})")
    adb("shell", "input", "tap", str(cx), str(cy))
    time.sleep(2)

# Find and tap Today tab
xml = open(dump("home2"), "r", encoding="utf-8").read()
# Find the Today tab in bottom nav
m = re.search(
    r'<node[^>]*text="Today"[^>]*bounds="(\[\d+,\d+\]\[\d+,\d+\])"', xml
)
# Better: find the clickable parent of "Today" text in bottom nav
# Bottom nav: bounds [0,2127][1080,2337] is bottom strip
# The 3 tabs are at [0,2157][346,2307] Home, [367,2148][713,2317] Today, [734,2157][1080,2307] Settings
# Direct tap on (540, 2232)
print("Tapping Today tab at (540, 2232)")
adb("shell", "input", "tap", "540", "2232")
time.sleep(2.5)
shot("today4")
today_path = dump("today4")
xml = open(today_path, "r", encoding="utf-8").read()

# Find first person row in decay section by looking for bounds in [1000-1900] y-range
rows = []
pattern = re.compile(
    r'<node[^>]*clickable="true"[^>]*long-clickable="true"[^>]*bounds="(\[\d+,\d+\]\[\d+,\d+\])"'
)
for m in pattern.finditer(xml):
    b = m.group(1)
    bm = re.match(r"\[(\d+),(\d+)\]\[(\d+),(\d+)\]", b)
    x1, y1, x2, y2 = map(int, bm.groups())
    if y1 >= 1300 and y2 <= 2200 and (x2 - x1) > 600:
        rows.append((b, (x1, y1, x2, y2)))

print(f"Found {len(rows)} candidate DecayRows:")
for b, _ in rows[:5]:
    sys.stdout.write(f"  {b}\n")

if not rows:
    print("ERROR: no DecayRows found")
    sys.exit(1)

# Pick first row, swipe right
b, (x1, y1, x2, y2) = rows[0]
cx, cy = (x1 + x2) // 2, (y1 + y2) // 2
print(f"Swiping row at ({cx}, {cy}) bounds={b}")
# Use a longer swipe duration (500ms) and clearer horizontal motion
adb("shell", "input", "swipe", str(x1 + 50), str(cy), str(x2 - 50), str(cy), "500")
# Capture snackbar ASAP
time.sleep(0.3)
snack1 = dump("snack3a")
shot("snack3a")
time.sleep(0.7)
snack2 = dump("snack3b")
shot("snack3b")

# Print results
for name, path in [("TODAY", today_path), ("SNACK_A", snack1), ("SNACK_B", snack2)]:
    with open(path, "r", encoding="utf-8") as f:
        xml = f.read()
    texts = re.findall(r'text="([^"]+)"', xml)
    sys.stdout.write(f"\n{'=' * 60}\n{name}\n{'=' * 60}\n")
    for t in texts:
        if t.strip():
            sys.stdout.write(f"  {t}\n")
    sys.stdout.flush()
