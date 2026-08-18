p = r'app\src\main\java\com\baton\app\ui\settings\SettingsViewModel.kt'
with open(p, 'r', encoding='utf-8') as f:
    lines = f.readlines()
# Find and remove the LLM block. Look for the line starting with "v1.5.4: the on-device LLM model state"
# and the "fun signOut()" line, remove everything in between.
start_idx = None
end_idx = None
for i, line in enumerate(lines):
    if 'v1.5.4: the on-device LLM model state' in line:
        # Walk back to the /** comment opener
        for j in range(i-1, -1, -1):
            if lines[j].strip().startswith('/**'):
                start_idx = j
                break
        break
# Find the "fun signOut() {" line
for i, line in enumerate(lines):
    if start_idx is not None and i > start_idx and 'fun signOut()' in line:
        # Walk back to the line before "fun signOut()"
        for j in range(i-1, start_idx, -1):
            if lines[j].strip() != '' and not lines[j].strip().startswith('*') and not lines[j].strip().startswith('//'):
                # Found the last non-empty non-comment line
                end_idx = j + 1
                break
        if end_idx is None:
            end_idx = start_idx
        break
print(f'start_idx={start_idx} end_idx={end_idx}')
print('---start line---')
print(lines[start_idx])
print('---end line---')
print(lines[end_idx-1] if end_idx else 'n/a')
if start_idx and end_idx:
    new_lines = lines[:start_idx] + lines[end_idx:]
    with open(p, 'w', encoding='utf-8') as f:
        f.writelines(new_lines)
    print(f'removed {end_idx - start_idx} lines; new size = {len(new_lines)} lines')
