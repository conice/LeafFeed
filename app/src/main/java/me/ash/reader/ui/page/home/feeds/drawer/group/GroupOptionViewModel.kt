package me.ash.reader.ui.page.home.feeds.drawer.group

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import me.ash.reader.domain.data.ArticleRuleRepository
import me.ash.reader.domain.model.article.RuleScope
import me.ash.reader.domain.model.article.RuleType
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import me.ash.reader.domain.model.group.Group
import me.ash.reader.domain.service.RssService
import me.ash.reader.infrastructure.di.ApplicationScope
import me.ash.reader.infrastructure.di.IODispatcher
import me.ash.reader.infrastructure.di.MainDispatcher
import javax.inject.Inject

@HiltViewModel
class GroupOptionViewModel @Inject constructor(
    val rssService: RssService,
    @MainDispatcher
    private val mainDispatcher: CoroutineDispatcher,
    @IODispatcher
    private val ioDispatcher: CoroutineDispatcher,
    @ApplicationScope
    private val applicationScope: CoroutineScope,
    private val articleRuleRepository: ArticleRuleRepository,
) : ViewModel() {
    val articleRules = articleRuleRepository.rules.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    private val _groupOptionUiState = MutableStateFlow(GroupOptionUiState())
    val groupOptionUiState: StateFlow<GroupOptionUiState> = _groupOptionUiState.asStateFlow()

    init {
        viewModelScope.launch(ioDispatcher) {
            rssService.get().pullGroups().collect { groups ->
                _groupOptionUiState.update { it.copy(groups = groups) }
            }
        }
    }

    fun fetchGroup(group: Group) {
        _groupOptionUiState.update { it.copy(group = group) }
        viewModelScope.launch(ioDispatcher) {
            val refreshedGroup = rssService.get().findGroupById(group.id) ?: return@launch
            _groupOptionUiState.update { state ->
                if (state.group?.id == group.id) state.copy(group = refreshedGroup) else state
            }
        }
    }


    fun allAllowNotification(isNotification: Boolean, callback: () -> Unit = {}) {
        _groupOptionUiState.value.group?.let {
            viewModelScope.launch(ioDispatcher) {
                rssService.get().groupAllowNotification(it, isNotification)
                withContext(mainDispatcher) {
                    callback()
                }
            }
        }
    }

    fun showAllAllowNotificationDialog() {
        _groupOptionUiState.update { it.copy(allAllowNotificationDialogVisible = true) }
    }

    fun hideAllAllowNotificationDialog() {
        _groupOptionUiState.update { it.copy(allAllowNotificationDialogVisible = false) }
    }

    fun allParseFullContent(isFullContent: Boolean, callback: () -> Unit = {}) {
        _groupOptionUiState.value.group?.let {
            viewModelScope.launch(ioDispatcher) {
                rssService.get().groupParseFullContent(it, isFullContent)
                withContext(mainDispatcher) {
                    callback()
                }
            }
        }
    }

    fun showAllParseFullContentDialog() {
        _groupOptionUiState.update { it.copy(allParseFullContentDialogVisible = true) }
    }
        
    fun hideAllParseFullContentDialog() {
        _groupOptionUiState.update { it.copy(allParseFullContentDialogVisible = false) }
    }

    fun allOpenInBrowser(isBrowser: Boolean, callback: () -> Unit = {}) {
        _groupOptionUiState.value.group?.let {
            viewModelScope.launch(ioDispatcher) {
                rssService.get().groupOpenInBrowser(it, isBrowser)
                withContext(mainDispatcher) {
                    callback()
                }
            }
        }
    }

    fun showAllOpenInBrowserDialog() {
        _groupOptionUiState.update { it.copy(allOpenInBrowserDialogVisible = true) }
    }

    fun hideAllOpenInBrowserDialog() {
        _groupOptionUiState.update { it.copy(allOpenInBrowserDialogVisible = false) }
    }

    fun delete(callback: () -> Unit = {}) {
        _groupOptionUiState.value.group?.let {
            applicationScope.launch(ioDispatcher) {
                rssService.get().deleteGroup(it)
                withContext(mainDispatcher) {
                    callback()
                }
            }
        }
    }

    fun showDeleteDialog() {
        _groupOptionUiState.update { it.copy(deleteDialogVisible = true) }
    }

    fun hideDeleteDialog() {
        _groupOptionUiState.update { it.copy(deleteDialogVisible = false) }
    }

    fun showClearDialog() {
        _groupOptionUiState.update { it.copy(clearDialogVisible = true) }
    }

    fun hideClearDialog() {
        _groupOptionUiState.update { it.copy(clearDialogVisible = false) }
    }

    fun clear(callback: () -> Unit = {}) {
        _groupOptionUiState.value.group?.let {
            viewModelScope.launch(ioDispatcher) {
                rssService.get().deleteArticles(group = it)
                withContext(mainDispatcher) {
                    callback()
                }
            }
        }
    }

    fun allMoveToGroup(callback: () -> Unit) {
        _groupOptionUiState.value.group?.let { group ->
            _groupOptionUiState.value.targetGroup?.let { targetGroup ->
                viewModelScope.launch(ioDispatcher) {
                    rssService.get().groupMoveToTargetGroup(group, targetGroup)
                    withContext(mainDispatcher) {
                        callback()
                    }
                }
            }
        }
    }

    fun showAllMoveToGroupDialog(targetGroup: Group) {
        _groupOptionUiState.update {
            it.copy(
                targetGroup = targetGroup,
                allMoveToGroupDialogVisible = true,
            )
        }
    }

    fun hideAllMoveToGroupDialog() {
        _groupOptionUiState.update {
            it.copy(
                targetGroup = null,
                allMoveToGroupDialogVisible = false,
            )
        }
    }

    fun rename() {
        _groupOptionUiState.value.group?.let {
            applicationScope.launch {
                rssService.get().renameGroup(it.copy(name = _groupOptionUiState.value.newName))
                _groupOptionUiState.update { it.copy(renameDialogVisible = false) }
            }
        }
    }

    fun showRenameDialog() {
        _groupOptionUiState.update {
            it.copy(
                renameDialogVisible = true,
                newName = _groupOptionUiState.value.group?.name ?: "",
            )
        }
    }

    fun hideRenameDialog() {
        _groupOptionUiState.update {
            it.copy(
                renameDialogVisible = false,
                newName = "",
            )
        }
    }

    fun inputNewName(content: String) {
        _groupOptionUiState.update { it.copy(newName = content) }
    }
    fun showRuleDialog(type: RuleType) = _groupOptionUiState.update { it.copy(ruleDialogType = type) }
    fun hideRuleDialog() = _groupOptionUiState.update { it.copy(ruleDialogType = null) }
    fun addRule(pattern: String, isRegex: Boolean, caseSensitive: Boolean, isGlobal: Boolean) {
        val group = _groupOptionUiState.value.group ?: return
        val type = _groupOptionUiState.value.ruleDialogType ?: return
        val scope = if (isGlobal) RuleScope.GLOBAL else RuleScope.GROUP
        viewModelScope.launch {
            articleRuleRepository.add(
                group.accountId,
                scope,
                if (isGlobal) "" else group.id,
                type,
                pattern,
                isRegex,
                caseSensitive,
            )
        }
    }
    fun editRule(
        id: String,
        pattern: String,
        isRegex: Boolean,
        caseSensitive: Boolean,
        isGlobal: Boolean,
    ) {
        val group = _groupOptionUiState.value.group ?: return
        viewModelScope.launch {
            articleRuleRepository.edit(
                id,
                if (isGlobal) RuleScope.GLOBAL else RuleScope.GROUP,
                if (isGlobal) "" else group.id,
                pattern,
                isRegex,
                caseSensitive,
            )
        }
    }
    fun reorderRules(ids: List<String>) {
        viewModelScope.launch { articleRuleRepository.reorder(ids) }
    }

    fun deleteRule(id: String) { viewModelScope.launch { articleRuleRepository.delete(id) } }
}

data class GroupOptionUiState(
    val group: Group? = null,
    val targetGroup: Group? = null,
    val groups: List<Group> = emptyList(),
    val allAllowNotificationDialogVisible: Boolean = false,
    val allParseFullContentDialogVisible: Boolean = false,
    val allOpenInBrowserDialogVisible: Boolean = false,
    val allMoveToGroupDialogVisible: Boolean = false,
    val deleteDialogVisible: Boolean = false,
    val clearDialogVisible: Boolean = false,
    val newName: String = "",
    val renameDialogVisible: Boolean = false,
    val ruleDialogType: RuleType? = null,
)
