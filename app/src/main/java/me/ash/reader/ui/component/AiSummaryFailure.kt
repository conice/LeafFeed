package me.ash.reader.ui.component

import androidx.annotation.StringRes
import java.io.IOException
import java.io.InterruptedIOException
import java.net.ConnectException
import java.net.NoRouteToHostException
import java.net.UnknownHostException
import javax.net.ssl.SSLException
import kotlinx.serialization.SerializationException
import me.ash.reader.R

enum class AiSummaryFailure(@StringRes val messageRes: Int) {
    NotConfigured(R.string.ai_summary_error_not_configured),
    InvalidConfiguration(R.string.ai_summary_error_invalid_configuration),
    Authentication(R.string.ai_summary_error_authentication),
    EndpointOrModelNotFound(R.string.ai_summary_error_endpoint_or_model),
    RateLimited(R.string.ai_summary_error_rate_limited),
    RequestRejected(R.string.ai_summary_error_request_rejected),
    ServiceUnavailable(R.string.ai_summary_error_service_unavailable),
    Network(R.string.ai_summary_error_network),
    Timeout(R.string.ai_summary_error_timeout),
    EmptyResponse(R.string.ai_summary_error_empty_response),
    InvalidResponse(R.string.ai_summary_error_invalid_response),
    NoArticles(R.string.ai_summary_error_no_articles),
    Unknown(R.string.ai_summary_error_unknown),
    ;

    companion object {
        fun from(error: Throwable): AiSummaryFailure {
            val causes = error.causeChain()
            val message = causes.mapNotNull(Throwable::message).joinToString("\n").lowercase()

            if ("ai url is not configured" in message || "ai model is not configured" in message) {
                return NotConfigured
            }
            if ("no articles to summarize" in message) return NoArticles
            if ("empty response" in message) return EmptyResponse

            if (
                "api key" in message || "unauthorized" in message ||
                    "authentication" in message || "permission denied" in message
            ) {
                return Authentication
            }
            if ("rate limit" in message || "quota" in message || "too many requests" in message) {
                return RateLimited
            }
            if (
                "model" in message &&
                    ("not found" in message || "unavailable" in message || "does not exist" in message)
            ) {
                return EndpointOrModelNotFound
            }
            HTTP_STATUS.find(message)?.groupValues?.getOrNull(1)?.toIntOrNull()?.let { status ->
                return when (status) {
                    401, 403 -> Authentication
                    404 -> EndpointOrModelNotFound
                    408 -> Timeout
                    429 -> RateLimited
                    in 400..499 -> RequestRejected
                    in 500..599 -> ServiceUnavailable
                    else -> Unknown
                }
            }
            if (causes.any { it is InterruptedIOException }) return Timeout
            if (
                causes.any {
                    it is UnknownHostException || it is ConnectException ||
                        it is NoRouteToHostException || it is SSLException
                }
            ) {
                return Network
            }
            if (causes.any { it is SerializationException }) return InvalidResponse
            if (causes.any { it is IOException }) return Network
            if (causes.any { it is IllegalArgumentException }) return InvalidConfiguration
            return Unknown
        }

        private val HTTP_STATUS = Regex("ai request failed \\((\\d{3})\\)")
    }
}

internal fun Throwable.aiSummaryFailureDetail(): String {
    val detail =
        causeChain()
            .map { cause ->
                val type = cause.javaClass.simpleName.ifBlank { cause.javaClass.name }
                val message = cause.message?.trim()?.takeIf { it.isNotEmpty() }
                if (message == null) type else "$type: ${message.redactAiSecrets()}"
            }
            .distinct()
            .joinToString("\nCaused by: ")
    return if (detail.length <= MAX_AI_ERROR_DETAIL_LENGTH) {
        detail
    } else {
        detail.take(MAX_AI_ERROR_DETAIL_LENGTH - 1) + "…"
    }
}

private fun Throwable.causeChain(): List<Throwable> {
    val causes = mutableListOf<Throwable>()
    var current: Throwable? = this
    while (current != null && current !in causes) {
        causes += current
        current = current.cause
    }
    return causes
}

private fun String.redactAiSecrets(): String =
    replace(AUTHORIZATION_BEARER) { match -> "${match.groupValues[1]}***" }
        .replace(AUTHORIZATION_VALUE) { match -> "${match.groupValues[1]}***" }
        .replace(SECRET_ASSIGNMENT) { match -> "${match.groupValues[1]}***" }
        .replace(URL_PASSWORD) { match -> "${match.groupValues[1]}***@" }
        .replace(OPENAI_STYLE_KEY, "sk-***")
        .replace(GOOGLE_STYLE_KEY, "AIza***")

private const val MAX_AI_ERROR_DETAIL_LENGTH = 2_000
private val AUTHORIZATION_BEARER = Regex("(?i)(\\b(?:bearer|basic)\\s+)[^\\s,;\\\"']+")
private val AUTHORIZATION_VALUE =
    Regex("(?i)(\\bauthorization\\s*[=:]\\s*)(?!(?:bearer|basic)\\s)[^\\s,;&]+")
private val SECRET_ASSIGNMENT =
    Regex("(?i)(\\b(?:api[_-]?key|access[_-]?token)\\s*[=:]\\s*)[^\\s,;&]+")
private val URL_PASSWORD = Regex("(?i)(https?://[^:/\\s]+:)[^@/\\s]+@")
private val OPENAI_STYLE_KEY = Regex("\\bsk-[A-Za-z0-9._-]{6,}\\b")
private val GOOGLE_STYLE_KEY = Regex("\\bAIza[0-9A-Za-z_-]{20,}\\b")
