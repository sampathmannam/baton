import re, subprocess

# Read the current file
with open(r'app\src\main\java/com/baton/app/data/local/InstructionDao.kt') as fh:
    content = fh.read()

# Truncate at the second 'package' line
lines = content.split('\n')
# Find the second 'package com.baton.app.data.local' line
pkg_indices = [i for i, l in enumerate(lines) if l.startswith('package com.baton.app.data.local')]
print(f'Found package lines at: {pkg_indices}')
# Keep everything up to (and not including) the second package line
if len(pkg_indices) >= 2:
    keep_to = pkg_indices[1]
    print(f'Truncating to line {keep_to} (before second package)')
    lines = lines[:keep_to]

# Add Tier 3's vaultMode methods at the end of the interface
t3 = subprocess.run(['git', 'show', 'm0/skeleton-v2-privacy:app/src/main/java/com/baton/app/data/local/InstructionDao.kt'],
                    capture_output=True, text=True).stdout
# Find the new methods
t3_methods_to_add = re.findall(
    r'(    /\*\*\n     \* v2\.0 T3-1.*?\*/\n    @Query[^@]*?fun (?:observeAllInMode|setVaultModeForPerson)[^}]*\}\s*\n)',
    t3, re.DOTALL)
for m in t3_methods_to_add:
    lines.append('')
    lines.extend(m.split('\n'))

# Add closing brace and data classes (these were already truncated, so we need to keep them)
# Actually the data classes were at lines 153-166 in the original; they should still be there if we truncated at the second package line.
# But we need to make sure the file has the right structure.
# Let's just write the result and check
out = '\n'.join(lines)
if not out.rstrip().endswith('}'):
    # Need to add closing brace for the data class
    if 'data class' in out and not out.rstrip().endswith('}'):
        # The data classes were truncated — we may have lost them
        pass

with open(r'app/src/main/java/com/baton/app/data/local/InstructionDao.kt', 'w') as fh:
    fh.write(out)
print(f'Wrote {len(lines)} lines')
