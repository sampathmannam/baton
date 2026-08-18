import re
p = r'app\src\main\java\com\baton\app\ui\today\TodayScreen.kt'
with open(p, 'r', encoding='utf-8') as f:
    content = f.read()
# Remove comments and strings
content2 = re.sub(r'//[^\n]*', '', content)
content2 = re.sub(r'/\*.*?\*/', '', content2, flags=re.DOTALL)
content2 = re.sub(r'"(?:[^"\\]|\\.)*"', '""', content2)
# Count per line, find lines that have unmatched { (i.e. +1 in the count)
# but only in function bodies
opens = [0] * (content2.count(chr(10)) + 2)
closes = [0] * (content2.count(chr(10)) + 2)
line = 1
for ch in content2:
    if ch == '\n':
        line += 1
    elif ch == '{':
        opens[line] += 1
    elif ch == '}':
        closes[line] += 1
# Find lines where opens - closes != 0
for l in range(1, len(opens)):
    if opens[l] - closes[l] != 0:
        pass  # too verbose, instead find unmatched candidates
# Sum totals
print('total opens:', sum(opens))
print('total closes:', sum(closes))
print('lines with opens > 0:')
for l in range(1, len(opens)):
    if opens[l] > 0 and closes[l] == 0:
        # pure-open line
        if 250 <= l <= 300:
            pass  # filter to interesting range
# Find lines with only opens and no closes
pure_open = []
pure_close = []
for l in range(1, len(opens)):
    if opens[l] > 0 and closes[l] == 0:
        pure_open.append((l, opens[l]))
    if closes[l] > 0 and opens[l] == 0:
        pure_close.append((l, closes[l]))
print('first 10 pure-open lines:')
for l, c in pure_open[:10]:
    print('  line', l, 'opens:', c)
print('first 10 pure-close lines:')
for l, c in pure_close[:10]:
    print('  line', l, 'closes:', c)
print('last 10 pure-close lines:')
for l, c in pure_close[-10:]:
    print('  line', l, 'closes:', c)
print('last 10 pure-open lines:')
for l, c in pure_open[-10:]:
    print('  line', l, 'opens:', c)
