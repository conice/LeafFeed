@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)

package me.ash.reader.ui.page.settings.features

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsBottomHeight
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.OpenInNew
import androidx.compose.material.icons.outlined.RemoveCircleOutline
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.Color
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.work.WorkInfo
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import me.ash.reader.R
import me.ash.reader.application.data.ArticleCollectionRepository
import me.ash.reader.domain.model.article.Article
import me.ash.reader.domain.model.article.ArticleNote
import me.ash.reader.domain.model.article.ArticleWithFeed
import me.ash.reader.domain.model.article.ArticleTagLabel
import me.ash.reader.domain.repository.PodcastDao
import me.ash.reader.application.service.AccountService
import me.ash.reader.application.service.RssService
import me.ash.reader.infrastructure.audio.PodcastDownloadRepository
import me.ash.reader.infrastructure.audio.PodcastDownloadWorker
import me.ash.reader.infrastructure.preference.FeaturePreferenceKeys
import me.ash.reader.infrastructure.preference.SettingsProvider
import me.ash.reader.ui.component.base.DisplayText
import me.ash.reader.ui.component.base.FeedbackIconButton
import me.ash.reader.ui.component.base.RYScaffold
import me.ash.reader.ui.component.base.Subtitle
import me.ash.reader.ui.page.settings.SettingItem
import me.ash.reader.ui.theme.palette.onLight

private enum class PodcastLibraryFilter { ALL, UNPLAYED, DOWNLOADED }

@HiltViewModel
class PodcastLibraryViewModel @Inject constructor(
    accountService: AccountService,
    private val downloads: PodcastDownloadRepository,
    private val settingsProvider: SettingsProvider,
    private val podcastDao: PodcastDao,
) : ViewModel() {
    val episodes = accountService.currentAccountIdFlow.filterNotNull()
        .flatMapLatest { podcastDao.observePodcastEpisodes(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val downloadWorkInfos = downloads.downloadWorkInfosByArticleId
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    fun download(article: Article) {
        downloads.enqueue(
            article,
            settingsProvider.get(FeaturePreferenceKeys.podcastWifiOnly) ?: true,
        )
    }

    fun remove(article: Article) = viewModelScope.launch { downloads.remove(article) }
    fun cancel(articleId: String) = downloads.cancel(articleId)
}

@Composable
fun PodcastLibraryPage(
    onBack: () -> Unit,
    onOpenArticle: (String) -> Unit,
    viewModel: PodcastLibraryViewModel = hiltViewModel(),
) {
    val episodes by viewModel.episodes.collectAsStateWithLifecycle()
    val downloadWorkInfos by viewModel.downloadWorkInfos.collectAsStateWithLifecycle()
    var filter by remember { mutableStateOf(PodcastLibraryFilter.ALL) }
    val visible = episodes.filter {
        when (filter) {
            PodcastLibraryFilter.ALL -> true
            PodcastLibraryFilter.UNPLAYED -> !it.article.isPlayed
            PodcastLibraryFilter.DOWNLOADED -> it.article.downloadedPath != null
        }
    }
    ManagementScaffold("Podcast library", onBack) {
        item {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 24.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                PodcastLibraryFilter.entries.forEach { option ->
                    FilterChip(
                        selected = filter == option,
                        onClick = { filter = option },
                        label = { Text(option.name.lowercase().replaceFirstChar(Char::uppercaseChar)) },
                    )
                }
            }
            Spacer(Modifier.height(12.dp))
        }
        if (visible.isEmpty()) {
            item { Text("No episodes in this view", Modifier.padding(horizontal = 24.dp)) }
        }
        items(visible, key = { it.article.id }) { item ->
            val work = downloadWorkInfos[item.article.id]
            val active = work?.state == WorkInfo.State.ENQUEUED ||
                work?.state == WorkInfo.State.RUNNING ||
                work?.state == WorkInfo.State.BLOCKED
            val progress = work?.progress?.getInt(PodcastDownloadWorker.PROGRESS, -1) ?: -1
            SettingItem(
                title = item.article.title,
                desc = buildString {
                    append(item.feed.name)
                    if (item.article.isPlayed) append(" · Played")
                    if (item.article.downloadedPath != null) append(" · Downloaded")
                    else if (active) {
                        append(" · Downloading")
                        if (progress >= 0) append(" $progress%")
                    } else if (work?.state == WorkInfo.State.FAILED) append(" · Download failed")
                },
                onClick = { onOpenArticle(item.article.id) },
            ) {
                Row {
                    IconButton(onClick = { onOpenArticle(item.article.id) }) {
                        Icon(Icons.Outlined.OpenInNew, contentDescription = "Open episode")
                    }
                    if (active) {
                        IconButton(onClick = { viewModel.cancel(item.article.id) }) {
                            Icon(Icons.Outlined.RemoveCircleOutline, contentDescription = "Cancel download")
                        }
                    } else if (item.article.downloadedPath == null) {
                        IconButton(onClick = { viewModel.download(item.article) }) {
                            Icon(Icons.Outlined.Download, contentDescription = "Download episode")
                        }
                    } else {
                        IconButton(onClick = { viewModel.remove(item.article) }) {
                            Icon(Icons.Outlined.RemoveCircleOutline, contentDescription = "Remove download")
                        }
                    }
                }
            }
        }
    }
}

@HiltViewModel
class CollectionManagerViewModel @Inject constructor(
    private val repository: ArticleCollectionRepository,
    private val rssService: RssService,
) : ViewModel() {
    val tags = repository.observeTagGroups()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val notes = repository.observeAllNotes()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    private val _taggedArticles = MutableStateFlow<List<ArticleWithFeed>?>(null)
    val taggedArticles = _taggedArticles.asStateFlow()

    fun update(tag: ArticleTagLabel, name: String, color: Int?) =
        viewModelScope.launch { repository.updateTag(tag, name, color) }
    fun delete(tag: ArticleTagLabel) = viewModelScope.launch { repository.deleteTag(tag) }
    fun delete(note: ArticleNote) = viewModelScope.launch { repository.deleteNote(note) }
    fun showArticles(tag: ArticleTagLabel) = viewModelScope.launch {
        _taggedArticles.value = repository.queryArticleIdsForTag(tag.id).mapNotNull { id ->
            runCatching { rssService.get().findArticleById(id) }
                .getOrNull()
        }
    }
    fun hideArticles() { _taggedArticles.value = null }
}

@Composable
fun CollectionManagerPage(
    onBack: () -> Unit,
    onOpenArticle: (String) -> Unit,
    viewModel: CollectionManagerViewModel = hiltViewModel(),
) {
    val tags by viewModel.tags.collectAsStateWithLifecycle()
    val notes by viewModel.notes.collectAsStateWithLifecycle()
    val taggedArticles by viewModel.taggedArticles.collectAsStateWithLifecycle()
    var editingTag by remember { mutableStateOf<ArticleTagLabel?>(null) }

    ManagementScaffold("Tags and notes", onBack) {
        item { Subtitle(Modifier.padding(horizontal = 24.dp), "Tags") }
        if (tags.isEmpty()) item { EmptyManagerRow("No tags") }
        items(tags, key = { it.tag.id }) { group ->
            val tag = group.tag
            val articleLabel = if (group.articleCount == 1L) "1 article" else "${group.articleCount} articles"
            SettingItem(title = tag.name, desc = articleLabel, onClick = { viewModel.showArticles(tag) }) {
                Row {
                    tag.color?.let { value ->
                        androidx.compose.foundation.layout.Box(
                            Modifier.align(Alignment.CenterVertically)
                                .size(18.dp)
                                .background(Color(value), CircleShape),
                        )
                    }
                    IconButton(onClick = { editingTag = tag }) {
                        Icon(Icons.Outlined.Edit, contentDescription = "Rename tag")
                    }
                    IconButton(onClick = { viewModel.delete(tag) }) {
                        Icon(Icons.Outlined.Delete, contentDescription = "Delete tag")
                    }
                }
            }
        }
        item { Subtitle(Modifier.padding(horizontal = 24.dp), "Notes") }
        if (notes.isEmpty()) item { EmptyManagerRow("No notes") }
        items(notes, key = { it.id }) { note ->
            SettingItem(
                title = note.note.ifBlank { note.quote },
                desc = note.quote.takeIf { it.isNotBlank() },
                onClick = { onOpenArticle(note.articleId) },
            ) {
                Row {
                    IconButton(onClick = { onOpenArticle(note.articleId) }) {
                        Icon(Icons.Outlined.OpenInNew, contentDescription = "Open article")
                    }
                    IconButton(onClick = { viewModel.delete(note) }) {
                        Icon(Icons.Outlined.Delete, contentDescription = "Delete note")
                    }
                }
            }
        }
    }

    taggedArticles?.let { articles ->
        AlertDialog(
            onDismissRequest = viewModel::hideArticles,
            title = { Text("Articles with this tag") },
            text = {
                if (articles.isEmpty()) {
                    Text("No saved articles use this tag.")
                } else {
                    LazyColumn(Modifier.heightIn(max = 400.dp)) {
                        items(articles, key = { it.article.id }) { item ->
                            TextButton(onClick = { onOpenArticle(item.article.id) }) {
                                Text(
                                    item.article.title,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = viewModel::hideArticles) { Text("Close") }
            },
        )
    }

    editingTag?.let { tag ->
        var name by remember(tag.id) { mutableStateOf(tag.name) }
        var color by remember(tag.id) { mutableStateOf(tag.color) }
        val colors = listOf<Int?>(
            null,
            0xFFB3261E.toInt(),
            0xFF7D5260.toInt(),
            0xFF006A6A.toInt(),
            0xFF386A20.toInt(),
            0xFF4F5F7A.toInt(),
        )
        AlertDialog(
            onDismissRequest = { editingTag = null },
            title = { Text("Rename tag") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        singleLine = true,
                        label = { Text("Name") },
                    )
                    Text("Color", style = MaterialTheme.typography.labelLarge)
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        colors.forEach { option ->
                            val swatch = option?.let { Color(it) }
                                ?: MaterialTheme.colorScheme.surfaceVariant
                            androidx.compose.foundation.layout.Box(
                                Modifier.size(32.dp)
                                    .background(swatch, CircleShape)
                                    .then(
                                        if (color == option) {
                                            Modifier.border(
                                                3.dp,
                                                MaterialTheme.colorScheme.primary,
                                                CircleShape,
                                            )
                                        } else Modifier
                                    )
                                    .clickable { color = option },
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(
                    enabled = name.isNotBlank(),
                    onClick = { viewModel.update(tag, name, color); editingTag = null },
                ) { Text("Save") }
            },
            dismissButton = {
                TextButton(onClick = { editingTag = null }) { Text("Cancel") }
            },
        )
    }
}

@Composable
fun EmptyManagerRow(text: String) {
    Text(text, Modifier.padding(horizontal = 24.dp, vertical = 12.dp))
}

@Composable
fun ManagementScaffold(
    title: String,
    onBack: () -> Unit,
    actions: (@Composable RowScope.() -> Unit)? = null,
    content: androidx.compose.foundation.lazy.LazyListScope.() -> Unit,
) {
    RYScaffold(
        containerColor = MaterialTheme.colorScheme.surface onLight MaterialTheme.colorScheme.inverseOnSurface,
        navigationIcon = {
            FeedbackIconButton(
                imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                contentDescription = stringResource(R.string.back),
                tint = MaterialTheme.colorScheme.onSurface,
                onClick = onBack,
            )
        },
        actions = actions,
        content = {
            LazyColumn {
                item { DisplayText(text = title, desc = "") }
                content()
                item {
                    Spacer(Modifier.height(24.dp))
                    Spacer(Modifier.windowInsetsBottomHeight(WindowInsets.navigationBars))
                }
            }
        },
    )
}
