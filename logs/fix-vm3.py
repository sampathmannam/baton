import re
p = r'app\src\main\java\com\baton\app\ui\settings\SettingsViewModel.kt'
with open(p, 'rb') as f:
    raw = f.read()
bom = b'\xef\xbb\xbf'
if raw.startswith(bom):
    raw = raw[3:]
content = raw.decode('utf-8')
# Strip LLM imports
content = re.sub(r'^    import com\.baton\.app\.ai\.llama\.ModelManager\n', '', content, flags=re.MULTILINE)
content = re.sub(r'^    import com\.baton\.app\.ai\.llama\.ModelState\n', '', content, flags=re.MULTILINE)
content = re.sub(r'^    import com\.baton\.app\.ai\.whisper\.WhisperModelManager\n', '', content, flags=re.MULTILINE)
# Strip LLM constructor params + comment block.
content = re.sub(
    r'    // v1\.5\.4: model download surfaces\..*?private val whisperModelManager: WhisperModelManager,\n',
    '',
    content, flags=re.DOTALL)
# Strip LLM StateFlows + download methods.
new_lines = content.splitlines(keepends=True)
i = 0
while i < len(new_lines):
    if 'val llmModelState: StateFlow<ModelState> = modelManager.state' in new_lines[i]:
        start = i
        for j in range(i-1, -1, -1):
            if new_lines[j].strip() == '/**':
                start = j
                break
        end = None
        for k in range(i+1, len(new_lines)):
            if 'fun downloadWhisper()' in new_lines[k]:
                depth = 0
                for m in range(k, len(new_lines)):
                    for ch in new_lines[m]:
                        if ch == '{': depth += 1
                        elif ch == '}':
                            depth -= 1
                            if depth == 0:
                                end = m + 1
                                break
                    if end is not None:
                        break
                break
        if end is None:
            end = len(new_lines)
        print(f'removing LLM block lines {start+1} to {end}')
        del new_lines[start:end]
        i = start
    else:
        i += 1
content = ''.join(new_lines)
with open(p, 'wb') as f:
    f.write(bom + content.encode('utf-8'))
print('final char count:', len(content))
