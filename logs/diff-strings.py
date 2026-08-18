import re, subprocess
# Get HEAD's strings.xml
head_xml = subprocess.run(['git', 'show', 'HEAD:app/src/main/res/values/strings.xml'],
    cwd=r'C:\Users\Sampath\.minimax-agent\projects\baton-v2-integration',
    capture_output=True, text=True).stdout
# Current
with open(r'app\src\main\res\values\strings.xml', 'r', encoding='utf-8') as f:
    cur_xml = f.read()
# Extract names
head_names = set(re.findall(r'name="(\w+)"', head_xml))
cur_names = set(re.findall(r'name="(\w+)"', cur_xml))
missing = sorted(head_names - cur_names)
print('Missing in current ({}):'.format(len(missing)))
for n in missing:
    print(' ', n)
