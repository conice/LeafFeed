package me.ash.reader.ui.page.settings.troubleshooting

import org.junit.Assert.assertEquals
import org.junit.Test

class DiagnosticRedactionTest {
    @Test
    fun `redacts http and https addresses without removing surrounding diagnostics`() {
        assertEquals(
            "Failed [feed address hidden] and [feed address hidden] (timeout)",
            redactUrls(
                "Failed https://example.com/feed.xml and http://localhost:8080/rss (timeout)"
            ),
        )
    }
}
