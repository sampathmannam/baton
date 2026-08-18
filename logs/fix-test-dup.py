#!/usr/bin/env python3
"""Truncate SettingsViewModelTest.kt at duplicate `package` declaration."""
import sys
PATH = r'C:\Users\Sampath\.minimax-agent\projects\baton-v2-integration\app\src\test\java\com\baton\app\ui\settings\SettingsViewModelTest.kt'

with open(PATH, 'rb') as f:
    b = f.read()
has_bom = b.startswith(b'\xef\xbb\xbf')
if has_bom:
    b = b[3:]
text = b.decode('utf-8')

# Find all occurrences of `package com.baton.app.ui.settings`
import re
matches = [m.start() for m in re.finditer(r'package com\.baton\.app\.ui\.settings', text)]
print(f"Found {len(matches)} occurrences: {matches}")
if len(matches) < 2:
    print("ERROR: need at least 2 occurrences", file=sys.stderr)
    sys.exit(1)

# Keep first 200 lines (first copy), truncate before second `package`
second_idx = matches[1]
# Truncate to before the duplicate (but include a trailing newline)
# Walk back to the start of the line
line_start = text.rfind('\n', 0, second_idx) + 1
text = text[:line_start]
print(f"Truncated to {len(text)} chars (cut at offset {line_start})")

out = (b'\xef\xbb\xbf' if has_bom else b'') + text.encode('utf-8')
with open(PATH, 'wb') as f:
    f.write(out)
print(f"Wrote {len(out)} bytes")
