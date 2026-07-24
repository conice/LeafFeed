package me.ash.reader.domain.model.account

import me.ash.reader.infrastructure.preference.KeepArchivedPreference
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class KeepArchivedConvertersTest {
    private val converters = KeepArchivedConverters()

    @Test
    fun `restores existing fixed retention values as days`() {
        assertEquals(1, converters.toKeepArchived(86_400_000L).days)
        assertEquals(7, converters.toKeepArchived(604_800_000L).days)
        assertEquals(30, converters.toKeepArchived(2_592_000_000L).days)
    }

    @Test
    fun `round trips custom retention days through millisecond storage`() {
        val preference = KeepArchivedPreference(45)

        val restored = converters.toKeepArchived(converters.fromKeepArchived(preference))

        assertEquals(preference, restored)
    }

    @Test
    fun `zero means keep archived articles forever`() {
        val preference = converters.toKeepArchived(0L)

        assertEquals(0, preference.days)
        assertEquals(true, preference.keepForever)
    }

    @Test
    fun `invalid stored values fall back to the default`() {
        assertEquals(KeepArchivedPreference.default, converters.toKeepArchived(-1L))
        assertEquals(KeepArchivedPreference.default, converters.toKeepArchived(1L))
        assertThrows(IllegalArgumentException::class.java) { KeepArchivedPreference(-1) }
    }
}
