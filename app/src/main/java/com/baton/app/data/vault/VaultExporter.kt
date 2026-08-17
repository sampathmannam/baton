package com.baton.app.data.vault

import android.content.Context
import android.net.Uri
import com.baton.app.data.local.AppDatabase
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Tier 1.1 (v2.0): exports the local Room DB to a SAF-provided
 * URI, re-encrypted under the user passphrase. The export
 * pipeline is:
 *
 *  1. Generate 16-byte salt + 12-byte IV via [VaultCrypto].
 *  2. Argon2id the passphrase → 32-byte key.
 *  3. WAL-checkpoint the on-disk Room DB (so the file copy is
 *     self-consistent), then read the bytes.
 *  4. Encrypt with AES-256-GCM (AAD = 56-byte header).
 *  5. Write `header || ciphertext || tag` to the SAF output
 *     stream.
 *
 * **Atomicity.** Step 3 runs on [Dispatchers.IO] inside a single
 * coroutine, so no writes can happen between the checkpoint and
 * the file copy. (Room serialises writes per connection; the
 * `PRAGMA wal_checkpoint(FULL)` call blocks until all WAL pages
 * are merged into the main DB.)
 *
 * The import side is the mirror: read header, derive the key,
 * decrypt under the same AAD, replace the on-disk DB, reopen.
 */
@Singleton
class VaultExporter @Inject constructor(
    @ApplicationContext private val context: Context,
    private val crypto: VaultCrypto,
    private val db: AppDatabase,
) {

    /**
     * Exports the on-disk Room DB to [outputUri], encrypted
     * under [passphrase]. The result is `Result.failure` on
     * any IO/encryption error — the caller surfaces the
     * [VaultError] in a Compose dialog.
     */
    suspend fun export(outputUri: Uri, passphrase: CharArray): Result<Unit> =
        withContext(Dispatchers.IO) {
            runCatching {
                val salt = crypto.generateSalt()
                val keyBytes = crypto.deriveKey(passphrase, salt)
                passphrase.fill('\u0000')

                val dbBytes = snapshotDatabase()
                val iv = crypto.generateIv()
                val header = VaultFormat.buildHeader(
                    salt = salt,
                    iv = iv,
                    kdfM = VaultCrypto.DEFAULT_M_KIB,
                    kdfT = VaultCrypto.DEFAULT_T,
                    kdfP = VaultCrypto.DEFAULT_P,
                    payloadLen = dbBytes.size + VaultCrypto.TAG_BYTES,
                )
                val payload = crypto.encrypt(
                    key = crypto.toSecretKey(keyBytes),
                    iv = iv,
                    plaintext = dbBytes,
                    aad = header,
                )
                keyBytes.fill(0)

                val out = context.contentResolver.openOutputStream(outputUri, "wt")
                    ?: throw VaultError.IoError("Could not open output stream")
                out.use { stream ->
                    stream.write(header)
                    stream.write(payload)
                }
            }.recoverCatching { e ->
                passphrase.fill('\u0000')
                throw when (e) {
                    is VaultError -> e
                    is IOException -> VaultError.IoError(e.message ?: "io error")
                    else -> e
                }
            }
        }

    /**
     * Forces a WAL checkpoint and copies the on-disk baton.db
     * to a byte array. The copy is self-consistent because the
     * checkpoint blocks until all WAL pages are merged into the
     * main DB file. Runs on whatever dispatcher `export()` is
     * called on (the coroutine is wrapped in `Dispatchers.IO`).
     */
    private fun snapshotDatabase(): ByteArray {
        db.openHelper.writableDatabase.execSQL("PRAGMA wal_checkpoint(FULL)")
        val file = context.getDatabasePath(AppDatabase.NAME)
        require(file.exists()) { "DB file does not exist: ${file.absolutePath}" }
        return file.readBytes()
    }
}
