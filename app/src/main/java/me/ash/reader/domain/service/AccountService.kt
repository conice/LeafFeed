package me.ash.reader.domain.service

import android.content.Context
import androidx.datastore.preferences.core.intPreferencesKey
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import me.ash.reader.R
import me.ash.reader.domain.model.account.Account
import me.ash.reader.domain.model.account.AccountType
import me.ash.reader.domain.model.group.Group
import me.ash.reader.domain.repository.AccountDao
import me.ash.reader.domain.repository.ArticleDao
import me.ash.reader.domain.repository.ArticleCollectionDao
import me.ash.reader.domain.repository.FeedDao
import me.ash.reader.domain.repository.GroupDao
import me.ash.reader.infrastructure.ai.AiSummaryCache
import me.ash.reader.infrastructure.di.ApplicationScope
import me.ash.reader.infrastructure.preference.SettingsProvider
import me.ash.reader.ui.ext.PreferencesKey
import me.ash.reader.ui.ext.dataStore
import me.ash.reader.ui.ext.getDefaultGroupId
import me.ash.reader.ui.ext.put
import me.ash.reader.ui.ext.showToastSuspend

class AccountService
@Inject
constructor(
    @ApplicationContext private val context: Context,
    private val accountDao: AccountDao,
    private val groupDao: GroupDao,
    private val feedDao: FeedDao,
    private val articleDao: ArticleDao,
    private val articleCollectionDao: ArticleCollectionDao,
    private val aiSummaryCache: AiSummaryCache,
    @ApplicationScope private val coroutineScope: CoroutineScope,
    private val settingsProvider: SettingsProvider,
) {

    private val accountIdKey = intPreferencesKey(PreferencesKey.currentAccountId)

    val currentAccountIdFlow =
        settingsProvider.preferencesFlow
            .map { it[accountIdKey] }
            .stateIn(scope = coroutineScope, started = SharingStarted.Eagerly, initialValue = null)

    val currentAccountFlow =
        currentAccountIdFlow
            .combine(getAccounts()) { id, accounts ->
                id?.let { accounts.firstOrNull { it.id == id } }
            }
            .stateIn(scope = coroutineScope, SharingStarted.Eagerly, initialValue = null)

    fun getAccounts(): Flow<List<Account>> = accountDao.queryAllAsFlow()

    fun getAccountFlowById(accountId: Int): Flow<Account?> = accountDao.queryAccount(accountId)

    suspend fun getAccountById(accountId: Int): Account? = accountDao.queryById(accountId)

    fun getCurrentAccount(): Account =
        checkNotNull(currentAccountFlow.value) { "Current account has not been initialized" }

    fun getCurrentAccountId(): Int =
        checkNotNull(currentAccountIdFlow.value) { "Current account has not been initialized" }

    suspend fun awaitCurrentAccount(): Account = currentAccountFlow.first { it != null }!!

    suspend fun awaitCurrentAccountId(): Int = currentAccountIdFlow.first { it != null }!!

    suspend fun ensureCurrentAccount(): Pair<Account, Boolean> {
        val accounts = accountDao.queryAll()
        if (accounts.isEmpty()) return initWithDefaultAccount() to true

        val requestedId = settingsProvider.awaitPreferences()[accountIdKey]
        val selected = accounts.firstOrNull { it.id == requestedId } ?: accounts.first()
        if (selected.id != requestedId) switch(selected)
        return selected to false
    }

    suspend fun addAccount(account: Account): Account {
        val id = accountDao.insert(account).toInt()
        return account.copy(id = id).also {
            when (it.type) {
                AccountType.Local -> {
                    groupDao.insert(
                        Group(
                            id = it.id!!.getDefaultGroupId(),
                            name = context.getString(R.string.defaults),
                            accountId = it.id!!,
                        )
                    )
                }
            }
            context.dataStore.put(PreferencesKey.currentAccountId, it.id!!)
            context.dataStore.put(PreferencesKey.currentAccountType, it.type.id)
        }
    }

    private fun getDefaultAccount(): Account =
        Account(type = AccountType.Local, name = context.getString(R.string.app_name))

    private suspend fun addDefaultAccount(): Account = addAccount(getDefaultAccount())

    private suspend fun initWithDefaultAccount(): Account = addDefaultAccount()

    fun getDefaultGroup(): Group =
        getCurrentAccountId().let {
            Group(
                id = it.getDefaultGroupId(),
                name = context.getString(R.string.defaults),
                accountId = it,
            )
        }

    suspend fun update(accountId: Int, block: Account.() -> Account) {
        accountDao.queryById(accountId)?.let { accountDao.update(it.run(block)) }
    }

    suspend fun update(account: Account) = accountDao.update(account)

    suspend fun delete(accountId: Int) {
        if (accountDao.queryAll().size == 1) {
            context.showToastSuspend(context.getString(R.string.must_have_an_account))
            return
        }
        accountDao.queryById(accountId)?.let {
            articleCollectionDao.deleteByAccount(accountId)
            articleDao.deleteByAccountId(accountId)
            feedDao.deleteByAccountId(accountId)
            groupDao.deleteByAccountId(accountId)
            accountDao.delete(it)
            aiSummaryCache.clearAccount(accountId)
            accountDao.queryAll().getOrNull(0)?.let {
                context.dataStore.put(PreferencesKey.currentAccountId, it.id!!)
                context.dataStore.put(PreferencesKey.currentAccountType, it.type.id)
            }
        }
    }

    suspend fun switch(account: Account) {
        context.dataStore.put(PreferencesKey.currentAccountId, account.id!!)
        context.dataStore.put(PreferencesKey.currentAccountType, account.type.id)
    }
}
