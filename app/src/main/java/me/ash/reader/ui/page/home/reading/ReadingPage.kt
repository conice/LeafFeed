package me.ash.reader.ui.page.home.reading

import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.isSpecified
import androidx.compose.ui.unit.sp
import kotlin.math.abs
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import me.ash.reader.R
import me.ash.reader.domain.model.article.ArticleNote
import me.ash.reader.domain.model.article.ArticleTagLabel
import me.ash.reader.domain.model.article.ArticleRuleDiagnostic
import me.ash.reader.infrastructure.android.TextToSpeechManager
import me.ash.reader.infrastructure.preference.LocalPullToSwitchArticle
import me.ash.reader.infrastructure.preference.LocalReadingAutoHideToolbar
import me.ash.reader.infrastructure.preference.LocalReadingTextLineHeight
import me.ash.reader.infrastructure.preference.FeatureSettings
import me.ash.reader.infrastructure.preference.toFeatureSettings
import me.ash.reader.ui.component.AiSummaryDialog
import me.ash.reader.ui.component.CompactStatusIndicator
import me.ash.reader.ui.ext.collectAsStateValue
import me.ash.reader.ui.ext.dataStore
import me.ash.reader.ui.ext.showToast
import me.ash.reader.ui.motion.Direction
import me.ash.reader.ui.motion.sharedYAxisTransitionSlow
import me.ash.reader.ui.page.adaptive.ArticleListReaderViewModel
import me.ash.reader.ui.page.adaptive.NavigationAction
import me.ash.reader.ui.page.adaptive.ReaderState
import me.ash.reader.ui.page.home.reading.tts.TtsButton

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun ReadingPage(
    //    navController: NavHostController,
    viewModel: ArticleListReaderViewModel,
    navigationAction: NavigationAction,
    onLoadArticle: (String, Int) -> Unit,
    onNavAction: (NavigationAction) -> Unit,
    onNavigateToStylePage: () -> Unit,
) {
    val context = LocalContext.current
    val featureSettings = context.dataStore.data.map { it.toFeatureSettings() }
        .collectAsStateValue(FeatureSettings())
    val isPullToSwitchArticleEnabled = LocalPullToSwitchArticle.current.value
    val readingUiState = viewModel.readingUiState.collectAsStateValue()
    val readerState = viewModel.readerStateStateFlow.collectAsStateValue()
    val aiSummaryState = viewModel.aiSummaryState.collectAsStateValue()
    val motionScheme = MaterialTheme.motionScheme

    var isReaderScrollingDown by remember { mutableStateOf(false) }
    var noteDialogVisible by remember { mutableStateOf(false) }
    var noteText by remember { mutableStateOf("") }
    var noteQuote by remember { mutableStateOf("") }
    var notes by remember { mutableStateOf<List<ArticleNote>>(emptyList()) }
    var tagDialogVisible by remember { mutableStateOf(false) }
    var tagText by remember { mutableStateOf("") }
    var tags by remember { mutableStateOf<List<ArticleTagLabel>>(emptyList()) }
    var ruleDiagnostics by remember { mutableStateOf<List<ArticleRuleDiagnostic>?>(null) }
    var showFullScreenImageViewer by remember { mutableStateOf(false) }
    var transcriptSheetUrl by remember { mutableStateOf<String?>(null) }

    var currentImageData by remember { mutableStateOf(ImageData()) }

    val isShowToolBar =
        if (LocalReadingAutoHideToolbar.current.value) {
            readerState.articleId != null && !isReaderScrollingDown
        } else {
            true
        }

    LaunchedEffect(noteDialogVisible, readerState.articleId) {
        if (noteDialogVisible) notes = viewModel.queryCurrentNotes()
    }
    LaunchedEffect(tagDialogVisible, readerState.articleId) {
        if (tagDialogVisible) tags = viewModel.queryCurrentTags()
    }
    LaunchedEffect(
        readerState.articleId,
        readingUiState.articleWithFeed?.article?.transcriptUrl,
        featureSettings.podcastAutoTranscript,
    ) {
        if (featureSettings.podcastAutoTranscript) {
            transcriptSheetUrl = readingUiState.articleWithFeed?.article?.transcriptUrl
        }
    }

    var showTopDivider by remember { mutableStateOf(false) }

    //    LaunchedEffect(readerState.listIndex) {
    //        readerState.listIndex?.let {
    //            navController.previousBackStackEntry?.savedStateHandle?.set("articleIndex", it)
    //        }
    //    }

    var bringToTop by remember { mutableStateOf(false) }

    val isArticleContentAvailable =
        (readerState.content is ReaderState.Description ||
            readerState.content is ReaderState.FullContent) &&
            !readerState.content.text.isNullOrBlank()

    Scaffold(
        containerColor = MaterialTheme.colorScheme.surface,
        content = { paddings ->
            Box(modifier = Modifier.fillMaxSize()) {
                if (readerState.articleId != null) {
                    TopBar(
                        isShow = isShowToolBar,
                        isScrolled = showTopDivider,
                        title = readerState.title,
                        link = readerState.link,
                        isAiSummaryAvailable = isArticleContentAvailable,
                        showNotesAction = featureSettings.showNotesAction,
                        showTagsAction = featureSettings.showArticleTags,
                        onClick = { bringToTop = true },
                        navigationAction = navigationAction,
                        onNavButtonClick = onNavAction,
                        onAiSummary = {
                            viewModel.summarizeCurrentArticle(readerState.content.text.orEmpty())
                        },
                        onAddNote = {
                            noteText = ""
                            noteQuote = ""
                            noteDialogVisible = true
                        },
                        onManageTags = {
                            tagText = ""
                            tagDialogVisible = true
                        },
                        onExplainRules = {
                            viewModel.diagnoseCurrentArticle { ruleDiagnostics = it }
                        },
                        onNavigateToStylePage = onNavigateToStylePage,
                    )
                }

                val isNextArticleAvailable = readerState.nextArticle != null
                val isPreviousArticleAvailable = readerState.previousArticle != null

                if (readerState.articleId != null) {
                    // Content
                    AnimatedContent(
                        targetState = readerState,
                        contentKey = { state -> state.articleId to state.content::class },
                        transitionSpec = {
                            val direction =
                                when {
                                    initialState.nextArticle?.articleId == targetState.articleId ->
                                        Direction.Forward
                                    initialState.previousArticle?.articleId ==
                                        targetState.articleId -> Direction.Backward
                                    initialState.articleId == targetState.articleId -> {
                                        when (targetState.content) {
                                            is ReaderState.Description -> Direction.Backward
                                            else -> Direction.Forward
                                        }
                                    }

                                    else -> Direction.Forward
                                }
                            sharedYAxisTransitionSlow(direction, motionScheme)
                        },
                        label = "readingArticleContent",
                    ) {
                        remember { it }
                            .run {
                                val displayedContent =
                                    content.text.orEmpty()
                                val state =
                                    rememberPullToLoadState(
                                        key = content,
                                        onLoadNext =
                                            if (isNextArticleAvailable) {
                                                {
                                                    val (id, index) = readerState.nextArticle
                                                    onLoadArticle(id, index)
                                                }
                                            } else null,
                                        onLoadPrevious =
                                            if (isPreviousArticleAvailable) {
                                                {
                                                    val (id, index) = readerState.previousArticle
                                                    onLoadArticle(id, index)
                                                }
                                            } else null,
                                    )

                                val listState =
                                    rememberSaveable(
                                        inputs = arrayOf(content),
                                        saver = LazyListState.Saver,
                                    ) {
                                        LazyListState()
                                    }

                                val scrollState = rememberScrollState()

                                val scope = rememberCoroutineScope()

                                LaunchedEffect(
                                    articleId,
                                    featureSettings.markReadAtEnd,
                                    readingUiState.isUnread,
                                ) {
                                    if (!featureSettings.markReadAtEnd || !readingUiState.isUnread) {
                                        return@LaunchedEffect
                                    }
                                    snapshotFlow {
                                        val webViewAtEnd =
                                            scrollState.maxValue > 0 &&
                                                scrollState.value >= scrollState.maxValue
                                        val layout = listState.layoutInfo
                                        val nativeAtEnd =
                                            layout.totalItemsCount > 1 &&
                                                layout.visibleItemsInfo.lastOrNull()?.index ==
                                                    layout.totalItemsCount - 1
                                        webViewAtEnd || nativeAtEnd
                                    }.filter { it }.first()
                                    viewModel.updateReadStatus(false)
                                }

                                LaunchedEffect(bringToTop) {
                                    if (bringToTop) {
                                        scope
                                            .launch {
                                                if (scrollState.value != 0) {
                                                    scrollState.animateScrollTo(0)
                                                } else if (listState.firstVisibleItemIndex != 0) {
                                                    listState.animateScrollToItem(0)
                                                }
                                            }
                                            .invokeOnCompletion { bringToTop = false }
                                    }
                                }

                                showTopDivider =
                                    snapshotFlow {
                                            scrollState.value >= 120 ||
                                                listState.firstVisibleItemIndex != 0
                                        }
                                        .collectAsStateValue(initial = false)

                                CompositionLocalProvider(
                                    LocalTextStyle provides
                                        LocalTextStyle.current.run {
                                            merge(
                                                lineHeight =
                                                    if (lineHeight.isSpecified)
                                                        (lineHeight.value *
                                                                LocalReadingTextLineHeight.current)
                                                            .sp
                                                    else TextUnit.Unspecified
                                            )
                                        }
                                ) {
                                    Box(
                                        modifier = Modifier.fillMaxSize(),
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        Content(
                                            modifier =
                                                Modifier.pullToLoad(
                                                    state = state,
                                                    onScroll = { f ->
                                                        if (abs(f) > 2f)
                                                            isReaderScrollingDown = f < 0f
                                                    },
                                                    enabled = isPullToSwitchArticleEnabled,
                                                ),
                                            contentPadding = paddings,
                                            content = displayedContent,
                                            feedName = feedName,
                                            title = title.orEmpty(),
                                            author = author,
                                            link = link,
                                            publishedDate = publishedDate,
                                            isLoading = content is ReaderState.Loading,
                                            scrollState = scrollState,
                                            listState = listState,
                                            onImageClick = { imgUrl, altText ->
                                                currentImageData = ImageData(imgUrl, altText)
                                                showFullScreenImageViewer = true
                                            },
                                        )
                                        PullToLoadIndicator(
                                            state = state,
                                            canLoadPrevious = isPreviousArticleAvailable,
                                            canLoadNext = isNextArticleAvailable,
                                        )
                                    }
                                }
                            }
                    }
                }
                // Bottom Bar
                if (readerState.articleId != null) {
                    BottomBar(
                        isShow = isShowToolBar,
                        isUnread = readingUiState.isUnread,
                        isStarred = readingUiState.isStarred,
                        isFullContent = readerState.content is ReaderState.FullContent ||
                            readerState.content is ReaderState.Error,
                        isFullContentLoading = readerState.content is ReaderState.Loading,
                        isReadLater = readingUiState.isReadLater,
                        showReadLaterAction = featureSettings.showReadLaterIcon,
                        onUnread = { viewModel.updateReadStatus(it) },
                        onStarred = { viewModel.updateStarredStatus(it) },
                        onReadLater = viewModel::updateReadLaterStatus,
                        onFullContent = {
                            if (it) viewModel.renderFullContent()
                            else viewModel.renderDescriptionContent()
                        },
                        ttsButton = {
                            TtsButton(
                                onClick = {
                                    when (it) {
                                        TextToSpeechManager.State.Error -> {
                                            context.showToast("TextToSpeech initialization failed")
                                        }

                                        TextToSpeechManager.State.Idle -> {
                                            viewModel.textToSpeechManager.readHtml(
                                                readerState.content.text.orEmpty()
                                            )
                                        }

                                        is TextToSpeechManager.State.Reading -> {
                                            viewModel.textToSpeechManager.stop()
                                        }

                                        TextToSpeechManager.State.Preparing -> {
                                            /* no-op */
                                        }
                                    }
                                },
                                state =
                                    viewModel.textToSpeechManager.stateFlow.collectAsStateValue(),
                            )
                        },
                    )
                }
                CompactStatusIndicator(
                    visible = !aiSummaryState.visible &&
                        aiSummaryState.loading,
                    message = stringResource(R.string.ai_summary),
                )
            }
        },
    )
    AiSummaryDialog(
        visible = aiSummaryState.visible,
        loading = aiSummaryState.loading,
        summary = aiSummaryState.summary,
        failure = aiSummaryState.failure,
        onDismiss = viewModel::hideAiSummary,
        sourceDescription = stringResource(R.string.ai_summary_source_article),
        onRegenerate = {
            viewModel.summarizeCurrentArticle(
                readerState.content.text.orEmpty(),
                forceRefresh = true,
            )
        },
    )
    if (noteDialogVisible) {
        AlertDialog(
            onDismissRequest = { noteDialogVisible = false },
            title = { Text(stringResource(R.string.add_note)) },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    notes.forEach { note ->
                        Row(Modifier.fillMaxWidth()) {
                            Text(
                                buildString {
                                    if (note.quote.isNotBlank()) append("“${note.quote}”\n")
                                    append(note.note)
                                },
                                modifier = Modifier.weight(1f),
                                style = MaterialTheme.typography.bodyMedium,
                            )
                            TextButton(
                                onClick = {
                                    viewModel.deleteNote(note)
                                    notes = notes.filterNot { it.id == note.id }
                                }
                            ) { Text(stringResource(R.string.delete)) }
                        }
                        Spacer(Modifier.height(12.dp))
                    }
                    OutlinedTextField(
                        value = noteQuote,
                        onValueChange = { noteQuote = it },
                        label = { Text("Quoted text") },
                        minLines = 2,
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = noteText,
                        onValueChange = { noteText = it },
                        label = { Text(stringResource(R.string.note)) },
                        minLines = 3,
                    )
                }
            },
            confirmButton = {
                TextButton(
                    enabled = noteText.isNotBlank() || noteQuote.isNotBlank(),
                    onClick = {
                        viewModel.saveNote(noteText, noteQuote)
                        noteDialogVisible = false
                    },
                ) { Text(stringResource(R.string.save)) }
            },
            dismissButton = {
                TextButton(onClick = { noteDialogVisible = false }) {
                    Text(stringResource(R.string.cancel))
                }
            },
        )
    }
    if (tagDialogVisible) {
        AlertDialog(
            onDismissRequest = { tagDialogVisible = false },
            title = { Text(stringResource(R.string.manage_tags)) },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    tags.forEach { tag ->
                        Row(Modifier.fillMaxWidth()) {
                            Text(
                                "#${tag.name}",
                                modifier = Modifier.weight(1f),
                                style = MaterialTheme.typography.bodyMedium,
                            )
                            TextButton(
                                onClick = {
                                    viewModel.removeTag(tag)
                                    tags = tags.filterNot { it.id == tag.id }
                                }
                            ) { Text(stringResource(R.string.remove)) }
                        }
                        Spacer(Modifier.height(8.dp))
                    }
                    OutlinedTextField(
                        value = tagText,
                        onValueChange = { tagText = it },
                        label = { Text(stringResource(R.string.new_tag)) },
                    )
                }
            },
            confirmButton = {
                TextButton(
                    enabled = tagText.isNotBlank(),
                    onClick = {
                        viewModel.addTag(tagText)
                        tagDialogVisible = false
                    },
                ) { Text(stringResource(R.string.add)) }
            },
            dismissButton = {
                TextButton(onClick = { tagDialogVisible = false }) {
                    Text(stringResource(R.string.close))
                }
            },
        )
    }

    ruleDiagnostics?.let { diagnostics ->
        AlertDialog(
            onDismissRequest = { ruleDiagnostics = null },
            title = { Text("Article rule diagnostics") },
            text = {
                if (diagnostics.isEmpty()) {
                    Text("No rules apply to this article.")
                } else {
                    LazyColumn(Modifier.heightIn(max = 400.dp)) {
                        items(diagnostics, key = { it.rule.id }) { diagnostic ->
                            Text(
                                buildString {
                                    append(if (diagnostic.matched) "Matched: " else "Not matched: ")
                                    append(diagnostic.rule.pattern)
                                    append(" (")
                                    append(diagnostic.rule.type.name.lowercase())
                                    append(")")
                                },
                                color = if (diagnostic.matched) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                },
                                modifier = Modifier.padding(vertical = 4.dp),
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { ruleDiagnostics = null }) { Text("Close") }
            },
        )
    }
    if (showFullScreenImageViewer) {

        ReaderImageViewer(
            imageData = currentImageData,
            onDownloadImage = {
                viewModel.downloadImage(
                    it,
                    onSuccess = { context.showToast(context.getString(R.string.image_saved)) },
                    onFailure = { throwable ->
                        context.showToast(
                            context.getString(
                                R.string.image_download_failed,
                                throwable.message ?: context.getString(R.string.unknown),
                            ),
                        )
                    },
                )
            },
            onDismissRequest = { showFullScreenImageViewer = false },
        )
    }
    transcriptSheetUrl?.let { url ->
        PodcastTranscriptSheet(
            url = url,
            onDismiss = { transcriptSheetUrl = null },
            load = viewModel::loadPodcastTranscript,
            onSeek = viewModel.podcastPlayer::seekTo,
        )
    }
}
