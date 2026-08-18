p = r'app\src\main\java\com\baton\app\ui\settings\SettingsViewModel.kt'
with open(p, 'r', encoding='utf-8') as f:
    lines = f.readlines()
print(f'starting line count: {len(lines)}')

# Find all `/**` followed within 5 lines by "v1.5.4:" marker that starts the LLM block
# The LLM block:
#   /** ... (some comment) ...
#    * v1.5.4: ... (some text) ...
#    */
#   private val _whisperAvailable = MutableStateFlow(whisperModelManager.isAvailable())
#   val whisperAvailable: StateFlow<Boolean> = _whisperAvailable.asStateFlow()
#   ...
#   fun downloadWhisper() {
#       viewModelScope.launch {
#           ...
#       }
#   }

# Find all comment start lines that are followed by "v1.5.4: the Whisper model availability"
llm_block_starts = []
for i, line in enumerate(lines):
    if 'v1.5.4: the Whisper model availability' in line:
        # walk back to /**
        for j in range(i-1, -1, -1):
            if lines[j].strip() == '/**':
                llm_block_starts.append(j)
                break
        else:
            llm_block_starts.append(i)

print(f'found {len(llm_block_starts)} LLM block starts at: {llm_block_starts}')

# For each LLM block start, find the end (the } of downloadWhisper)
def find_block_end(start):
    # Find fun downloadWhisper
    for k in range(start, len(lines)):
        if 'fun downloadWhisper()' in lines[k]:
            # walk forward to matching }
            depth = 0
            for m in range(k, len(lines)):
                for ch in lines[m]:
                    if ch == '{': depth += 1
                    elif ch == '}':
                        depth -= 1
                        if depth == 0:
                            return m + 1
            return len(lines)
    return start

llm_block_ends = [find_block_end(s) for s in llm_block_starts]
print(f'LLM block ends: {llm_block_ends}')

# Delete all LLM blocks (from end to start so indices stay valid)
for start, end in zip(reversed(llm_block_starts), reversed(llm_block_ends)):
    print(f'deleting lines {start+1} to {end}')
    del lines[start:end]

# Also remove the modelManager/whisperModelManager constructor params if still there
# (the constructor might still reference them)
content = ''.join(lines)
import re
# Remove the LLM constructor params + their comment block
content = re.sub(
    r'\s*// v1\.5\.4: model download surfaces.*?private val modelManager: ModelManager,\n\s*private val whisperModelManager: WhisperModelManager,\n',
    '\n',
    content, flags=re.DOTALL)

# Remove the LLM imports
content = re.sub(r'import com\.baton\.app\.ai\.llama\.ModelManager\n', '', content)
content = re.sub(r'import com\.baton\.app\.ai\.llama\.ModelState\n', '', content)
content = re.sub(r'import com\.baton\.app\.ai\.whisper\.WhisperModelManager\n', '', content)

with open(p, 'w', encoding='utf-8') as f:
    f.write(content)
print(f'final line count: {len(content.splitlines())}')
print(f'final char count: {len(content)}')
