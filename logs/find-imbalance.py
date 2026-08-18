import re
p = r'app\src\main\java\com\baton\app\ui\today\TodayScreen.kt'
with open(p, 'r', encoding='utf-8') as f:
    content = f.read()
content2 = re.sub(r'//[^\n]*', '', content)
content2 = re.sub(r'/\*.*?\*/', '', content2, flags=re.DOTALL)
content2 = re.sub(r'"(?:[^"\\]|\\.)*"', '""', content2)
depth = 0
line = 1
depths = {}
for i, ch in enumerate(content2):
    if ch == '\n':
        depths[line] = depth
        line += 1
    elif ch == '{':
        depth += 1
    elif ch == '}':
        depth -= 1
depths[line] = depth
# Find functions: lines starting with "fun" or "@Composable"
# Just print the depth at every 20th line
for l in sorted(depths.keys()):
    if l % 30 == 0 or l in (1, 100, 200, 300, 380, 420, 423):
        print('line', l, 'depth', depths[l])
print('---last 5:---')
for l in sorted(depths.keys())[-5:]:
    print('line', l, 'depth', depths[l])
print('---find lines where depth=1 (we have 1 extra open):---')
last_depth_1 = 0
for l in sorted(depths.keys()):
    if depths[l] == 1:
        last_depth_1 = l
print('last line with depth=1:', last_depth_1)
