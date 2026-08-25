import re, sys
with open(sys.argv[1], 'r', encoding='utf-8') as f:
    xml = f.read()
for m in re.finditer(r'text="([^"]+)"[^>]*bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"', xml):
    t, x1, y1, x2, y2 = m.group(1), int(m.group(2)), int(m.group(3)), int(m.group(4)), int(m.group(5))
    print(f'[{x1:>4},{y1:>4}][{x2:>4},{y2:>4}] h={y2-y1:>4} w={x2-x1:>4}  {t[:90]}')
