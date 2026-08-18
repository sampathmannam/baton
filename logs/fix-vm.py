import re
p = r'app\src\main\java\com\baton\app\ui\settings\SettingsViewModel.kt'
with open(p, 'rb') as f:
    raw = f.read()
# Strip BOM if present
bom = b'\xef\xbb\xbf'
if raw.startswith(bom):
    raw = raw[3:]
content = raw.decode('utf-8')
lines = content.splitlines(keepends=True)
# Find all 'package ' declarations
pkg_idxs = [i for i, l in enumerate(lines) if l.startswith('package ')]
print('package lines:', [i+1 for i in pkg_idxs])
# Keep from the SECOND package to end (second copy has vault)
second_pkg = pkg_idxs[1]
# Find end of second copy: scan for any "package" that might be after
keep = lines[second_pkg:]
# Strip the LLM block from the second copy
content2 = ''.join(keep)
# Remove the LLM imports
content2 = re.sub(r'^import com\.baton\.app\.ai\.llama\.ModelManager\n', '', content2, flags=re.MULTILINE)
content2 = re.sub(r'^import com\.baton\.app\.ai\.llama\.ModelState\n', '', content2, flags=re.MULTILINE)
content2 = re.sub(r'^import com\.baton\.app\.ai\.whisper\.WhisperModelManager\n', '', content2, flags=re.MULTILINE)
# Remove the LLM constructor params + comment block
# The pattern: from "// v1.5.4: model download surfaces" through "private val whisperModelManager: WhisperModelManager,"
content2 = re.sub(
    r'\n    // v1\.5\.4: model download surfaces.*?private val whisperModelManager: WhisperModelManager,\n',
    '\n',
    content2, flags=re.DOTALL)
# Remove the LLM StateFlows + download methods
# Block: llmModelState
content2 = re.sub(
    r'\n    /\*\*\n     \* v1\.5\.4: the on-device LLM model state.*?\n     \*/\n    val llmModelState: StateFlow<ModelState> = modelManager\.state\n',
    '\n', content2, flags=re.DOTALL)
# Block: llmDownloadProgress
content2 = re.sub(
    r'\n    /\*\*\n     \* Tier 0\.5: the LLM download progress.*?\n     \*/\n    val llmDownloadProgress: StateFlow<Float> = modelManager\.progress\n',
    '\n', content2, flags=re.DOTALL)
# Block: _whisperAvailable + whisperAvailable (start of LLM)
# This is harder because the docstring is large. Let me match just the field decls.
content2 = re.sub(
    r'\n    /\*\*\n     \* v1\.5\.4: the Whisper model availability.*?\n     \*/\n    private val _whisperAvailable = MutableStateFlow\(whisperModelManager\.isAvailable\(\)\)\n    val whisperAvailable: StateFlow<Boolean> = _whisperAvailable\.asStateFlow\(\)\n',
    '\n', content2, flags=re.DOTALL)
# Block: downloadLlm
content2 = re.sub(
    r'\n    /\*\*\n     \* v1\.5\.4: kick off the LLM download.*?\n     \*/\n    fun downloadLlm\(\) \{\n        modelManager\.ensureModel\(\)\n        modelManager\.download\(\)\n    \}\n',
    '\n', content2, flags=re.DOTALL)
# Block: downloadWhisper
content2 = re.sub(
    r'\n    /\*\*\n     \* v1\.5\.4: kick off the Whisper download.*?\n     \*/\n    fun downloadWhisper\(\) \{\n        viewModelScope\.launch \{\n            runCatching \{\n                whisperModelManager\.downloadModel\(\)\.collect \{ /\* progress \*/ \}\n            \}\n            // Recompute after the flow terminates .*\n            _whisperAvailable\.value = whisperModelManager\.isAvailable\(\)\n        \}\n    \}\n',
    '\n', content2, flags=re.DOTALL)
# Write back
with open(p, 'wb') as f:
    f.write(bom + content2.encode('utf-8'))
print('final size:', len(content2), 'chars')
