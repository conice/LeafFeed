package me.ash.reader.ui.page.history

import androidx.lifecycle.SavedStateHandle
import me.ash.reader.domain.repository.ArticleDao
import org.junit.Assert.assertEquals
import org.junit.Test
import org.mockito.kotlin.mock

class ReadingHistoryViewModelTest {
    @Test
    fun `search query survives view model recreation`() {
        val savedState = SavedStateHandle()
        ReadingHistoryViewModel(mock<ArticleDao>(), savedState)
            .updateSearchQuery("kotlin")

        val restored = ReadingHistoryViewModel(mock<ArticleDao>(), savedState)

        assertEquals("kotlin", restored.searchQuery.value)
    }
}
