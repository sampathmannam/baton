import re
p = r'app\src\main\java\com\baton\app\ui\today\TodayScreen.kt'
with open(p, 'r', encoding='utf-8') as f:
    content = f.read()
# Remove comments and strings
content2 = re.sub(r'//[^\n]*', '', content)
content2 = re.sub(r'/\*.*?\*/', '', content2, flags=re.DOTALL)
content2 = re.sub(r'"(?:[^"\\]|\\.)*"', '""', content2)
lines = content2.split(chr(10))
# Find function definitions: lines starting with "fun " or "@Composable\nfun "
fn_starts = []
for i, line in enumerate(lines):
    # Find top-level (depth 1) functions
    if re.match(r'^\s*fun\s+\w+', line):
        # Check if this is depth-1 function (class level)
        # Count depth up to this line
        depth = 0
        for j in range(i):
            for ch in lines[j]:
                if ch == '{': depth += 1
                elif ch == '}': depth -= 1
        if depth == 1:
            fn_starts.append((i+1, line.strip()))
print('top-level functions:')
for ln, name in fn_starts:
    print(f'  line {ln}: {name[:80]}')
# Now check brace balance for each function
print('---brace balance per function---')
for idx, (start, name) in enumerate(fn_starts):
    end_line = fn_starts[idx+1][0] - 1 if idx+1 < len(fn_starts) else len(lines)
    opens = 0
    closes = 0
    for j in range(start-1, end_line):
        for ch in lines[j]:
            if ch == '{': opens += 1
            elif ch == '}': closes += 1
    diff = opens - closes
    print(f'  fn at line {start}: opens={opens} closes={closes} diff={diff}  {name[:60]}')
