import re
import sys
with open(sys.argv[1], 'r', encoding='utf-8') as f:
    text = f.read()
texts = re.findall(r'text="([^"]+)"', text)
for t in texts:
    if t.strip():
        print('TEXT:', t)
