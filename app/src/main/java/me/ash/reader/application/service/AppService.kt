package me.ash.reader.application.service

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.withContext
import me.ash.reader.R
import me.ash.reader.domain.model.general.toVersion
import me.ash.reader.infrastructure.di.IODispatcher
import me.ash.reader.infrastructure.di.MainDispatcher
import me.ash.reader.infrastructure.net.Download
import me.ash.reader.infrastructure.net.NetworkDataSource
import me.ash.reader.infrastructure.net.downloadToFileWithProgress
import me.ash.reader.infrastructure.preference.*
import me.ash.reader.infrastructure.preference.NewVersionSizePreference.formatSize
import me.ash.reader.infrastructure.android.getCurrentVersion
import me.ash.reader.infrastructure.android.getLatestApk
import me.ash.reader.infrastructure.preference.PreferencesKey
import me.ash.reader.infrastructure.android.showToast
import me.ash.reader.infrastructure.preference.dataStore
import javax.inject.Inject
import timber.log.Timber

class AppService @Inject constructor(
    @ApplicationContext
    private val context: Context,
    private val networkDataSource: NetworkDataSource,
    private val settingsProvider: SettingsProvider,
    @IODispatcher
    private val ioDispatcher: CoroutineDispatcher,
    @MainDispatcher
    private val mainDispatcher: CoroutineDispatcher,
) {

    suspend fun checkUpdate(showToast: Boolean = true): Boolean? = withContext(ioDispatcher) {
        try {
            val now = System.currentTimeMillis()
            val preferences = settingsProvider.awaitPreferences()
            if (!showToast && !shouldCheckForUpdate(now, preferences[LAST_UPDATE_CHECK_KEY])) {
                return@withContext false
            }
            // Record the attempt before making the request so repeated launches while offline do
            // not wake the radio every time. Manual checks always bypass this interval.
            if (!showToast) {
                context.dataStore.edit { it[LAST_UPDATE_CHECK_KEY] = now }
            }
            val response = networkDataSource.getReleaseLatest(context.getString(R.string.update_link))
            when {
                response.code() == 403 -> {
                    withContext(mainDispatcher) {
                        if (showToast) context.showToast(context.getString(R.string.rate_limit))
                    }
                    return@withContext null
                }

                response.body() == null -> {
                    withContext(mainDispatcher) {
                        if (showToast) context.showToast(context.getString(R.string.check_failure))
                    }
                    return@withContext null
                }
            }
            val skipVersion =
                SkipVersionNumberPreference.fromPreferences(settingsProvider.awaitPreferences())
            val currentVersion = context.getCurrentVersion()
            val latest = response.body()!!
            val latestVersion = latest.tag_name.toVersion()
            val latestLog = latest.body ?: ""
            val latestPublishDate = latest.published_at ?: latest.created_at ?: ""
            val latestSize = latest.assets?.first()?.size ?: 0
            val latestDownloadUrl = latest.assets?.first()?.browser_download_url ?: ""

            if (latestVersion.whetherNeedUpdate(currentVersion, skipVersion)) {
                context.dataStore.edit { updatePreferences ->
                    updatePreferences[stringPreferencesKey(PreferencesKey.newVersionNumber)] =
                        latestVersion.toString()
                    updatePreferences[stringPreferencesKey(PreferencesKey.newVersionLog)] = latestLog
                    updatePreferences[
                        stringPreferencesKey(PreferencesKey.newVersionPublishDate)
                    ] = latestPublishDate
                    updatePreferences[stringPreferencesKey(PreferencesKey.newVersionSizeString)] =
                        latestSize.formatSize()
                    updatePreferences[
                        stringPreferencesKey(PreferencesKey.newVersionDownloadUrl)
                    ] = latestDownloadUrl
                }
                true
            } else {
                false
            }
        } catch (e: Exception) {
            Timber.e(e, "Unable to check for an application update")
            withContext(mainDispatcher) {
                if (showToast) context.showToast(context.getString(R.string.check_failure))
            }
            null
        }
    }

    suspend fun downloadFile(url: String): Flow<Download> =
        withContext(ioDispatcher) {
            Timber.d("Starting the application update download")
            try {
                return@withContext networkDataSource.downloadFile(url)
                    .downloadToFileWithProgress(context.getLatestApk())
            } catch (e: Exception) {
                Timber.e(e, "Unable to start the application update download")
                withContext(mainDispatcher) {
                    context.showToast(context.getString(R.string.download_failure))
                }
            }
            emptyFlow()
        }
}

private val LAST_UPDATE_CHECK_KEY = longPreferencesKey("last_automatic_update_check")
internal const val AUTOMATIC_UPDATE_CHECK_INTERVAL_MS = 24L * 60L * 60L * 1_000L

internal fun shouldCheckForUpdate(nowMillis: Long, lastCheckMillis: Long?): Boolean {
    lastCheckMillis ?: return true
    val elapsed = nowMillis - lastCheckMillis
    return elapsed < 0L || elapsed >= AUTOMATIC_UPDATE_CHECK_INTERVAL_MS
}
