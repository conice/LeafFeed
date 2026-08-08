package me.ash.reader.infrastructure.ai

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.inject.Inject
import javax.inject.Singleton

/** Stores provider secrets encrypted with an AI-specific Android Keystore key. */
@Singleton
class AiSecretStore @Inject constructor(
    private val dao: AiDao,
) {
    suspend fun put(secretRef: String, value: String) {
        require(value.isNotBlank()) { "AI secret cannot be blank" }
        dao.insertSecret(
            AiSecretEntity(
                id = secretRef,
                ciphertext = AiSecretCipher.encrypt(value),
                updatedAt = System.currentTimeMillis(),
            )
        )
    }

    suspend fun get(secretRef: String?): String? =
        secretRef?.takeIf { it.isNotBlank() }?.let { ref ->
            dao.querySecret(ref)?.let { secret ->
                runCatching { AiSecretCipher.decrypt(secret.ciphertext) }.getOrNull()
            }
        }

    suspend fun remove(secretRef: String?) {
        val ref = secretRef?.takeIf { it.isNotBlank() } ?: return
        dao.deleteSecret(ref)
    }
}

private object AiSecretCipher {
    private const val ANDROID_KEY_STORE = "AndroidKeyStore"
    private const val KEY_ALIAS = "leaffeed.ai.secrets.v1"
    private const val FORMAT_PREFIX = "v1"
    private const val SEPARATOR = ":"
    private const val GCM_TAG_LENGTH_BITS = 128

    fun encrypt(cleartext: String): String {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey())
        val iv = Base64.encodeToString(cipher.iv, Base64.NO_WRAP)
        val payload = Base64.encodeToString(
            cipher.doFinal(cleartext.toByteArray(Charsets.UTF_8)),
            Base64.NO_WRAP,
        )
        return listOf(FORMAT_PREFIX, iv, payload).joinToString(SEPARATOR)
    }

    fun decrypt(ciphertext: String): String {
        val parts = ciphertext.split(SEPARATOR, limit = 3)
        require(parts.size == 3 && parts[0] == FORMAT_PREFIX) {
            "Invalid AI secret ciphertext"
        }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(
            Cipher.DECRYPT_MODE,
            getOrCreateKey(),
            GCMParameterSpec(
                GCM_TAG_LENGTH_BITS,
                Base64.decode(parts[1], Base64.NO_WRAP),
            ),
        )
        return String(
            cipher.doFinal(Base64.decode(parts[2], Base64.NO_WRAP)),
            Charsets.UTF_8,
        )
    }

    @Synchronized
    private fun getOrCreateKey(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEY_STORE).apply { load(null) }
        (keyStore.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }

        val keyGenerator =
            KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEY_STORE)
        keyGenerator.init(
            KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setRandomizedEncryptionRequired(true)
                .build()
        )
        return keyGenerator.generateKey()
    }
}
