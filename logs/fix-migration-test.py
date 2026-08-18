#!/usr/bin/env python3
"""Fix MigrationUpgradeRegressionTest.kt to use SQLCipher 4.6.1 API.

In v4.6.1, the classes are:
  - net.zetetic.database.sqlcipher.SupportHelper (not SupportSQLiteOpenHelper)
  - net.zetetic.database.sqlcipher.SupportFactory  (not SupportOpenHelperFactory)
"""
import sys
PATH = r'C:\Users\Sampath\.minimax-agent\projects\baton-v2-integration\app\src\test\java\com\baton\app\di\MigrationUpgradeRegressionTest.kt'

with open(PATH, 'rb') as f:
    b = f.read()
has_bom = b.startswith(b'\xef\xbb\xbf')
if has_bom:
    b = b[3:]
text = b.decode('utf-8')

# Replace import
text = text.replace(
    'import net.zetetic.database.sqlcipher.SupportOpenHelperFactory',
    'import net.zetetic.database.sqlcipher.SupportFactory\nimport net.zetetic.database.sqlcipher.SupportHelper',
)

# Replace class usage
text = text.replace('SupportOpenHelperFactory(', 'SupportFactory(')
text = text.replace('SupportSQLiteOpenHelper.Configuration.builder', 'SupportHelper.Configuration.builder')

out = (b'\xef\xbb\xbf' if has_bom else b'') + text.encode('utf-8')
with open(PATH, 'wb') as f:
    f.write(out)
print(f"Wrote {len(out)} bytes")
