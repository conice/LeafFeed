package me.ash.reader.infrastructure.preference

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import me.ash.reader.ui.ext.dataStore
import me.ash.reader.domain.model.general.OperationFailureKind

@Serializable
enum class PersistedSyncState {
    RUNNING,
    SUCCEEDED,
    RETRYING,
    FAILED,
    CANCELLED,
}

@Serializable
data class SyncSummary(
    val accountId: Int,
    val state: PersistedSyncState,
    val startedAtMillis: Long,
    val finishedAtMillis: Long? = null,
    val completed: Int = 0,
    val total: Int? = null,
    val errorMessage: String? = null,
    val failureKind: OperationFailureKind? = null,
    val failedFeedIds: List<String> = emptyList(),
    val failedFeedNames: List<String> = emptyList(),
    val attempt: Int = 0,
    val scope: SyncScope = SyncScope.ACCOUNT,
)

@Serializable
enum class SyncScope { ACCOUNT, FEED, GROUP }

@Singleton
class SyncStatusStore @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val json = Json { ignoreUnknownKeys = true }

    fun observe(accountId: Int): Flow<SyncSummary?> =
        context.dataStore.data.map { preferences ->
            preferences[key(accountId)]?.let(::decode)
        }

    suspend fun get(accountId: Int): SyncSummary? =
        observe(accountId).first()

    suspend fun write(summary: SyncSummary) {
        context.dataStore.edit { preferences ->
            preferences[key(summary.accountId)] = json.encodeToString(SyncSummary.serializer(), summary)
        }
    }

    suspend fun clear(accountId: Int) {
        context.dataStore.edit { preferences -> preferences.remove(key(accountId)) }
    }

    private fun decode(value: String): SyncSummary? =
        runCatching { json.decodeFromString(SyncSummary.serializer(), value) }.getOrNull()

    private fun key(accountId: Int) = stringPreferencesKey("sync_summary_$accountId")
}
