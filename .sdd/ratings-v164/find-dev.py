import re
import sys

with open(sys.argv[1], 'rb') as f:
    content = f.read().decode('utf-8', errors='replace').replace(chr(0), '')

for m in re.finditer(r'text="([^"]*)"[^>]*bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"', content):
    t = m.group(1)
    if 'Load' in t or 'reload' in t.lower() or 'Erase' in t:
        b = '[' + m.group(2) + ',' + m.group(3) + '][' + m.group(4) + ',' + m.group(5) + ']'
        print('Text:', t, 'bounds=', b)
