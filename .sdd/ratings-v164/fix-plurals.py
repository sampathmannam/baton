"""
v1.6.4: Fix 6 other count+noun strings that have the same plural bug
as the T41 fix in v1.6.3-r2.

For each, we replace the <string> with a <plurals> block (one + other
quantities), keeping the same `name` attribute so existing call sites
continue to compile. Call sites need to be updated from
`stringResource(R.string.X, count)` to
`pluralStringResource(R.plurals.X, count, count)`.
"""

import re

PATH = 'app/src/main/res/values/strings.xml'

with open(PATH, 'r', encoding='utf-8') as f:
    content = f.read()

# The 6 fixes: (old_string, new_plurals_block)
# Note: the plurals XML uses the same name as the original string, so
# the resource id (R.string.X vs R.plurals.X) changes — call sites
# need to be updated.
fixes = [
    # decay_days_quiet: "haven't touched in %1$d days"
    #   → "haven't touched in 1 day" / "haven't touched in %d days"
    (
        '<string name="decay_days_quiet">haven\\\'t touched in %1$d days</string>',
        '''<plurals name="decay_days_quiet">
        <item quantity="one">haven\\\'t touched in %1$d day</item>
        <item quantity="other">haven\\\'t touched in %1$d days</item>
    </plurals>'''
    ),
    # morning_brief_carried_over: "%1$d carried over"
    (
        '<string name="morning_brief_carried_over">%1$d carried over</string>',
        '''<plurals name="morning_brief_carried_over">
        <item quantity="one">%1$d carried over</item>
        <item quantity="other">%1$d carried over</item>
    </plurals>'''
    ),
    # todays_win_summary: "%1$d captures across %2$d people, %3$d carried over, %4$d sensitive."
    # Three of the four numbers are counts of pluralizable nouns.
    # Plurals resources only support ONE count. We replace this with
    # a single number "1 carry-over" / "N carry-overs" via a separate
    # plurals resource and use a stringResource for the outer
    # sentence. BUT the original is a single string; the simplest
    # fix is to keep it as stringResource with `s` suffixes. That
    # changes grammar. Easiest pragmatic fix: use two strings —
    # `todays_win_summary_part1` (with %1$d captures, %2$d people,
    # %4$d sensitive as raw stringResource with s suffix) and a
    # plurals `todays_win_carried_over` for the carried-over count.
    # For now we leave todays_win_summary as-is (would need a larger
    # refactor) and flag it.
    # NOTE: this one is NOT auto-fixed.
    None,
    # bulk_snooze_banner: "%1$d quiet contacts — redistribute?"
    (
        '<string name="bulk_snooze_banner">%1$d quiet contacts — redistribute?</string>',
        '''<plurals name="bulk_snooze_banner">
        <item quantity="one">%1$d quiet contact — redistribute?</item>
        <item quantity="other">%1$d quiet contacts — redistribute?</item>
    </plurals>'''
    ),
    # settings_storage_value: "%1$d people, %2$d instructions, %3$d tags"
    # Three counts in one string — same problem as todays_win_summary.
    # Pragmatic fix: leave as-is and add a comment, OR split into 3
    # sub-strings. For now: leave + flag.
    # NOTE: this one is NOT auto-fixed.
    None,
    # settings_dev_loaded: "Loaded %1$d people, %2$d instructions, %3$d captures, %4$d tags."
    # Same problem.
    # NOTE: this one is NOT auto-fixed.
    None,
]

applied = []
skipped = []
for fix in fixes:
    if fix is None:
        skipped.append("multi-count string (left as-is, flagged for refactor)")
        continue
    old, new = fix
    if old in content:
        content = content.replace(old, new, 1)
        applied.append(old.split('"')[1] if '"' in old else old)
    else:
        # Try with normalized whitespace
        old_normalized = re.sub(r'\s+', ' ', old).strip()
        for m in re.finditer(re.escape(old.split('"')[1]), content):
            line_start = content.rfind('\n', 0, m.start()) + 1
            line_end = content.find('\n', m.end())
            actual_line = content[line_start:line_end]
            print(f'  MISMATCH: expected="{old}"')
            print(f'           actual  ="{actual_line}"')
            break
        else:
            print(f'  NOT FOUND: {old}')

# Write back
with open(PATH, 'w', encoding='utf-8') as f:
    f.write(content)

print(f'\nApplied {len(applied)} fixes:')
for name in applied:
    print(f'  - {name}')
print(f'Skipped {len(skipped)} multi-count strings (flagged for refactor)')
