with open(r'app\src\main\java\com\baton\app\ui/settings/SettingsViewModel.kt') as fh:
    lines = fh.readlines()
# Find a data class close } followed by imports
for i, l in enumerate(lines):
    if l.strip() == '}' and i > 300:
        # Check if next line is 'import'
        if i+1 < len(lines) and lines[i+1].startswith('import '):
            print('  Found closing } at line {}, next line is import'.format(i+1))
            # Truncate here
            new_lines = lines[:i+1]
            with open(r'app\src\main\java\com\baton\app\ui/settings/SettingsViewModel.kt', 'w') as fh:
                fh.writelines(new_lines)
            print('  Wrote {} lines'.format(len(new_lines)))
            break
