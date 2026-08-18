import re, subprocess

out = subprocess.run(['git', 'show', 'm0/skeleton-v2-cleanup:app/src/main/res/values/strings.xml'],
                     capture_output=True, text=True).stdout
t0_strings = {}
for m in re.finditer(r'<string name="(tier0_[\w_]+)">([^<]*)</string>', out):
    t0_strings[m.group(1)] = m.group(2)
print(f'Tier 0 strings: {t0_strings}')

# Add these to the current strings.xml if missing
with open(r'app\src\main\res/values/strings.xml') as fh:
    current = fh.read()
missing = {k: v for k, v in t0_strings.items() if f'name="{k}"' not in current}
print(f'Missing in current: {missing}')

if missing:
    # Insert before </resources>
    additions = '\n    <!-- Tier 0 features (cleanup) - widget + tile labels. -->\n'
    for k, v in missing.items():
        additions += f'    <string name="{k}">{v}</string>\n'
    new = current.rstrip()
    if new.endswith('</resources>'):
        new = new[:-len('</resources>')].rstrip() + additions + '</resources>\n'
    with open(r'app\src\main\res/values/strings.xml', 'w') as fh:
        fh.write(new)
    print('Added tier0 strings')
