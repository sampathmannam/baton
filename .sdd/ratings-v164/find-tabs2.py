import re
import sys

with open(sys.argv[1], 'rb') as f:
    content = f.read().decode('utf-8', errors='replace').replace(chr(0), '')

# Find text=Settings
for m in re.finditer(r'text="([^"]*)"[^>]*bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"', content):
    t = m.group(1)
    if t and ('Setting' in t or 'Home' in t or 'Today' in t or 'People' in t or 'Quick' in t):
        b = '[' + m.group(2) + ',' + m.group(3) + '][' + m.group(4) + ',' + m.group(5) + ']'
        t_safe = t.encode('ascii', 'replace').decode('ascii')
        print('Text:', t_safe, 'bounds=', b)

# All clickable in y > 2000
print('--- clickable in y > 1900 ---')
for m in re.finditer(r'clickable="true"[^>]*bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"', content):
    y1 = int(m.group(2))
    if y1 >= 1900:
        b = '[' + m.group(1) + ',' + m.group(2) + '][' + m.group(3) + ',' + m.group(4) + ']'
        print('clickable bounds=', b)
