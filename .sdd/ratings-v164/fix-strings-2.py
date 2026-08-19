import sys

path = 'app/src/main/res/values/strings.xml'
with open(path, 'r', encoding='utf-8') as f:
    content = f.read()

old = 'Loads the synthetic test fixture (12 people, 36 instructions, 7 captures, 12 tags). Erases existing data first. Debug builds only.'
new = 'Loads the synthetic test fixture (55 people, 200 instructions, 50 captures, 16 tags in v1.6.4). Erases existing data first. Debug builds only.'

if old in content:
    content = content.replace(old, new, 1)
    with open(path, 'w', encoding='utf-8') as f:
        f.write(content)
    print('Replaced')
else:
    print('Not found')
    sys.exit(1)
