package com.conice.morss.domain.model.account.security

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Encrypts account credentials with an app-local, non-exportable Android Keystore key.
 *
 * Versioned ciphertext keeps existing database rows readable while allowing the legacy DES format
 * to be migrated without a Room schema change.
 */
object CredentialCipher {
    private const val ANDROID_KEY_STORE = "AndroidKeyStore"
    private const val KEY_ALIAS = "morss.account.credentials.v2"
    private const val FORMAT_PREFIX = "v2"
    private const val SEPARATOR = ":"
    private const val GCM_TAG_LENGTH_BITS = 128

    fun encrypt(cleartext: String): String {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey())
        val iv = Base64.encodeToString(cipher.iv, Base64.NO_WRAP)
        val payload =
            Base64.encodeToString(
                cipher.doFinal(cleartext.toByteArray(Charsets.UTF_8)),
                Base64.NO_WRAP,
            )
        return listOf(FORMAT_PREFIX, iv, payload).joinToString(SEPARATOR)
    }

    fun decrypt(ciphertext: String): String {
        if (!isCurrentFormat(ciphertext)) return DESUtils.decrypt(ciphertext)

        val parts = ciphertext.split(SEPARATOR, limit = 3)
        require(parts.size == 3 && parts[1].isNotBlank() && parts[2].isNotBlank()) {
            "Invalid credential ciphertext"
        }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(
            Cipher.DECRYPT_MODE,
            getOrCreateKey(),
            GCMParameterSpec(GCM_TAG_LENGTH_BITS, Base64.decode(parts[1], Base64.NO_WRAP)),
        )
        return String(cipher.doFinal(Base64.decode(parts[2], Base64.NO_WRAP)), Charsets.UTF_8)
    }

    fun migrateLegacy(ciphertext: String?): String? {
        if (ciphertext.isNullOrBlank() || isCurrentFormat(ciphertext)) return ciphertext
        return encrypt(DESUtils.decrypt(ciphertext))
    }

    fun isCurrentFormat(ciphertext: String): Boolean =
        ciphertext.startsWith("$FORMAT_PREFIX$SEPARATOR")

    @Synchronized
    private fun getOrCreateKey(): SecretKey {
        val keyStore =
            KeyStore.getInstance(ANDROID_KEY_STORE).apply {
                load(null)
            }
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
