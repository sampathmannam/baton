"""End-to-end v1.9.6 drive-verify on emulator-5554.

Does:
  1. Force-stop BSA + Baton, relaunch Baton
  2. Dismiss notification permission dialog if present
  3. Tap Today tab
  4. Confirm 3 fixes visible: today's win copy, gesture hint, person row
  5. Swipe right on first person row
  6. Dump snackbar text, verify NO UUID fragment

Writes outputs to docs/drive-verify/v196-*.xml/png + dv-final.txt
"""
import re
import subprocess
import sys
import time
from pathlib import Path

try:
    sys.stdout.reconfigure(encoding="utf-8")
except Exception:
    pass

SERIAL = "emulator-5554"
BASE = Path(r"C:\Users\Sampath\.minimax-agent\projects\baton-v170\docs\drive-verify")
BASE.mkdir(parents=True, exist_ok=True)

# Open log file with utf-8 encoding
LOG = open(BASE / "dv-final.txt", "w", encoding="utf-8")


def log(msg=""):
    sys.stdout.write(msg + "\n")
    LOG.write(msg + "\n")
    LOG.flush()
    sys.stdout.flush()


def adb(*args, timeout=10):
    return subprocess.run(
        ["adb", "-s", SERIAL, *args], capture_output=True, text=True, check=False, timeout=timeout
    )


def shell(*args, timeout=10):
    return adb("shell", *args, timeout=timeout)


def dump(name):
    p = BASE / f"v196-{name}.xml"
    with open(p, "wb") as f:
        subprocess.run(
            ["adb", "-s", SERIAL, "exec-out", "uiautomator", "dump", "/dev/tty"],
            stdout=f,
            check=True,
        )
    return p


def shot(name):
    p = BASE / f"v196-{name}.png"
    with open(p, "wb") as f:
        subprocess.run(
            ["adb", "-s", SERIAL, "exec-out", "screencap", "-p"],
            stdout=f,
            check=True,
        )
    return p


def parse_bounds(b):
    m = re.match(r"\[(\d+),(\d+)\]\[(\d+),(\d+)\]", b)
    if not m:
        return None
    return tuple(int(x) for x in m.groups())


def all_texts(xml_path):
    with open(xml_path, "r", encoding="utf-8") as f:
        xml = f.read()
    return [t for t in re.findall(r'text="([^"]+)"', xml) if t.strip()]


def find_node_bounds(xml_text, predicate):
    """Find first node whose text matches predicate(text)."""
    pat = re.compile(r'<node[^>]*text="([^"]*)"[^>]*bounds="(\[\d+,\d+\]\[\d+,\d+\])"')
    for m in pat.finditer(xml_text):
        t, b = m.group(1), m.group(2)
        if predicate(t):
            return t, b
    return None, None


# === 1. Reset to clean state ===
log("=" * 70)
log("STEP 1: Force-stop competing apps + relaunch Baton")
log("=" * 70)
shell("am", "force-stop", "com.bsa.dummies.debug")
shell("am", "force-stop", "com.baton.app")
time.sleep(1)
r = shell("am", "start", "-n", "com.baton.app/.MainActivity")
log(r.stdout.strip())
time.sleep(5)

# === 2. Dismiss permission dialog if any ===
log("")
log("=" * 70)
log("STEP 2: Dismiss permission dialog (if any)")
log("=" * 70)
xml = dump("01-perm").read_text(encoding="utf-8")
text, b = find_node_bounds(xml, lambda t: t in ("Don't allow", "DON\u2019T ALLOW"))
if not b:
    text, b = find_node_bounds(xml, lambda t: "Don" in t and "allow" in t.lower())
if b:
    bb = parse_bounds(b)
    cx, cy = (bb[0] + bb[2]) // 2, (bb[1] + bb[3]) // 2
    log(f"  Tap 'Don't allow' at ({cx}, {cy})")
    shell("input", "tap", str(cx), str(cy))
    time.sleep(2)
else:
    log("  No permission dialog visible")

# === 3. Tap Today tab ===
log("")
log("=" * 70)
log("STEP 3: Tap Today tab")
log("=" * 70)
shell("input", "tap", "540", "2232")
time.sleep(3)
shot("02-today")
today_xml = dump("02-today").read_text(encoding="utf-8")
texts = all_texts(BASE / "02-today.xml")
log("Visible text on Today:")
for t in texts:
    log(f"  {t!r}")

# === 4. Confirm 3 fixes by text ===
log("")
log("=" * 70)
log("STEP 4: Confirm 3 fixes present")
log("=" * 70)

joined = "\n".join(texts)
checks = [
    ("Today's win copy", "No captures today yet. Add one from the Home tab."),
    ("Gesture hint", "Swipe right or long-press a card to mark someone as recent."),
]
for name, expected in checks:
    if expected in joined:
        log(f"  PASS  {name}: '{expected}'")
    else:
        log(f"  FAIL  {name}: '{expected}' NOT FOUND")

# === 5. Find first person row, swipe right ===
log("")
log("=" * 70)
log("STEP 5: Find first person row + swipe right")
log("=" * 70)

# Try to find any person by their name in the today dump
person_names = [
    "A. Test SP", "B. Ramesh Naidu", "K. Mahesh",
    "O'Brien Jr.", "O’Brien Jr.",
    "B. Srinivas", "B. Srinivasa", "A. Venkateshwarlu",
    "सु. अनीता",  # Hindi name from earlier dump
]
chosen = None
for name in person_names:
    text, b = find_node_bounds(today_xml, lambda t, n=name: t == n)
    if b:
        chosen = (name, b)
        break

if not chosen:
    log("  No person row found by name; falling back to first node with 'haven' in subtree")
    # Find a person row by its parent: any View containing 'haven' (from "haven't touched")
    # Look for the topmost element with that text
    pat = re.compile(r'<node[^>]*text="([^"]*haven[^"]*)"[^>]*bounds="(\[\d+,\d+\]\[\d+,\d+\])"')
    matches = list(pat.finditer(today_xml))
    if matches:
        # Use the first match's bounds as approximate
        chosen = ("(by 'haven')", matches[0].group(2))

if not chosen:
    log("  ABORT: No person row found in dump")
    log("  Full text dump:")
    for t in texts:
        log(f"    {t!r}")
    sys.exit(1)

name, b = chosen
bb = parse_bounds(b)
log(f"  Found {name!r} at {b} (x1={bb[0]}, y1={bb[1]}, x2={bb[2]}, y2={bb[3]})")

# The text bounds are just the name; the actual card is taller. Use the text y as
# the swipe y, and the full screen width for x.
cy = (bb[1] + bb[3]) // 2
x_start, x_end = 100, 980
swipe_ms = 400
log(f"  Swiping: ({x_start}, {cy}) -> ({x_end}, {cy}) in {swipe_ms}ms")
shell("input", "swipe", str(x_start), str(cy), str(x_end), str(cy), str(swipe_ms))

# Capture snackbar immediately
time.sleep(0.3)
shot("03-snack")
snack1 = dump("03-snack")
texts1 = all_texts(snack1)
log("  Text 0.3s after swipe:")
for t in texts1:
    log(f"    {t!r}")

time.sleep(1.0)
shot("04-snack2")
snack2 = dump("04-snack2")
texts2 = all_texts(snack2)
log("  Text 1.3s after swipe:")
for t in texts2:
    log(f"    {t!r}")

# === 6. Analyze snackbar ===
log("")
log("=" * 70)
log("STEP 6: Analyze snackbar for UUID fragment")
log("=" * 70)
combined = texts1 + texts2
# Look for the expected snackbar text
expected_marker = "marked"  # Baton uses "Marked as recent" or similar
snackbar_candidates = [t for t in combined if any(k in t for k in ("Mark", "marked", "recent", "Marked"))]
log(f"  Snackbar candidates (text containing Mark/marked/recent): {snackbar_candidates}")

# Check for UUID fragment (hex 6-12 chars not in dictionary words)
uuid_pat = re.compile(r'\b[A-Fa-f0-9]{6,12}\b')
uuid_hits = []
for t in combined:
    for m in uuid_pat.finditer(t):
        hex_str = m.group(0)
        # Skip common false positives
        if hex_str.lower() in ("000000", "ffffff", "abcdef", "123456"):
            continue
        uuid_hits.append((t, hex_str))
log(f"  UUID-like hex fragments in any text: {uuid_hits}")

# === 7. Final verdict ===
log("")
log("=" * 70)
log("STEP 7: Final verdict")
log("=" * 70)
verdict = {
    "todays_win_copy": "No captures today yet. Add one from the Home tab." in joined,
    "gesture_hint_visible": "Swipe right or long-press a card" in joined,
    "person_row_visible": chosen is not None,
    "snackbar_text_present": len(snackbar_candidates) > 0,
    "no_uuid_fragment": len(uuid_hits) == 0,
}
log("  Checks:")
for k, v in verdict.items():
    log(f"    {k}: {'PASS' if v else 'FAIL'}")

log("")
log("Output files:")
for f in sorted(BASE.glob("v196-*.xml")):
    log(f"  {f}")
for f in sorted(BASE.glob("v196-*.png")):
    log(f"  {f}  ({(f.stat().st_size // 1024)} KB)")

LOG.close()
print(f"\nFull log: {BASE / 'dv-final.txt'}")
