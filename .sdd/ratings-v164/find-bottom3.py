import re
import sys

with open(sys.argv[1], 'rb') as f:
    content = f.read()
content = content.decode('utf-8', errors='replace').replace(chr(0), '')

# Find all bounds in y > 2000
for m in re.finditer(r'bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"', content):
    y1 = int(m.group(2))
    y2 = int(m.group(4))
    if y1 >= 2000 or (y1 < 2000 and y2 >= 2000):
        print(f'bounds=[{m.group(1)},{y1}][{m.group(3)},{y2}]')
print('---')
# All clickable
for m in re.finditer(r'clickable="true"[^>]*bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"', content):
    print(f'clickable bounds=[{m.group(1)},{m.group(2)}][{m.group(3)},{m.group(4)}]')
