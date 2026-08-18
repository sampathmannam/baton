import re
with open(r'app\src\main\res\values\strings.xml', 'r', encoding='utf-8') as f:
    content = f.read()
names = re.findall(r'name="(\w+)"', content)
for n in names:
    if 'confidence' in n or 'extract' in n or 'llm' in n or 'confirmation' in n or 'model' in n:
        print(n)
print('total strings:', len(names))
