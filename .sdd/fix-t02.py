import subprocess, re

# Tier 0 strings
t0 = subprocess.run(['git', '-C', r'C:\Users\Sampath\.minimax-agent\projects\baton-v2-cleanup',
                     'show', 'HEAD:app/src/main/res/values/strings.xml'],
                    capture_output=True).stdout
t0_names = set(re.findall(rb'<string name="(tier0_[\w_]+)">', t0))
print(f'Tier 0 tier0_* strings: {len(t0_names)}')

# Read current file
with open(r'app\src\main\res\values/strings.xml', 'rb') as f:
    data = f.read()

current_names = set(re.findall(rb'<string name="([\w_]+)">', data))
missing = t0_names - current_names
print(f'Missing tier0_*: {len(missing)}')

if missing:
    # Add the missing lines from t0
    new_lines = []
    for line in t0.split(b'\n'):
        m = re.search(rb'<string name="(tier0_[\w_]+)">', line)
        if m and m.group(1) in missing:
            new_lines.append(line)
    # Insert before </resources>
    data = data.replace(b'</resources>', b'\n    <!-- Tier 0 (cleanup) widget + tile labels. -->\n' +
                        b'\n'.join(new_lines) + b'\n</resources>')
    with open(r'app\src\main\res\values/strings.xml', 'wb') as f:
        f.write(data)
    print(f'Added {len(new_lines)} strings')
