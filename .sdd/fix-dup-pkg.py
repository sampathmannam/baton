import os, re

files = [
    'app/src/main/java/com/baton/app/di/DatabaseModule.kt',
    'app/src/main/java/com/baton/app/ui/settings/SettingsViewModel.kt',
    'app/src/main/java/com/baton/app/ui/today/TodayScreen.kt',
    'app/src/main/java/com/baton/app/data/local/InstructionDao.kt',  # already fixed but verify
    'app/src/main/java/com/baton/app/data/local/entities/InstructionEntity.kt',
    'app/src/main/java/com/baton/app/MainActivity.kt',
    'app/src/main/java/com/baton/app/ui/settings/SettingsSheet.kt',
]

for f in files:
    if not os.path.exists(f):
        print(f'  missing: {f}')
        continue
    with open(f) as fh:
        content = fh.read()
    # Find duplicate package declarations
    matches = list(re.finditer(r'^package ', content, re.MULTILINE))
    if len(matches) < 2:
        print(f'  no dup: {f}')
        continue
    # Find the second package, truncate before it
    second_pkg = matches[1].start()
    # Find the first closing brace before second_pkg (the end of legitimate content)
    truncated = content[:second_pkg].rstrip()
    # Append the rest of the file from the second package if it has useful content
    rest = content[second_pkg:].strip()
    if rest:
        # Extract non-imports/non-package content from rest
        lines = rest.split('\n')
        new_lines = []
        skip = True
        for line in lines:
            if skip and (line.startswith('import ') or line.startswith('package ')):
                continue
            skip = False
            new_lines.append(line)
        if new_lines:
            # Append after the truncated file
            # But we need to handle case where the truncated file is missing closing brace
            truncated += '\n\n' + '\n'.join(new_lines).strip() + '\n'
    # Ensure file ends with proper brace if needed
    if not truncated.rstrip().endswith('}'):
        # Try to find a closing brace
        if 'class ' in truncated or 'interface ' in truncated or 'object ' in truncated:
            # likely needs a closing brace
            pass
    with open(f, 'w') as fh:
        fh.write(truncated)
    print(f'  cleaned: {f} (truncated to {len(truncated)} bytes)')
