#!/usr/bin/env python3
"""Fix the broken SettingsViewModel constructor in SettingsViewModelTest.kt."""
import sys
PATH = r'C:\Users\Sampath\.minimax-agent\projects\baton-v2-integration\app\src\test\java\com\baton\app\ui\settings\SettingsViewModelTest.kt'

with open(PATH, 'rb') as f:
    b = f.read()
has_bom = b.startswith(b'\xef\xbb\xbf')
if has_bom:
    b = b[3:]
text = b.decode('utf-8')

# The broken block has:
# - Line 88: '        val appContext = mockk<android.content.Context>(relaxed = true)\r\n'
# - Line 89: '                authRepository = auth,' (no \r\n, 16 spaces)
# - Line 90-100: '        appInitializer = init,\r\n' etc.
# - Line 101: '        appContext = mockk<android.content.Context>(relaxed = true),\r\n'
# - Line 102: '        return VmMocks(init, auth, realtime, syncEngine, vm)\r\n'

# Use a regex with DOTALL to match from line 88 through line 102
import re
pattern = re.compile(
    r'        val appContext = mockk<android\.content\.Context>\(relaxed = true\)\r?\n'
    r'                authRepository = auth,\r?\n'
    r'(?:        [^\r\n]+\r?\n)+'
    r'        return VmMocks\(init, auth, realtime, syncEngine, vm\)',
    re.MULTILINE
)
m = pattern.search(text)
if not m:
    print("ERROR: pattern not found", file=sys.stderr)
    sys.exit(1)

new_block = """        val appContext = mockk<android.content.Context>(relaxed = true)
        val vm = SettingsViewModel(
            authRepository = auth,
            appInitializer = init,
            tagRepository = mockk<RoomTagRepository>(relaxed = true),
            realtimeSync = realtime,
            syncEngine = syncEngine,
            personDao = mockk<PersonDao>(relaxed = true),
            instructionDao = mockk<InstructionDao>(relaxed = true),
            tagDao = mockk<TagDao>(relaxed = true),
            vaultModeHolder = mockk<VaultModeHolder>(relaxed = true),
            securePreferences = mockk<SecurePreferences>(relaxed = true),
            preferences = mockk<BatonPreferences>(relaxed = true),
            plainExporter = mockk<PlainExporter>(relaxed = true),
            appContext = appContext,
        )
        return VmMocks(init, auth, realtime, syncEngine, vm)"""

text = text[:m.start()] + new_block + text[m.end():]
out = (b'\xef\xbb\xbf' if has_bom else b'') + text.encode('utf-8')
with open(PATH, 'wb') as f:
    f.write(out)
print(f"Wrote {len(out)} bytes")
