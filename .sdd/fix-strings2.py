import re, subprocess

# Tier 1 has v1.5.7 + Tier 0 + Tier 1 features (vault, search, theme, onboarding, etc.)
t1 = subprocess.run(['git', 'show', 'm0/skeleton-v2-survival:app/src/main/res/values/strings.xml'],
                    capture_output=True, text=True).stdout
# Tier 2 has Tier 1 + Tier 2 features (decay, worry, important dates, etc.)
t2 = subprocess.run(['git', 'show', 'm0/skeleton-v2-moat:app/src/main/res/values/strings.xml'],
                    capture_output=True, text=True).stdout
# Tier 3 has Tier 0 + Tier 3 (privacy, recovery phrase, threat model)
t3 = subprocess.run(['git', 'show', 'm0/skeleton-v2-privacy:app/src/main/res/values/strings.xml'],
                    capture_output=True, text=True).stdout

def extract(s):
    out = {}
    for m in re.finditer(r'<string name="([\w_]+)">(.*?)</string>', s, re.DOTALL):
        out[m.group(1)] = m.group(2)
    return out

t1_s = extract(t1)
t2_s = extract(t2)
t3_s = extract(t3)

# Build union: start with Tier 1, add anything from Tier 2 not in Tier 1, then Tier 3
all_s = dict(t1_s)
for k, v in t2_s.items():
    if k not in all_s:
        all_s[k] = v
for k, v in t3_s.items():
    if k not in all_s:
        all_s[k] = v

print(f'Tier 1: {len(t1_s)}, Tier 2: {len(t2_s)}, Tier 3: {len(t3_s)}, Union: {len(all_s)}')

# Get the raw lines from each file (preserves order)
def get_new_lines(content, existing_set):
    out = []
    for line in content.split('\n'):
        m = re.search(r'<string name="([\w_]+)">', line)
        if m and m.group(1) not in existing_set:
            out.append(line)
    return out

existing = set(t1_s.keys())
t2_lines = get_new_lines(t2, existing)
existing.update(t2_s.keys())
t3_lines = get_new_lines(t3, existing)

# Build the combined file from Tier 1's full content
combined = t1.rstrip()
if combined.endswith('</resources>'):
    combined = combined[:-len('</resources>')].rstrip()
combined += '\n\n' + '    <!-- v2.0 Tier 2 features (§2.1-§2.14): decay, worry, dates, brief, person links, etc. -->\n'
combined += '\n'.join(t2_lines) + '\n'
combined += '\n    <!-- v2.0 Tier 3 features: deniable vault, BIP39 recovery phrase, threat model. -->\n'
combined += '\n'.join(t3_lines) + '\n</resources>\n'

with open(r'app\src\main\res\values/strings.xml', 'w') as fh:
    fh.write(combined)
print(f'Wrote {len(combined)} bytes')
