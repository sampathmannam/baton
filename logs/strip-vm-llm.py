import re
p = r'app\src\main\java\com\baton\app\ui\settings\SettingsViewModel.kt'
with open(p, 'r', encoding='utf-8') as f:
    content = f.read()
# 1. Remove LLM imports
content = re.sub(r'import com\.baton\.app\.ai\.llama\.ModelManager\n', '', content)
content = re.sub(r'import com\.baton\.app\.ai\.llama\.ModelState\n', '', content)
content = re.sub(r'import com\.baton\.app\.ai\.whisper\.WhisperModelManager\n', '', content)
# 2. Remove the LLM constructor params + their comment
content = re.sub(
    r'\s*// v1\.5\.4: model download surfaces.*?private val modelManager: ModelManager,\n\s*private val whisperModelManager: WhisperModelManager,\n',
    '\n',
    content, flags=re.DOTALL)
# 3. Remove the LLM StateFlows + download methods
# Block 1: llmModelState
content = re.sub(
    r'\s*/\*\*\n     \* v1\.5\.4: the on-device LLM model state.*?\n     \*/\n    val llmModelState: StateFlow<ModelState> = modelManager\.state\n',
    '', content, flags=re.DOTALL)
# Block 2: llmDownloadProgress
content = re.sub(
    r'\s*/\*\*\n     \* Tier 0\.5: the LLM download progress.*?\n     \*/\n    val llmDownloadProgress: StateFlow<Float> = modelManager\.progress\n',
    '', content, flags=re.DOTALL)
# Block 3: whisperAvailable + private _whisperAvailable
content = re.sub(
    r'\s*/\*\*\n     \* v1\.5\.4: the Whisper model availability.*?available\n     \*/\n    private val _whisperAvailable = MutableStateFlow\(whisperModelManager\.isAvailable\(\)\)\n    val whisperAvailable: StateFlow<Boolean> = _whisperAvailable\.asStateFlow\(\)\n',
    '', content, flags=re.DOTALL)
# Block 4: downloadLlm method
content = re.sub(
    r'\s*/\*\*\n     \* v1\.5\.4: kick off the LLM download.*?ready\)\n     \*/\n    fun downloadLlm\(\) \{\n        modelManager\.ensureModel\(\)\n        modelManager\.download\(\)\n    \}\n',
    '', content, flags=re.DOTALL)
# Block 5: downloadWhisper method (with its _whisperAvailable.value update)
content = re.sub(
    r'\s*/\*\*\n     \* v1\.5\.4: kick off the Whisper download.*?post-download state\.\n     \*/\n    fun downloadWhisper\(\) \{\n        viewModelScope\.launch \{\n            runCatching \{\n                whisperModelManager\.downloadModel\(\)\.collect \{ /\* progress \*/ \}\n            \}\n            // Recompute after the flow terminates .*\n            _whisperAvailable\.value = whisperModelManager\.isAvailable\(\)\n        \}\n    \}\n',
    '', content, flags=re.DOTALL)
with open(p, 'w', encoding='utf-8') as f:
    f.write(content)
print('Done. New size:', len(content))
