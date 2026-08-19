package com.conice.morss.ui.page.settings.accounts.addition

import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import javax.inject.Inject

@HiltViewModel
class AdditionViewModel @Inject constructor() : ViewModel() {

    private val _additionUiState = MutableStateFlow(AdditionUiState())
    val additionUiState: StateFlow<AdditionUiState> = _additionUiState.asStateFlow()

    fun showAddLocalAccountDialog() {
        _additionUiState.update {
            it.copy(
                addLocalAccountDialogVisible = true,
            )
        }
    }

    fun hideAddLocalAccountDialog() {
        _additionUiState.update {
            it.copy(
                addLocalAccountDialogVisible = false,
            )
        }
    }

    fun showAddFeverAccountDialog() {
        _additionUiState.update {
            it.copy(
                addFeverAccountDialogVisible = true,
            )
        }
    }

    fun hideAddFeverAccountDialog() {
        _additionUiState.update {
            it.copy(
                addFeverAccountDialogVisible = false,
            )
        }
    }

    fun showAddGoogleReaderAccountDialog() {
        _additionUiState.update {
            it.copy(
                addGoogleReaderAccountDialogVisible = true,
            )
        }
    }

    fun hideAddGoogleReaderAccountDialog() {
        _additionUiState.update {
            it.copy(
                addGoogleReaderAccountDialogVisible = false,
            )
        }
    }

    fun showAddFreshRSSAccountDialog() {
        _additionUiState.update {
            it.copy(
                addFreshRSSAccountDialogVisible = true,
            )
        }
    }

    fun hideAddFreshRSSAccountDialog() {
        _additionUiState.update {
            it.copy(
                addFreshRSSAccountDialogVisible = false,
            )
        }
    }
}

data class AdditionUiState(
    val addLocalAccountDialogVisible: Boolean = false,
    val addFeverAccountDialogVisible: Boolean = false,
    val addGoogleReaderAccountDialogVisible: Boolean = false,
    val addFreshRSSAccountDialogVisible: Boolean = false,
)
