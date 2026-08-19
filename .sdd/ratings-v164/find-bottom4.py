import re
import sys

with open(sys.argv[1], 'rb') as f:
    content = f.read().decode('utf-8', errors='replace').replace(chr(0), '')

# All bounds with bottom > 1900
for m in re.finditer(r'bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"', content):
    y2 = int(m.group(4))
    if y2 >= 1900 and y2 <= 2200:
        b = '[' + m.group(1) + ',' + m.group(2) + '][' + m.group(3) + ',' + m.group(4) + ']'
        print('bounds=', b)
print('---')
# All text with bottom > 1900
for m in re.finditer(r'text="([^"]*)"[^>]*bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"', content):
    y1 = int(m.group(3))
    y2 = int(m.group(5))
    if y1 >= 1900 and y2 <= 2300:
        t = m.group(1)
        t_safe = t.encode('ascii', 'replace').decode('ascii')[:30]
        b = '[' + m.group(2) + ',' + m.group(3) + '][' + m.group(4) + ',' + m.group(5) + ']'
        print('Text:', t_safe, 'bounds=', b)
