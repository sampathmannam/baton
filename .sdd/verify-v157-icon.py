"""Capture v1.5.7 icon screenshots on the emulator, then make thumbnails."""
import os
import subprocess
import time
from PIL import Image

SDD = r"C:\Users\Sampath\.minimax-agent\projects\baton\.sdd"
os.chdir(SDD)

# Wipe old/corrupted pngs
for n in ("icon-v1.5.7-emu-launch", "icon-v1.5.7-emu-home", "icon-v1.5.7-emu-recents"):
    p = f"{n}.png"
    if os.path.exists(p):
        os.remove(p)
    t = f"{n}-thumb.png"
    if os.path.exists(t):
        os.remove(t)

def cap(name):
    out = subprocess.run(
        ["adb", "-s", "emulator-5554", "exec-out", "screencap", "-p"],
        capture_output=True,
    ).stdout
    with open(f"{name}.png", "wb") as f:
        f.write(out)
    print(f"{name}: {len(out)} bytes")

# Make sure MindAnchor and the old release build are not in the way
subprocess.run(["adb", "-s", "emulator-5554", "shell", "am", "force-stop", "org.mindanchor"])
subprocess.run(["adb", "-s", "emulator-5554", "shell", "am", "force-stop", "com.baton.app"])
time.sleep(1)
# Launch the debug build
subprocess.run(["adb", "-s", "emulator-5554", "shell", "am", "start", "-n",
                "com.baton.app.debug/com.baton.app.MainActivity"])
time.sleep(4)
cap("icon-v1.5.7-emu-launch")

# Home
subprocess.run(["adb", "-s", "emulator-5554", "shell", "input", "keyevent", "KEYCODE_HOME"])
time.sleep(2)
cap("icon-v1.5.7-emu-home")

# Recents
subprocess.run(["adb", "-s", "emulator-5554", "shell", "input", "keyevent", "KEYCODE_APP_SWITCH"])
time.sleep(2)
cap("icon-v1.5.7-emu-recents")

# Make thumbnails for inline view
for n in ("icon-v1.5.7-emu-launch", "icon-v1.5.7-emu-home", "icon-v1.5.7-emu-recents"):
    img = Image.open(f"{n}.png")
    print(f"{n}: {img.size} {img.mode}")
    thumb = img.copy()
    thumb.thumbnail((600, 1300))
    thumb.save(f"{n}-thumb.png")
