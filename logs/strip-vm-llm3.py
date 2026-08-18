import re
p = r'app\src\main\java\com\baton\app\ui\settings\SettingsViewModel.kt'
with open(p, 'r', encoding='utf-8') as f:
    content = f.read()
# Find all instances of the LLM block pattern
# The block starts with "v1.5.4: the on-device LLM model state" and ends with the close of "fun downloadWhisper()"
# Let me match more broadly
pattern = re.compile(
    r'\n\s*/\*\*\n'
    r'(?:\s*\*[^\n]*\n)+?'
    r'\s*\* v1\.5\.4: the on-device LLM model state.*?'
    r'\s*\*\s*/\n'
    r'\s*val llmModelState.*?\n'
    r'(?:\s*/\*\*\n(?:\s*\*[^\n]*\n)+?\s*\*\s*/\n\s*val llmDownloadProgress.*?\n)?'
    r'(?:\s*/\*\*\n(?:\s*\*[^\n]*\n)+?\s*\*\s*/\n\s*private val _whisperAvailable.*?\n\s*val whisperAvailable.*?\n)?'
    r'\s*/\*\*\n\s*\*\n\s*\* v1\.5\.4: kick off the LLM download.*?\n\s*fun downloadLlm\(\).*?\n\s*\}\n'
    r'\s*/\*\*\n\s*\*\n\s*\* v1\.5\.4: kick off the Whisper download.*?\n\s*fun downloadWhisper\(\).*?\n\s*\}\n',
    re.DOTALL
)
matches = pattern.findall(content)
print(f'Found {len(matches)} LLM blocks')
for m in matches:
    print('---')
    print(m[:200])
    print('...')
# Remove all matches
new_content = pattern.sub('\n', content)
print(f'Removed {len(content) - len(new_content)} chars')
with open(p, 'w', encoding='utf-8') as f:
    f.write(new_content)
print(f'New size: {len(new_content)}')
