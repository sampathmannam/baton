"""Inspect dump and report clickable bounds + top focus."""
import re
import subprocess
import sys

try:
    sys.stdout.reconfigure(encoding="utf-8")
except Exception:
    pass

with open(
    r"C:\Users\Sampath\.minimax-agent\projects\baton-v170\docs\drive-verify\v196-today4.xml",
    encoding="utf-8",
) as f:
    xml = f.read()

# All clickable+long-clickable
pat = re.compile(
    r'<node[^>]*clickable="true"[^>]*long-clickable="true"[^>]*bounds="(\[\d+,\d+\]\[\d+,\d+\])"'
)
for m in pat.finditer(xml):
    b = m.group(1)
    bm = re.match(r"\[(\d+),(\d+)\]\[(\d+),(\d+)\]", b)
    x1, y1, x2, y2 = map(int, bm.groups())
    sys.stdout.write(f"  bounds={b}  w={x2-x1}  h={y2-y1}\n")

# All clickable (any)
sys.stdout.write("---ALL CLICKABLE---\n")
pat2 = re.compile(r'<node[^>]*clickable="true"[^>]*bounds="(\[\d+,\d+\]\[\d+,\d+\])"')
for m in pat2.finditer(xml):
    b = m.group(1)
    bm = re.match(r"\[(\d+),(\d+)\]\[(\d+),(\d+)\]", b)
    x1, y1, x2, y2 = map(int, bm.groups())
    if y1 >= 1000 and y1 <= 2200:
        sys.stdout.write(f"  bounds={b}  w={x2-x1}  h={y2-y1}\n")

# Top focus
out = subprocess.run(
    ["adb", "-s", "emulator-5554", "shell", "dumpsys", "window"],
    capture_output=True,
    text=True,
).stdout
for line in out.splitlines():
    if "mCurrentFocus" in line or "mFocusedApp" in line:
        sys.stdout.write(f"FOCUS: {line.strip()}\n")
sys.stdout.flush()
