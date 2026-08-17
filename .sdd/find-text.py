import re
import sys

with open(sys.argv[1], 'r', encoding='utf-8') as f:
    text = f.read()

if len(sys.argv) > 2:
    needle = sys.argv[2].lower()
else:
    needle = ''

pattern = re.compile(r'text="([^"]+)"[^>]*?bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"')
for m in pattern.finditer(text):
    label, x1, y1, x2, y2 = m.groups()
    x1, y1, x2, y2 = int(x1), int(y1), int(x2), int(y2)
    cx, cy = (x1+x2)//2, (y1+y2)//2
    if not needle or needle in label.lower():
        print(f'"{label}": ({cx}, {cy}) bounds=[{x1},{y1}][{x2},{y2}]')
