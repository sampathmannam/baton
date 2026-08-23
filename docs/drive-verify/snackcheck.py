"""One-shot: dump UI, screencap, find snackbar."""
import re
import subprocess
import sys
import time

SERIAL = "emulator-5554"
DUMP = r"C:\Users\Sampath\.minimax-agent\projects\baton-v170\docs\drive-verify\v196-snack.xml"
SHOT = r"C:\Users\Sampath\.minimax-agent\projects\baton-v170\docs\drive-verify\v196-snack.png"


# 1. Dump current UI
with open(DUMP, "wb") as f:
    subprocess.run(
        ["adb", "-s", SERIAL, "exec-out", "uiautomator", "dump", "/dev/tty"],
        stdout=f,
        check=True,
    )
with open(SHOT, "wb") as f:
    subprocess.run(
        ["adb", "-s", SERIAL, "exec-out", "screencap", "-p"],
        stdout=f,
        check=True,
    )

# 2. Print all visible text
with open(DUMP, "r", encoding="utf-8") as f:
    xml = f.read()
texts = re.findall(r'text="([^"]+)"', xml)
print("=" * 70)
print("ALL VISIBLE TEXT (current state)")
print("=" * 70)
for t in texts:
    if t.strip():
        print(repr(t))
print("=" * 70)
print("LIKELY SNACKBAR / REVEALED ROW:")
for t in texts:
    if any(k in t for k in ("Mark", "recent", "Ramesh", "undo", "UNDO", "restored", "Swipe right")):
        print("  " + repr(t))
print("---")
print("POSSIBLE UUID FRAGMENTS (hex 6+ chars):")
for t in texts:
    if re.match(r'^[A-Fa-f0-9]{6,8}$', t):
        print("  " + repr(t))

# Look at first 3 chars of each "label" pattern, see if there's anything weird
print("---")
print("ALL texts that are 6-12 chars long (might be UUID fragments or action labels):")
for t in texts:
    if t.strip() and 4 <= len(t) <= 14:
        print(f"  len={len(t):2d}  {t!r}")
