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

private fun Throwable.causeChain(): List<Throwable> {
    val causes = mutableListOf<Throwable>()
    var current: Throwable? = this
    while (current != null && current !in causes) {
        causes += current
        current = current.cause
    }
    return causes
}
