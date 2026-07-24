package me.ash.reader.domain.model.general

import java.io.IOException
import java.io.FileNotFoundException
import java.net.SocketTimeoutException
import java.util.Locale

enum class OperationFailureKind {
    AUTHENTICATION,
    NETWORK,
    TIMEOUT,
    PARSING,
    STORAGE,
    UNKNOWN,
}

data class OperationFailure(
    val kind: OperationFailureKind,
    val message: String? = null,
)

fun Throwable.toOperationFailure(): OperationFailure {
    val chain = generateSequence(this) { it.cause }.toList()
    val messages = chain.joinToString(" ") { it.message.orEmpty() }.lowercase(Locale.ROOT)
    val kind = when {
        messages.contains("401") || messages.contains("403") || messages.contains("unauthorized") ||
            messages.contains("forbidden") ->
            OperationFailureKind.AUTHENTICATION
        chain.any { it is SocketTimeoutException } -> OperationFailureKind.TIMEOUT
        chain.any { it is kotlinx.serialization.SerializationException } -> OperationFailureKind.PARSING
        chain.any { it is FileNotFoundException || it is SecurityException } ->
            OperationFailureKind.STORAGE
        chain.any { it is IOException } ||
            messages.contains("http 429") || messages.contains("http 5") ->
            OperationFailureKind.NETWORK
        else -> OperationFailureKind.UNKNOWN
    }
    return OperationFailure(kind = kind, message = message)
}
