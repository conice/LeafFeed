package com.conice.morss.infrastructure.exception

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SuspendResultTest {
    @Test(expected = CancellationException::class)
    fun `cancellation is never converted to failure`() {
        runBlocking {
            runSuspendCatching<Int> { throw CancellationException("cancelled") }
        }
    }

    @Test
    fun `ordinary failures remain inspectable`() = runBlocking {
        val result = runSuspendCatching<Int> { error("failed") }

        assertTrue(result.isFailure)
        assertEquals("failed", result.exceptionOrNull()?.message)
    }
}
