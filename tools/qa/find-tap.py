import re
import sys

target = sys.argv[1]
xml_path = sys.argv[2]
with open(xml_path, 'r', encoding='utf-8') as f:
    text = f.read()

# Find ALL nodes that have the target text. We also need to find
# the EditText (PIN input) and the Save button.
if target == "EditText":
    nodes = re.findall(r'<node[^>]*?class="android\.widget\.EditText"[^>]*?bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"', text)
else:
    nodes = re.findall(r'<node[^>]*?text="([^"]*)"[^>]*?bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"', text)
for tup in nodes:
    if target == "EditText":
        x1, y1, x2, y2 = tup
        t = "EditText"
    else:
        t, x1, y1, x2, y2 = tup
    if target in t:
        cx = (int(x1) + int(x2)) // 2
        cy = (int(y1) + int(y2)) // 2
        print(f'  text="{t}"  bounds=[{x1},{y1}][{x2},{y2}]  tap=({cx},{cy})')
