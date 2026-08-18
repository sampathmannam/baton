import re, sys
p = r'app\src\main\java\com\baton\app\ui\today\TodayScreen.kt'
with open(p, 'r', encoding='utf-8') as f:
    content = f.read()
content2 = re.sub(r'//[^\n]*', '', content)
content2 = re.sub(r'/\*.*?\*/', '', content2, flags=re.DOTALL)
content2 = re.sub(r'"(?:[^"\\]|\\.)*"', '""', content2)
depth = 0
max_depth = 0
max_line = 0
line = 1
last_problem = None
for i, ch in enumerate(content2):
    if ch == '\n':
        line += 1
    elif ch == '{':
        depth += 1
        if depth > max_depth:
            max_depth = depth
            max_line = line
    elif ch == '}':
        depth -= 1
        if depth < 0:
            last_problem = ('unmatched } at line', line)
            break
print('final depth:', depth, 'max depth:', max_depth, 'at line:', max_line)
print('problem:', last_problem)
print('total lines:', content.count(chr(10)) + 1)
