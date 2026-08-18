#!/usr/bin/env python3
"""Add missing imports to SettingsViewModel.kt."""
import sys
PATH = r'C:\Users\Sampath\.minimax-agent\projects\baton-v2-integration\app\src\main\java\com\baton\app\ui\settings\SettingsViewModel.kt'

with open(PATH, 'rb') as f:
    b = f.read()
has_bom = b.startswith(b'\xef\xbb\xbf')
if has_bom:
    b = b[3:]
text = b.decode('utf-8')

needed = [
    'import kotlinx.coroutines.flow.map',
    'import java.io.File',
]

added = []
for imp in needed:
    if imp not in text:
        # Find insertion point: after the last import line
        lines = text.split('\n')
        last_import_idx = -1
        for i, line in enumerate(lines):
            if line.startswith('import '):
                last_import_idx = i
        if last_import_idx == -1:
            print("ERROR: no imports found", file=sys.stderr)
            sys.exit(1)
        # Insert AFTER the last import, preserving line endings
        # We need to insert in the right CRLF/LF form
        eol = '\r\n' if '\r\n' in text else '\n'
        new_line = imp + eol
        lines.insert(last_import_idx + 1, imp)
        text = eol.join(lines)
        added.append(imp)
        print(f"  + added {imp}")

if added:
    out = (b'\xef\xbb\xbf' if has_bom else b'') + text.encode('utf-8')
    with open(PATH, 'wb') as f:
        f.write(out)
    print(f"Wrote {len(out)} bytes, added {len(added)} imports")
else:
    print("No imports needed")
