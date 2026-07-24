package me.ash.reader.domain.model.general

import java.io.IOException
import java.net.SocketTimeoutException
import java.io.FileNotFoundException
import org.junit.Assert.assertEquals
import org.junit.Test

class OperationFailureTest {
    @Test
    fun `classifies authentication before generic network failures`() {
        assertEquals(
            OperationFailureKind.AUTHENTICATION,
            IOException("request failed with 401").toOperationFailure().kind,
        )
    }

    @Test
    fun `classifies timeout and network failures`() {
        assertEquals(
            OperationFailureKind.TIMEOUT,
            SocketTimeoutException("timed out").toOperationFailure().kind,
        )
        assertEquals(
            OperationFailureKind.NETWORK,
            IOException("connection reset").toOperationFailure().kind,
        )
    }

    @Test
    fun `classifies wrapped failures and retryable server responses`() {
        assertEquals(
            OperationFailureKind.AUTHENTICATION,
            IllegalStateException("request failed", IOException("HTTP 403")).toOperationFailure().kind,
        )
        assertEquals(
            OperationFailureKind.NETWORK,
            IllegalStateException("HTTP 503 service unavailable").toOperationFailure().kind,
        )
        assertEquals(
            OperationFailureKind.STORAGE,
            FileNotFoundException("offline content").toOperationFailure().kind,
        )
    }
}
