#!/usr/bin/env python3
"""Remove orphan modelManager / whisperManager declarations from test files."""
import os

TARGETS = [
    r'C:\Users\Sampath\.minimax-agent\projects\baton-v2-integration\app\src\test\java\com\baton\app\ui\settings\SettingsViewModelTest.kt',
    r'C:\Users\Sampath\.minimax-agent\projects\baton-v2-integration\app\src\test\java\com\baton\app\ui\settings\SettingsVaultPinTest.kt',
]

NEEDLES = [
    '        val modelManager = mockk<com.baton.app.ai.llama.ModelManager>(relaxed = true)\r\n',
    '        val modelManager = mockk<com.baton.app.ai.llama.ModelManager>(relaxed = true)\n',
    '        val whisperManager = mockk<com.baton.app.ai.whisper.WhisperModelManager>(relaxed = true)\r\n',
    '        val whisperManager = mockk<com.baton.app.ai.whisper.WhisperModelManager>(relaxed = true)\n',
]

for path in TARGETS:
    if not os.path.exists(path):
        print(f"SKIP: {path} (not found)")
        continue
    with open(path, 'rb') as f:
        b = f.read()
    has_bom = b.startswith(b'\xef\xbb\xbf')
    if has_bom:
        b = b[3:]
    text = b.decode('utf-8')

    before = len(text)
    for needle in NEEDLES:
        text = text.replace(needle, '')
    after = len(text)
    out = (b'\xef\xbb\xbf' if has_bom else b'') + text.encode('utf-8')
    with open(path, 'wb') as f:
        f.write(out)
    print(f"{path}: removed {before - after} chars (now {len(out)} bytes)")
