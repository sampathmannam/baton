with open(r'app\src\main\java\com\baton\app\data\auth/SecurePreferences.kt', encoding='utf-8') as f:
    text = f.read()

# Add IdentityCrypto import if not present
if 'IdentityCrypto' not in text:
    text = text.replace('import ', 'import com.baton.app.data.vault.IdentityCrypto\nimport ', 1)

# Add the constants
consts = '''    private val KEY_VAULT_PIN_HASH = "vault_pin_hash"
    private val KEY_RECOVERY_PHRASE_HASH = "recovery_phrase_hash"
'''
if 'KEY_VAULT_PIN_HASH' not in text:
    # Insert after the prefs declaration
    text = text.replace('private val prefs = EncryptedSharedPreferences',
                         'private val prefs = EncryptedSharedPreferences\n' + consts)

# Add the methods before the class close
addition = '''
    fun pinMatches(pin: String): Boolean {
        val stored = prefs.getString(KEY_VAULT_PIN_HASH, null) ?: return false
        return IdentityCrypto.sha256Hex(pin) == stored
    }
    fun clearVaultPinHash() {
        prefs.edit().remove(KEY_VAULT_PIN_HASH).apply()
    }
    fun recoveryPhraseHash(): String? = prefs.getString(KEY_RECOVERY_PHRASE_HASH, null)
    fun setRecoveryPhraseHash(hash: String) {
        prefs.edit().putString(KEY_RECOVERY_PHRASE_HASH, hash).apply()
    }
'''
last_brace = text.rfind('}')
text = text[:last_brace] + addition + '\n' + text[last_brace:]

with open(r'app\src\main\java\com\baton\app\data\auth/SecurePreferences.kt', 'w', encoding='utf-8') as f:
    f.write(text)
print('Added methods to SecurePreferences')
