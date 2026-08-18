import re
# Step 1: read HEAD's file
p = r'app\src\main\java\com\baton\app\ui\settings\SettingsViewModel.kt'
with open(p, 'rb') as f:
    raw = f.read()
bom = b'\xef\xbb\xbf'
if raw.startswith(bom):
    raw = raw[3:]
content = raw.decode('utf-8')
lines = content.splitlines(keepends=True)
# Find SECOND package, take from there
pkg_idxs = [i for i, l in enumerate(lines) if l.startswith('package ')]
assert len(pkg_idxs) == 2, f'expected 2 packages, got {len(pkg_idxs)}'
keep = lines[pkg_idxs[1]:]
content2 = ''.join(keep)
# Strip LLM imports
content2 = re.sub(r'^    import com\.baton\.app\.ai\.llama\.ModelManager\n', '', content2, flags=re.MULTILINE)
content2 = re.sub(r'^    import com\.baton\.app\.ai\.llama\.ModelState\n', '', content2, flags=re.MULTILINE)
content2 = re.sub(r'^    import com\.baton\.app\.ai\.whisper\.WhisperModelManager\n', '', content2, flags=re.MULTILINE)
# Strip LLM constructor params + comment block.
# Pattern: '    // v1.5.4: model download surfaces.' through 'private val whisperModelManager: WhisperModelManager,\n'
content2 = re.sub(
    r'    // v1\.5\.4: model download surfaces\..*?private val whisperModelManager: WhisperModelManager,\n',
    '',
    content2, flags=re.DOTALL)
# Now strip the LLM StateFlows + download methods.
# The LLM block is between 'val llmModelState: StateFlow<ModelState> = modelManager.state' and the end of downloadWhisper().
# Find the start line (the line BEFORE the /** doc opener, or just the val line)
new_lines = content2.splitlines(keepends=True)
removed_count = 0
i = 0
while i < len(new_lines):
    if 'val llmModelState: StateFlow<ModelState> = modelManager.state' in new_lines[i]:
        # Walk back to /** doc opener
        start = i
        for j in range(i-1, -1, -1):
            if new_lines[j].strip() == '/**':
                start = j
                break
        # Walk forward to find 'fun downloadWhisper()' and its matching }
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
        removed_count += 1
        i = start
    else:
        i += 1
content2 = ''.join(new_lines)
with open(p, 'wb') as f:
    f.write(bom + content2.encode('utf-8'))
print('removed', removed_count, 'LLM blocks')
print('final char count:', len(content2))
