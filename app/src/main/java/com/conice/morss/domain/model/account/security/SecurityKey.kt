package com.conice.morss.domain.model.account.security

import com.google.gson.Gson

abstract class SecurityKey {
    fun <T> decode(value: String?, classOfT: Class<T>): T =
        gson.fromJson(
            CredentialCipher.decrypt(value?.ifEmpty { DESUtils.empty } ?: DESUtils.empty),
            classOfT,
        )

    override fun toString(): String = CredentialCipher.encrypt(gson.toJson(this))

    override fun equals(other: Any?): Boolean =
        other != null && javaClass == other.javaClass && gson.toJson(this) == gson.toJson(other)

    override fun hashCode(): Int = 31 * javaClass.hashCode() + gson.toJson(this).hashCode()

    private companion object {
        val gson = Gson()
    }
}
