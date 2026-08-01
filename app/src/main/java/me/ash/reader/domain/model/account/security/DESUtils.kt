package me.ash.reader.domain.model.account.security

import android.util.Base64
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.DESKeySpec

/** Legacy credential decoder retained only for migration to [CredentialCipher]. */
object DESUtils {

    const val empty = "CvJ1PKM8EW8="
    private const val secret = "mJn':4Nbk};AMVFGEWiY!(8&gp1xOv@/"

    fun decrypt(ciphertext: String): String {
        val key =
            SecretKeyFactory
                .getInstance("DES")
                .generateSecret(DESKeySpec(secret.toByteArray()))

        return Cipher.getInstance("DES").run {
            init(Cipher.DECRYPT_MODE, key)
            String(doFinal(Base64.decode(ciphertext, Base64.DEFAULT)), Charsets.UTF_8)
        }
    }
}
