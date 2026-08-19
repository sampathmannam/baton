import re
import sys

with open(sys.argv[1], 'rb') as f:
    content = f.read().decode('utf-8', errors='replace').replace(chr(0), '')

# Find count badges (numbers 1-99, right-aligned in upper area)
for m in re.finditer(r'text="(\d+)"[^>]*bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"', content):
    t = m.group(1)
    y1 = int(m.group(3))
    if 1 <= int(t) <= 99 and y1 < 1800:
        b = '[' + m.group(2) + ',' + m.group(3) + '][' + m.group(4) + ',' + m.group(5) + ']'
        print('Count:', t, 'bounds=', b)
