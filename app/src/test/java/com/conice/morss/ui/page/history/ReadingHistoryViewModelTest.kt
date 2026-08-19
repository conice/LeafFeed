package com.conice.morss.ui.page.history

import androidx.lifecycle.SavedStateHandle
import com.conice.morss.domain.repository.ReadingHistoryDao
import org.junit.Assert.assertEquals
import org.junit.Test
import org.mockito.kotlin.mock

class ReadingHistoryViewModelTest {
    @Test
    fun `search query survives view model recreation`() {
        val savedState = SavedStateHandle()
        ReadingHistoryViewModel(mock<ReadingHistoryDao>(), savedState)
            .updateSearchQuery("kotlin")

        val restored = ReadingHistoryViewModel(mock<ReadingHistoryDao>(), savedState)

        assertEquals("kotlin", restored.searchQuery.value)
    }
}
