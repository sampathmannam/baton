"""Helper: print all text from a uiautomator dump."""
import re
import sys

path = sys.argv[1]
with open(path, "r", encoding="utf-8") as f:
    xml = f.read()
texts = re.findall(r'text="([^"]+)"', xml)
for t in texts:
    if t.strip():
        print(repr(t))
