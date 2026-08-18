#!/usr/bin/env python3
"""Fix MigrationUpgradeRegressionTest.kt to use SQLCipher 4.6.1 API.

In v4.6.1:
  - net.zetetic.database.sqlcipher.SupportOpenHelperFactory
    .create(androidx.sqlite.db.SupportSQLiteOpenHelper.Configuration)
  - The Configuration class is androidx.sqlite.db.SupportSQLiteOpenHelper.Configuration
  - helper.writableDatabase returns androidx.sqlite.db.SupportSQLiteDatabase
"""
import sys
PATH = r'C:\Users\Sampath\.minimax-agent\projects\baton-v2-integration\app\src\test\java\com\baton\app\di\MigrationUpgradeRegressionTest.kt'

with open(PATH, 'rb') as f:
    b = f.read()
has_bom = b.startswith(b'\xef\xbb\xbf')
if has_bom:
    b = b[3:]
text = b.decode('utf-8')

# Revert the import from my earlier wrong change
text = text.replace(
    'import net.zetetic.database.sqlcipher.SupportFactory\nimport net.zetetic.database.sqlcipher.SupportHelper',
    'import net.zetetic.database.sqlcipher.SupportOpenHelperFactory'
)

# Fix the class usage: SupportFactory -> SupportOpenHelperFactory
text = text.replace('SupportFactory(', 'SupportOpenHelperFactory(')

# Fix the Configuration reference: net.zetetic.database.sqlcipher.SupportHelper.Configuration
# -> androidx.sqlite.db.SupportSQLiteOpenHelper.Configuration
text = text.replace(
    'net.zetetic.database.sqlcipher.SupportHelper.Configuration.builder',
    'androidx.sqlite.db.SupportSQLiteOpenHelper.Configuration.builder'
)

# Add the import
if 'import androidx.sqlite.db.SupportSQLiteOpenHelper' not in text:
    # Insert after the last import
    lines = text.split('\n')
    last_import = -1
    for i, line in enumerate(lines):
        if line.startswith('import'):
            last_import = i
    lines.insert(last_import + 1, 'import androidx.sqlite.db.SupportSQLiteOpenHelper')
    text = '\n'.join(lines)

out = (b'\xef\xbb\xbf' if has_bom else b'') + text.encode('utf-8')
with open(PATH, 'wb') as f:
    f.write(out)
print(f"Wrote {len(out)} bytes")
