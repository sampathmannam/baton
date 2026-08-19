import re
import sys

with open(sys.argv[1], 'rb') as f:
    content = f.read().decode('utf-8', errors='replace').replace(chr(0), '')

# Find text=Settings
for m in re.finditer(r'text="([^"]*)"[^>]*bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"', content):
    t = m.group(1)
    if t and ('Setting' in t or 'Home' in t or 'Today' in t or 'People' in t):
        b = f'[{m.group(2)},{m.group(3)}][{m.group(4)},{m.group(5)}]'
        print(f'Text: {t:20s} bounds={b}')

# All clickable
print('--- clickable ---')
for m in re.finditer(r'clickable="true"[^>]*bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"', content):
    b = f'[{m.group(1)},{m.group(2)}][{m.group(3)},{m.group(4)}]'
    print(f'clickable bounds={b}')
