p = r'app\src\main\java\com\baton\app\ui\settings\SettingsViewModel.kt'
with open(p, 'r', encoding='utf-8') as f:
    content = f.read()
bom = '\ufeff'
if content.startswith(bom):
    content = content[len(bom):]
lines = content.splitlines(keepends=True)
print('total lines:', len(lines))
seen = 0
for i, line in enumerate(lines):
    if line.startswith('package '):
        seen += 1
        if seen == 2:
            keep = lines[:i]
            with open(p, 'w', encoding='utf-8') as f:
                f.writelines(keep)
            print(f'truncated at line {i+1}, kept {len(keep)} lines')
            break
