import subprocess
import re

# Get Tier 3's InstructionDao
t3 = subprocess.run(['git', 'show', 'm0/skeleton-v2-privacy:app/src/main/java/com/baton/app/data/local/InstructionDao.kt'],
                    capture_output=True, text=True).stdout
# Get Tier 2's InstructionDao
t2 = subprocess.run(['git', 'show', 'm0/skeleton-v2-moat:app/src/main/java/com/baton/app/data/local/InstructionDao.kt'],
                    capture_output=True, text=True).stdout

t3_methods = set(re.findall(r'fun (\w+)\(', t3))
t2_methods = set(re.findall(r'fun (\w+)\(', t2))
t2_unique = t2_methods - t3_methods
t3_unique = t3_methods - t2_methods
print(f'In T2 but not T3: {sorted(t2_unique)}')
print(f'In T3 but not T2: {sorted(t3_unique)}')

# Strategy: use Tier 2's file as the base (has v1.5.7 + Tier 1 + Tier 2 worry + observeAllInMode),
# and add Tier 3's vaultMode methods
base = t2
# Add setVaultModeForPerson + observeAllInMode from Tier 3 (Tier 2 might have observeAllInMode too)
t3_new = re.findall(r'(    @(?:Query|Update|Insert|Delete)[^@]*?fun (?:' + '|'.join(t3_unique) + r')\([^)]*\)[^{]*\{[^}]*\}\s*\n)', t3, re.DOTALL)
for m in t3_new:
    print('---')
    print(m)
