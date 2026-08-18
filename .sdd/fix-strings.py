import re, subprocess

# Get Tier 2's strings.xml (has v1.5.7 + Tier 0 + Tier 1 + Tier 2)
t2 = subprocess.run(['git', 'show', 'm0/skeleton-v2-moat:app/src/main/res/values/strings.xml'],
                    capture_output=True, text=True).stdout
# Get Tier 3's strings.xml (has v1.5.7 + Tier 0 + Tier 3)
t3 = subprocess.run(['git', 'show', 'm0/skeleton-v2-privacy:app/src/main/res/values/strings.xml'],
                    capture_output=True, text=True).stdout

# Extract <string> entries from each
def extract(s):
    out = {}
    for m in re.finditer(r'<string name="([\w_]+)">(.*?)</string>', s, re.DOTALL):
        out[m.group(1)] = m.group(2)
    return out

t2_strings = extract(t2)
t3_strings = extract(t3)
print(f'Tier 2 strings: {len(t2_strings)}, Tier 3 strings: {len(t3_strings)}')

# Tier 3 added new strings. Use Tier 2 as base, then add Tier 3's additions.
# Find Tier 3 keys that are NOT in Tier 2 (these are the genuine new additions)
new_from_t3 = {k: v for k, v in t3_strings.items() if k not in t2_strings}
print(f'New from Tier 3: {len(new_from_t3)}')
for k, v in list(new_from_t3.items())[:5]:
    print(f'  {k} = {v[:60]}')

# Build the combined strings.xml: take Tier 2 as the base (it has all v1.5.7 + Tier 0 + Tier 1 + Tier 2)
# Then insert the new Tier 3 strings at the right place.
# Simplest: get Tier 2's full file, find the </resources> closing tag, insert new strings before it.

# Get the new Tier 3 strings (raw XML lines) by parsing Tier 3's file
t3_new_lines = []
seen = set()
for line in t3.split('\n'):
    m = re.search(r'<string name="([\w_]+)">', line)
    if m and m.group(1) in new_from_t3 and m.group(1) not in seen:
        t3_new_lines.append(line)
        seen.add(m.group(1))

# Now insert these into Tier 2's strings.xml just before </resources>
combined = t2.rstrip()
if combined.endswith('</resources>'):
    combined = combined[:-len('</resources>')].rstrip()
# Find a good insertion point: just before </resources>
combined = combined + '\n\n' + '    <!-- v2.0 Tier 3 features: deniable vault, BIP39 recovery phrase, threat model. -->\n'
combined += '\n'.join(t3_new_lines) + '\n</resources>\n'

with open(r'app\src\main\res\values/strings.xml', 'w') as fh:
    fh.write(combined)
print(f'Wrote combined file: {len(combined)} bytes')
