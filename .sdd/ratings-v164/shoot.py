"""Take a screenshot + dump + thumb on the given device."""
import os
import subprocess
import sys

DEV = sys.argv[1]
PREFIX = sys.argv[2]
OUT = ".sdd/ratings-v164"

os.makedirs(OUT, exist_ok=True)

def run(cmd):
    return subprocess.run(cmd, shell=True, capture_output=True, text=True)

# Take screenshot
shot_path = f"{OUT}/{PREFIX}.png"
run(f"adb -s {DEV} exec-out screencap -p > {shot_path}")
print(f"shot: {shot_path} ({os.path.getsize(shot_path):,} bytes)")

# Dump UI
run(f"adb -s {DEV} shell uiautomator dump /sdcard/d.xml >/dev/null")
dump_path = f"{OUT}/{PREFIX}.xml"
run(f"adb -s {DEV} exec-out cat /sdcard/d.xml > {dump_path}")
print(f"dump: {dump_path} ({os.path.getsize(dump_path):,} bytes)")

# Make thumb (Python image lib)
try:
    from PIL import Image
    img = Image.open(shot_path)
    img.thumbnail((540, 1200))
    thumb_path = f"{OUT}/{PREFIX}.thumb.png"
    img.save(thumb_path, optimize=True)
    print(f"thumb: {thumb_path} ({os.path.getsize(thumb_path):,} bytes, {img.size})")
except ImportError:
    print("PIL not available — skipping thumb")
