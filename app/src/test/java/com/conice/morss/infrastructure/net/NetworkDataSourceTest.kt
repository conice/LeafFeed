package com.conice.morss.infrastructure.net

import java.io.File
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okhttp3.MediaType
import okhttp3.ResponseBody
import okio.Buffer
import okio.BufferedSource
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class NetworkDataSourceTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    fun `network clients verify server certificates by default`() {
        assertFalse(com.conice.morss.infrastructure.di.DEFAULT_TRUST_ALL_CERTIFICATES)
    }

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

    @Test
    fun `release request and response complete a real HTTP round trip`() = runBlocking {
        val server = MockWebServer()
        server.start()
        try {
            server.enqueue(
                MockResponse(
                    body =
                        """{"tag_name":"v1.2.3","draft":false,"assets":[{"name":"app.apk","size":42}]}"""
                )
            )
            val endpoint = server.url("/repos/owner/project/releases/latest").toString()
            val response = NetworkDataSource.create(server.url("/").toString())
                .getReleaseLatest(endpoint)

            assertTrue(response.isSuccessful)
            assertEquals("v1.2.3", response.body()?.tag_name)
            assertEquals(42, response.body()?.assets?.single()?.size)
            val request = server.takeRequest()
            assertEquals("GET", request.method)
            assertEquals("/repos/owner/project/releases/latest", request.requestUrl?.encodedPath)
        } finally {
            server.close()
        }
    }

    private fun body(content: ByteArray, contentLength: Long): ResponseBody =
        object : ResponseBody() {
            override fun contentType(): MediaType? = null
            override fun contentLength(): Long = contentLength
            override fun source(): BufferedSource = Buffer().write(content)
        }
}
