"""Find first person row bounds."""
import re
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

# Find any node with text matching a person's name
for name in ("A. Test SP", "B. Ramesh Naidu", "K. Mahesh", "O'Brien Jr.", "O.Brien Jr."):
    pat = re.compile(rf'<node[^>]*text="{re.escape(name)}"[^>]*bounds="(\[\d+,\d+\]\[\d+,\d+\])"')
    m = pat.search(xml)
    if m:
        sys.stdout.write(f"{name}: {m.group(1)}\n")
        bm = re.match(r"\[(\d+),(\d+)\]\[(\d+),(\d+)\]", m.group(1))
        x1, y1, x2, y2 = map(int, bm.groups())
        sys.stdout.write(f"  center=({(x1+x2)//2}, {(y1+y2)//2})\n")
        break
else:
    sys.stdout.write("No person row found\n")
sys.stdout.flush()
