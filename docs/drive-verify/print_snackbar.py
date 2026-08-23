"""Print snackbar text with safe encoding."""
import re
import sys

path = r"C:\Users\Sampath\.minimax-agent\projects\baton-v170\docs\drive-verify\v196-snack.xml"
with open(path, "r", encoding="utf-8") as f:
    xml = f.read()

texts = re.findall(r'text="([^"]+)"', xml)
print("=" * 60)
print("ALL VISIBLE TEXT AFTER SWIPE")
print("=" * 60)
for t in texts:
    if t.strip():
        # Print with backslash-escape
        print(repr(t))
print("=" * 60)
# Look specifically for snackbar patterns
print("LIKELY SNACKBAR MATCHES:")
for t in texts:
    if "Mark" in t or "recent" in t or "Ramesh" in t or "undo" in t.lower() or "undo" in t.lower():
        print("  " + repr(t))
print("---")
# Check for UUID fragments
print("POSSIBLE UUID FRAGMENTS (hex 6+ chars):")
for t in texts:
    if re.match(r'^[A-Fa-f0-9]{6,8}$', t):
        print("  " + repr(t))
