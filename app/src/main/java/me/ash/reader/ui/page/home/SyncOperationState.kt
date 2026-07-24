package me.ash.reader.ui.page.home

import me.ash.reader.domain.model.general.OperationFailure

sealed interface SyncOperationState {
    data object Idle : SyncOperationState

    data class Running(
        val completed: Int = 0,
        val total: Int? = null,
    ) : SyncOperationState

    data object Completed : SyncOperationState

    data class Failed(val failure: OperationFailure? = null) : SyncOperationState
}

fun SyncOperationState.progressFraction(): Float? =
    (this as? SyncOperationState.Running)?.let { state ->
        state.total
            ?.takeIf { it > 0 }
            ?.let { total -> (state.completed.toFloat() / total).coerceIn(0f, 1f) }
    }
