#!/usr/bin/env python3
"""Fix all test files broken by v1.6.1 LLM removal and constructor changes.

Files:
  - SettingsViewModelTest.kt: remove modelManager/whisperModelManager
    references, add vaultModeHolder/securePreferences/preferences/plainExporter
    mocks, fix duplicate appContext
  - SettingsVaultPinTest.kt: remove modelManager/whisperModelManager
    references, add preferences/plainExporter mocks
  - AdhdUxFindingTests.kt: remove CaptureProcessor reference
  - WorryBoxViewModelTest.kt: check FTS dao issue
"""
import re
import sys

BASE = r'C:\Users\Sampath\.minimax-agent\projects\baton-v2-integration\app\src\test\java\com\baton\app'


def read_text(path):
    with open(path, 'rb') as f:
        b = f.read()
    has_bom = b.startswith(b'\xef\xbb\xbf')
    if has_bom:
        b = b[3:]
    return b.decode('utf-8'), has_bom


def write_text(path, text, has_bom):
    out = (b'\xef\xbb\xbf' if has_bom else b'') + text.encode('utf-8')
    with open(path, 'wb') as f:
        f.write(out)


# --- 1) SettingsViewModelTest.kt
PATH = BASE + r'\ui\settings\SettingsViewModelTest.kt'
text, has_bom = read_text(PATH)

# Remove the modelManager + whisperManager val declarations (lines 77-78)
text = re.sub(
    r'\s*// v1\.5\.4: relaxed mocks for the two model managers[\s\S]*?val whisperManager = mockk<com\.baton\.app\.ai\.whisper\.WhisperModelManager>\(relaxed = true\)\n',
    '\n',
    text,
    count=1,
)

# Add vaultModeHolder and securePreferences mocks after the tagDao line
# The pattern: tagDao = mockk<TagDao>(relaxed = true),\n            modelManager = modelManager,
# Replace it with: tagDao = mockk<TagDao>(relaxed = true),\n            vaultModeHolder = mockk<VaultModeHolder>(relaxed = true),\n            securePreferences = mockk<SecurePreferences>(relaxed = true),\n            preferences = mockk<BatonPreferences>(relaxed = true),\n            plainExporter = mockk<PlainExporter>(relaxed = true),\n

# Actually, let me match the entire VM constructor block and rewrite it
# The block: vm = SettingsViewModel(\n ... \n)
# Find it and replace
old_ctor = re.search(
    r'(val vm = SettingsViewModel\()([\s\S]*?)(\n        \))',
    text)
if old_ctor is None:
    print("FATAL: cannot find SettingsViewModel constructor call", file=sys.stderr)
    sys.exit(1)

new_ctor_body = '''        authRepository = auth,
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
        appContext = mockk<android.content.Context>(relaxed = true),'''

text = text[:old_ctor.start()] + new_ctor_body + text[old_ctor.end():]

# Add missing imports
needed_imports = [
    'import com.baton.app.data.auth.SecurePreferences',
    'import com.baton.app.data.export.PlainExporter',
    'import com.baton.app.data.preferences.BatonPreferences',
    'import com.baton.app.data.vault.VaultModeHolder',
]
imports_to_add = [imp for imp in needed_imports if imp not in text]
if imports_to_add:
    # Insert after last import
    last_import = re.search(r'^import .+', text, re.MULTILINE)
    if last_import:
        ip = text.rfind('\n', 0, last_import.end()) + 1
        eol = '\r\n' if '\r\n' in text else '\n'
        text = text[:ip] + eol.join(imports_to_add) + eol + text[ip:]

write_text(PATH, text, has_bom)
print(f"Wrote {PATH}")


# --- 2) SettingsVaultPinTest.kt
PATH = BASE + r'\ui\settings\SettingsVaultPinTest.kt'
text, has_bom = read_text(PATH)

# Remove modelManager + whisperManager val declarations
text = re.sub(
    r'\s*val modelManager = mockk<com\.baton\.app\.ai\.llama\.ModelManager>\(relaxed = true\)\n',
    '\n', text)
text = re.sub(
    r'\s*val whisperManager = mockk<com\.baton\.app\.ai\.whisper\.WhisperModelManager>\(relaxed = true\)\n',
    '\n', text)

# In the SettingsViewModel constructor call, remove modelManager and whisperModelManager
text = re.sub(
    r'\s*modelManager = modelManager,\n',
    '\n', text)
text = re.sub(
    r'\s*whisperModelManager = whisperManager,\n',
    '\n', text)

# Add preferences + plainExporter
text = re.sub(
    r'(\s*vaultModeHolder = vaultModeHolder,\n\s*securePreferences = securePreferences,)\n',
    r'\1\n        preferences = mockk<com.baton.app.data.preferences.BatonPreferences>(relaxed = true),\n        plainExporter = mockk<com.baton.app.data.export.PlainExporter>(relaxed = true),',
    text, count=1)

write_text(PATH, text, has_bom)
print(f"Wrote {PATH}")


# --- 3) AdhdUxFindingTests.kt
PATH = BASE + r'\features\adhd\AdhdUxFindingTests.kt'
text, has_bom = read_text(PATH)

# Find CaptureProcessor reference and replace with stub
# Original: val processor = com.baton.app.features.capture.CaptureProcessor { null }
# The CaptureProcessor was the LLM extraction step. Since LLM is removed, comment it out.
text = re.sub(
    r'val processor = com\.baton\.app\.features\.capture\.CaptureProcessor \{ null \}',
    '// v1.6.1: CaptureProcessor removed (LLM drop). Stub the variable.\n        val processor: (() -> Any?)? = null',
    text)

write_text(PATH, text, has_bom)
print(f"Wrote {PATH}")

# --- 4) WorryBoxViewModelTest.kt - check what the ftsDao issue is
PATH = BASE + r'\ui\today\worry\WorryBoxViewModelTest.kt'
text, has_bom = read_text(PATH)
# Check imports
print(f"\n--- WorryBoxViewModelTest imports ---")
for line in text.split('\n')[:35]:
    if line.startswith('import'):
        print(line)
