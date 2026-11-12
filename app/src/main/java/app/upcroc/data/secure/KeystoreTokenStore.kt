package app.upcroc.data.secure

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import app.upcroc.core.auth.TokenSet
import app.upcroc.core.auth.TokenStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Tokens are encrypted with a non-exportable Android Keystore key and stored in
 * noBackupFilesDir. Losing the key simply signs the user out; it never exposes plaintext.
 */
class KeystoreTokenStore(context: Context) : TokenStore {
    private val appContext = context.applicationContext
    private val file = File(appContext.noBackupFilesDir, "token.bin")

    override suspend fun load(): TokenSet? = withContext(Dispatchers.IO) {
        runCatching {
            if (!file.exists()) return@withContext null
            val bytes = file.readBytes()
            if (bytes.size <= IV_BYTES) return@withContext null
            val iv = bytes.copyOfRange(0, IV_BYTES)
            val ciphertext = bytes.copyOfRange(IV_BYTES, bytes.size)
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, iv))
            TokenSet.decode(String(cipher.doFinal(ciphertext), Charsets.UTF_8))
        }.getOrElse {
            runCatching { file.delete() }
            null
        }
    }

    override suspend fun save(tokens: TokenSet) = withContext(Dispatchers.IO) {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, key())
        val ciphertext = cipher.doFinal(tokens.encode().toByteArray(Charsets.UTF_8))
        val payload = cipher.iv + ciphertext
        val temp = File(appContext.noBackupFilesDir, "token.bin.tmp")
        temp.writeBytes(payload)
        if (!temp.renameTo(file)) {
            file.writeBytes(payload)
            temp.delete()
        }
    }

    override suspend fun clear() = withContext(Dispatchers.IO) {
        runCatching { file.delete() }
        runCatching {
            val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
            if (store.containsAlias(ALIAS)) store.deleteEntry(ALIAS)
        }
        Unit
    }

    private fun key(): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (store.getEntry(ALIAS, null) as? KeyStore.SecretKeyEntry)?.let { return it.secretKey }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        generator.init(
            KeyGenParameterSpec.Builder(
                ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .setUserAuthenticationRequired(false)
                .build()
        )
        return generator.generateKey()
    }

    private companion object {
        const val ALIAS = "upcroc_token_v1"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val IV_BYTES = 12
    }
}
