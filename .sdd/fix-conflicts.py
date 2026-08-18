import re, os

# Files with leftover conflict markers
files = [
    'app/src/main/java/com/baton/app/MainActivity.kt',
    'app/src/main/java/com/baton/app/data/local/AppDatabase.kt',
    'app/src/main/java/com/baton/app/data/local/InstructionDao.kt',
    'app/src/main/java/com/baton/app/data/local/entities/InstructionEntity.kt',
    'app/src/main/java/com/baton/app/data/local/entities/PersonEntity.kt',
    'app/src/main/java/com/baton/app/di/DatabaseModule.kt',
    'app/src/main/java/com/baton/app/ui/settings/SettingsSheet.kt',
    'app/src/main/java/com/baton/app/ui/settings/SettingsViewModel.kt',
    'app/src/main/res/values/strings.xml',
    'app/src/test/java/com/baton/app/ui/settings/SettingsViewModelTest.kt',
    'app/src/main/java/com/baton/app/ui/today/TodayScreen.kt',
]

for f in files:
    if not os.path.exists(f):
        print(f'  missing: {f}')
        continue
    with open(f) as fh:
        c = fh.read()
    orig = c
    # Strip any line that is just a conflict marker
    lines = c.split('\n')
    cleaned = []
    for line in lines:
        s = line.strip()
        if s in ('<<<<<<< HEAD', '<<<<<<< m0/skeleton-v2-survival',
                 '<<<<<<< m0/skeleton-v2-moat', '<<<<<<< m0/skeleton-v2-privacy',
                 '<<<<<<< m0/skeleton-v2-cleanup',
                 '=======',
                 '>>>>>>> m0/skeleton-v2-cleanup',
                 '>>>>>>> m0/skeleton-v2-survival',
                 '>>>>>>> m0/skeleton-v2-moat',
                 '>>>>>>> m0/skeleton-v2-privacy',
                 '||||||| merged common ancestors'):
            continue
        cleaned.append(line)
    c = '\n'.join(cleaned)
    # Remove the doubled-blank-line effect of consecutive removals
    c = re.sub(r'\n{3,}', '\n\n', c)
    if c != orig:
        with open(f, 'w') as fh:
            fh.write(c)
        print(f'  cleaned {f}')
    else:
        print(f'  no change: {f}')
