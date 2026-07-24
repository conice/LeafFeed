package me.ash.reader.domain.data

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import me.ash.reader.domain.model.feed.Feed
import me.ash.reader.domain.model.general.Filter
import me.ash.reader.domain.model.group.Group
import me.ash.reader.domain.repository.ArticleDao
import me.ash.reader.domain.repository.FeedDao
import me.ash.reader.domain.repository.GroupDao
import me.ash.reader.infrastructure.di.ApplicationScope
import me.ash.reader.infrastructure.preference.SettingsProvider
import javax.inject.Singleton

@Singleton
class FilterStateUseCase
@Inject
constructor(
    settingsProvider: SettingsProvider,
    @ApplicationContext context: Context,
    private val articleDao: ArticleDao,
    private val feedDao: FeedDao,
    private val groupDao: GroupDao,
    @ApplicationScope private val coroutineScope: CoroutineScope,
) {
    private val contentTypePreferences =
        context.getSharedPreferences("content_type_selection", Context.MODE_PRIVATE)

    private val _filterUiState =
        MutableStateFlow(FilterState(filter = settingsProvider.settings.initialFilter.toFilter()))
    val filterStateFlow = _filterUiState.asStateFlow()
    private val _contentTypeRevision = MutableStateFlow(0L)
    val contentTypeRevision = _contentTypeRevision.asStateFlow()
    private val filterState
        get() = filterStateFlow.value

    fun updateFilterState(
        feed: Feed? = filterState.feed,
        group: Group? = filterState.group,
        filter: Filter = filterState.filter,
        contentType: ArticleContentType = filterState.contentType,
        searchContent: String? = filterState.searchContent,
        highlightRuleId: String? = filterState.highlightRuleId,
        highlightUnreadOnly: Boolean = filterState.highlightUnreadOnly,
    ) {
        _filterUiState.update {
            it.copy(
                feed = feed,
                group = group,
                searchContent = searchContent,
                filter = filter,
                contentType = contentType,
                highlightRuleId = if (filter.isHighlighted()) highlightRuleId else null,
                highlightUnreadOnly = highlightUnreadOnly,
            )
        }
    }

    fun updateFilterState(filterState: FilterState) {
        _filterUiState.update { filterState }
    }

    fun contentTypeForScope(filterState: FilterState): ArticleContentType =
        storedContentType(filterState) ?: ArticleContentType.ARTICLE

    fun hasStoredContentType(filterState: FilterState): Boolean =
        storedContentType(filterState) != null

    suspend fun saveContentType(filterState: FilterState) {
        val key = contentTypeScopeKey(filterState) ?: return
        val editor = contentTypePreferences.edit().putInt(key, filterState.contentType.ordinal)
        if (filterState.group != null) {
            // A group selection is a bulk operation. Remove feed overrides so every
            // subscription in the group inherits the new group setting.
            feedDao.queryByGroupId(filterState.group.accountId, filterState.group.id)
                .forEach { feed ->
                    editor.remove("account:${feed.accountId}:feed:${feed.id}")
                    editor.remove("feed:${feed.id}")
                }
        }
        editor.apply()
        _contentTypeRevision.update { it + 1 }
    }

    private fun storedContentType(filterState: FilterState): ArticleContentType? {
        val key = contentTypeScopeKey(filterState) ?: return null
        readContentType(key)?.let { return it }

        // A feed inherits its group's setting unless it has an explicit override.
        filterState.feed?.let { feed ->
            readContentType("feed:${feed.id}")?.let { return it }
            readContentType("account:${feed.accountId}:group:${feed.groupId}")?.let { return it }
            readContentType("group:${feed.groupId}")?.let { return it }
            return null
        }

        val legacyKey = legacyContentTypeScopeKey(filterState) ?: return null
        return readContentType(legacyKey)
    }

    private fun readContentType(key: String): ArticleContentType? =
        contentTypePreferences.getInt(key, -1)
            .takeIf { it in ArticleContentType.entries.indices }
            ?.let(ArticleContentType.entries::get)

    suspend fun autoSelectContentType(filterState: FilterState): FilterState {
        val accountId = filterState.feed?.accountId ?: filterState.group?.accountId
            ?: return filterState
        val groupId = filterState.group?.id
        val feedId = filterState.feed?.id
        val selectedHasContent = articleDao.hasContent(
            accountId = accountId,
            groupId = groupId,
            feedId = feedId,
            audioOnly = filterState.contentType == ArticleContentType.AUDIO,
        )
        if (selectedHasContent) return filterState

        val otherContentType = filterState.contentType.other()
        val otherHasContent = articleDao.hasContent(
            accountId = accountId,
            groupId = groupId,
            feedId = feedId,
            audioOnly = otherContentType == ArticleContentType.AUDIO,
        )
        return filterState.copy(
            contentType = resolveContentType(
                selected = filterState.contentType,
                selectedHasContent = false,
                otherHasContent = otherHasContent,
            )
        )
    }

    fun init(feedId: String?, groupId: String?) {
        coroutineScope.launch { initNow(feedId, groupId) }
    }

    suspend fun initNow(feedId: String?, groupId: String?) {
        val feed = feedId?.let { feedDao.queryById(it) }
        val group = groupId?.let { groupDao.queryById(it) }
        val nextState = filterState.copy(
            feed = feed,
            group = group,
            filter = Filter.Unread,
        ).let { it.copy(contentType = contentTypeForScope(it)) }
        val resolvedState =
            if (hasStoredContentType(nextState)) nextState else autoSelectContentType(nextState)
        updateFilterState(resolvedState)
    }
}

internal fun contentTypeScopeKey(filterState: FilterState): String? =
    filterState.feed?.let { "account:${it.accountId}:feed:${it.id}" }
        ?: filterState.group?.let { "account:${it.accountId}:group:${it.id}" }

private fun legacyContentTypeScopeKey(filterState: FilterState): String? =
    filterState.feed?.id?.let { "feed:$it" }
        ?: filterState.group?.id?.let { "group:$it" }

data class FilterState(
    val group: Group? = null,
    val feed: Feed? = null,
    val filter: Filter = Filter.All,
    val contentType: ArticleContentType = ArticleContentType.ARTICLE,
    val searchContent: String? = null,
    val highlightRuleId: String? = null,
    val highlightUnreadOnly: Boolean = true,
)

enum class ArticleContentType {
    ARTICLE,
    AUDIO,
}

private fun ArticleContentType.other(): ArticleContentType =
    when (this) {
        ArticleContentType.ARTICLE -> ArticleContentType.AUDIO
        ArticleContentType.AUDIO -> ArticleContentType.ARTICLE
    }

internal fun resolveContentType(
    selected: ArticleContentType,
    selectedHasContent: Boolean,
    otherHasContent: Boolean,
): ArticleContentType =
    if (!selectedHasContent && otherHasContent) selected.other() else selected

internal fun ArticleContentType.includes(audioUrl: String?): Boolean =
    when (this) {
        ArticleContentType.ARTICLE -> audioUrl == null
        ArticleContentType.AUDIO -> audioUrl != null
    }
