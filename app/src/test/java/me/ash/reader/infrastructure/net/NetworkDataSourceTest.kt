package me.ash.reader.infrastructure.net

import java.io.File
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import okhttp3.MediaType
import okhttp3.ResponseBody
import okio.Buffer
import okio.BufferedSource
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class NetworkDataSourceTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    fun `downloads responses without content length`() = runBlocking {
        val content = "chunked response".toByteArray()
        val destination = temporaryFolder.newFile("download")
        val events = body(content, contentLength = -1L).downloadToFileWithProgress(destination).toList()

        assertEquals(listOf(Download.Progress(0), Download.Finished(destination)), events)
        assertArrayEquals(content, destination.readBytes())
    }

    @Test
    fun `download progress remains within percent bounds`() = runBlocking {
        val destination = temporaryFolder.newFile("download")
        val events = body("content".toByteArray(), contentLength = 7L)
            .downloadToFileWithProgress(destination)
            .toList()

        assertEquals(100, events.filterIsInstance<Download.Progress>().last().percent)
    }

    private fun body(content: ByteArray, contentLength: Long): ResponseBody =
        object : ResponseBody() {
            override fun contentType(): MediaType? = null
            override fun contentLength(): Long = contentLength
            override fun source(): BufferedSource = Buffer().write(content)
        }
}
