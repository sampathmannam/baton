#!/usr/bin/env python3
"""Fix the stray `)` line 158 in SettingsViewModel.kt (CRLF endings)."""
import sys
PATH = r'C:\Users\Sampath\.minimax-agent\projects\baton-v2-integration\app\src\main\java\com\baton\app\ui\settings\SettingsViewModel.kt'
with open(PATH, 'rb') as f:
    b = f.read()
has_bom = b.startswith(b'\xef\xbb\xbf')
if has_bom:
    b = b[3:]
text = b.decode('utf-8')
# Lines 156-158 are: '    )', '', '    )'  (with CRLF endings)
# Use the exact byte sequence with \r\n
needle = '    )\n\n\r\n    )\r\n\r\n    /**'
repl = '    )\r\n\r\n    /**'
if needle not in text:
    print("ERROR: needle not found", file=sys.stderr)
    # Debug: show context
    idx = text.find('    )')
    while idx != -1:
        print(f"Found '    )' at {idx}: {text[idx:idx+30]!r}", file=sys.stderr)
        idx = text.find('    )', idx + 1)
    sys.exit(1)
text = text.replace(needle, repl, 1)
out = (b'\xef\xbb\xbf' if has_bom else b'') + text.encode('utf-8')
with open(PATH, 'wb') as f:
    f.write(out)
print(f"Wrote {len(out)} bytes (removed stray paren)")
