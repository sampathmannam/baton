#!/usr/bin/env python3
"""
Restore themeMode, setThemeMode, exportPlain, computeStorageSizeBytes,
StorageInfo.sizeBytes, and the new `val storage` block from v1.6.0.1 HEAD
into the current SettingsViewModel.kt that was over-stripped during LLM removal.

Uses hardcoded 1-indexed line ranges from the v1.6.0.1 file (logs/svm-v1601.kt).
"""
import os
import re
import sys

V1601_PATH = r'C:\Users\Sampath\.minimax-agent\projects\baton-v2-integration\logs\svm-v1601.kt'
CURRENT_PATH = r'C:\Users\Sampath\.minimax-agent\projects\baton-v2-integration\app\src\main\java\com\baton\app\ui\settings\SettingsViewModel.kt'


def read_text(path):
    with open(path, 'rb') as f:
        b = f.read()
    if b.startswith(b'\xef\xbb\xbf'):
        b = b[3:]
    return b.decode('utf-8')


def read_lines(path):
    return read_text(path).split('\n')


def extract_lines(lines, start, end):
    """Return lines[start-1:end] joined with newlines. 1-indexed inclusive."""
    return '\n'.join(lines[start-1:end])


v1601_lines = read_lines(V1601_PATH)
current = read_text(CURRENT_PATH)

# 1) Extract blocks from v1.6.0.1
# Lines identified manually by reading the file:
#   - storage val block: lines 122-158 (with doc)
#   - themeMode val block: lines 279-292 (with doc + .stateIn)
#   - setThemeMode: lines 294-296
#   - exportPlain: lines 298-316
#   - computeStorageSizeBytes: lines 336-371
#   - StorageInfo: lines 374-389 (with doc)
storage_v1601 = extract_lines(v1601_lines, 122, 158) + '\n'
theme_mode_v1601 = extract_lines(v1601_lines, 279, 292) + '\n'
set_theme_mode_v1601 = extract_lines(v1601_lines, 294, 296) + '\n'
export_plain_v1601 = extract_lines(v1601_lines, 298, 316) + '\n'
compute_size_v1601 = extract_lines(v1601_lines, 336, 371) + '\n'
storage_info_v1601 = extract_lines(v1601_lines, 374, 389) + '\n'

print(f"storage block: {len(storage_v1601)} chars")
print(f"themeMode block: {len(theme_mode_v1601)} chars")
print(f"setThemeMode block: {len(set_theme_mode_v1601)} chars")
print(f"exportPlain block: {len(export_plain_v1601)} chars")
print(f"computeStorageSizeBytes block: {len(compute_size_v1601)} chars")
print(f"StorageInfo block: {len(storage_info_v1601)} chars")

# 2) Patch current file
# --- a) Imports
needed_imports = [
    'import android.content.Context',
    'import android.net.Uri',
    'import com.baton.app.data.export.PlainExporter',
    'import com.baton.app.data.local.AppDatabase',
    'import com.baton.app.data.preferences.BatonPreferences',
    'import com.baton.app.data.preferences.ThemeMode',
    'import dagger.hilt.android.qualifiers.ApplicationContext',
    'import kotlinx.coroutines.Dispatchers',
    'import kotlinx.coroutines.flow.flowOn',
    'import kotlinx.coroutines.withContext',
]
imports_to_add = []
for imp in needed_imports:
    if imp not in current:
        imports_to_add.append(imp)
if imports_to_add:
    last_import = re.search(r'^import .+', current, re.MULTILINE)
    if last_import:
        insertion_point = current.rfind('\n', 0, last_import.end()) + 1
        current = current[:insertion_point] + '\n'.join(imports_to_add) + '\n' + current[insertion_point:]
    print(f"  + added {len(imports_to_add)} imports")

# --- b) Inject dependencies into constructor
# Add after `private val securePreferences: SecurePreferences,`:
#   - private val preferences: BatonPreferences,
#   - private val plainExporter: PlainExporter,
#   - @ApplicationContext private val appContext: Context,
ctor_marker = 'private val securePreferences: SecurePreferences,'
if ctor_marker in current and 'private val preferences: BatonPreferences' not in current:
    deps = (
        'private val preferences: BatonPreferences,\n'
        '    private val plainExporter: PlainExporter,\n'
        '    @ApplicationContext private val appContext: Context,'
    )
    new_ctor = current.replace(ctor_marker, ctor_marker + '\n    ' + deps)
    if new_ctor == current:
        print("FATAL: cannot find securePreferences injection point", file=sys.stderr)
        sys.exit(1)
    current = new_ctor
    print("  + injected preferences/plainExporter/appContext into constructor")

# --- c) Replace `val storage` block (current has it but missing sizeBytes)
old_storage_m = re.search(
    r'val storage: StateFlow<StorageInfo> = combine\([\s\S]*?initialValue = StorageInfo\(\),',
    current)
if old_storage_m is None:
    print("FATAL: cannot find current `val storage` block", file=sys.stderr)
    sys.exit(1)
old_end = old_storage_m.end()
while old_end < len(current) and current[old_end] in ' \t\n':
    old_end += 1
if old_end < len(current) and current[old_end] == ')':
    old_end += 1
# Replace; preserve the trailing blank line
current = current[:old_storage_m.start()] + storage_v1601.rstrip() + '\n\n' + current[old_end:].lstrip('\n')
print("  + replaced val storage with sizeBytes computation")

# --- d) Replace `data class StorageInfo` block
old_storage_info_m = re.search(
    r'data class StorageInfo\([^)]*\)',
    current)
if old_storage_info_m is None:
    print("FATAL: cannot find current StorageInfo", file=sys.stderr)
    sys.exit(1)
# Find the doc comment ABOVE the data class
doc_start = old_storage_info_m.start()
# Walk back to find `/**`
k = doc_start
while k > 0:
    nl = current.rfind('\n', 0, k)
    if nl == -1:
        break
    line = current[nl+1:k]
    if line.strip().endswith('*/'):
        # Find `/**` in this line
        idx = current.rfind('/**', 0, k)
        if idx != -1:
            doc_start = idx
            break
        else:
            break
    elif line.strip() == '':
        k = nl  # skip blank line, keep looking
        continue
    else:
        break

current = current[:doc_start] + storage_info_v1601.rstrip() + '\n\n' + current[old_storage_info_m.end():]
print("  + replaced StorageInfo data class")

# --- e) Add new methods before `private fun isValidPin`
m_anchor = re.search(r'\n    private fun isValidPin', current)
if m_anchor is None:
    print("FATAL: cannot find isValidPin anchor", file=sys.stderr)
    sys.exit(1)

methods_to_add = (
    theme_mode_v1601.rstrip() + '\n\n'
    + set_theme_mode_v1601.rstrip() + '\n\n'
    + export_plain_v1601.rstrip() + '\n\n'
    + compute_size_v1601.rstrip() + '\n\n'
)
current = current[:m_anchor.start()] + '\n' + methods_to_add + current[m_anchor.start()+1:]
print(f"  + added {len(methods_to_add)} bytes of new methods")

# --- f) Save
with open(CURRENT_PATH, 'wb') as f:
    f.write(b'\xef\xbb\xbf' + current.encode('utf-8'))
print(f"\nWROTE: {CURRENT_PATH}")
print(f"  size: {len(current)} chars / {len(current.encode('utf-8'))} bytes")
