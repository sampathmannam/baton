import subprocess

# Get raw bytes from each tier's strings.xml
def get(branch, path):
    return subprocess.run(['git', '-C', fr'C:\Users\Sampath\.minimax-agent\projects\baton-v2-{branch}',
                          'show', f'HEAD:{path}'],
                         capture_output=True).stdout

t1 = get('survival', 'app/src/main/res/values/strings.xml')
t2 = get('moat', 'app/src/main/res/values/strings.xml')
t3 = get('privacy', 'app/src/main/res/values/strings.xml')

import re

# Extract string names from each
def get_names(data):
    return set(re.findall(rb'<string name="([\w_]+)">', data))

t1_names = get_names(t1)
t2_names = get_names(t2)
t3_names = get_names(t3)

# Build combined by taking t1 as base, adding missing t2 lines, then missing t3 lines
def get_unused_lines(data, existing_names):
    out = []
    for line in data.split(b'\n'):
        m = re.search(rb'<string name="([\w_]+)">', line)
        if m and m.group(1) not in existing_names:
            out.append(line)
    return out

t1_lines = t1.split(b'\n')
# Find the </resources> line in t1
end_idx = None
for i, line in enumerate(t1_lines):
    if line.strip() == b'</resources>':
        end_idx = i
        break

t1_truncated = b'\n'.join(t1_lines[:end_idx])
# Strip trailing whitespace
t1_truncated = t1_truncated.rstrip() + b'\n'

# Get new lines from t2 and t3
existing = set(t1_names)
t2_new = get_unused_lines(t2, existing)
existing.update(t2_names)
t3_new = get_unused_lines(t3, existing)

combined = t1_truncated + b'\n    <!-- v2.0 Tier 2 features. -->\n'
combined += b'\n'.join(t2_new) + b'\n\n    <!-- v2.0 Tier 3 features. -->\n'
combined += b'\n'.join(t3_new) + b'\n</resources>\n'

with open(r'app\src\main\res\values/strings.xml', 'wb') as f:
    f.write(combined)
print(f'Wrote {len(combined)} bytes ({len(t1_names)+len(t2_new)+len(t3_new)} strings)')
