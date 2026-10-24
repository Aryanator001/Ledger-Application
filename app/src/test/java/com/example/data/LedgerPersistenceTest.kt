package com.example.data

import android.app.Application
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class)
class LedgerPersistenceTest {
    @get:Rule val temporaryFolder = TemporaryFolder()
    private lateinit var database: AppDatabase
    private lateinit var preferences: UserPreferences
    private lateinit var preferenceScope: CoroutineScope
    private lateinit var sessions: LedgerSessionManager
    private lateinit var repository: TransactionRepository

    @Before fun setup() {
        database = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), AppDatabase::class.java)
            .allowMainThreadQueries().build()
        preferenceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        preferences = UserPreferences(PreferenceDataStoreFactory.create(scope = preferenceScope) {
            temporaryFolder.root.resolve("test.preferences_pb")
        })
        sessions = LedgerSessionManager(database, preferences)
        repository = TransactionRepository(database.transactionDao(), database.categoryDao(), database.accountDao(), preferences)
    }

    @After fun close() { database.close(); preferenceScope.cancel() }

    private suspend fun user(uid: String, scope: String) {
        database.userProfileDao().insertProfile(UserProfile(uid, uid, "$uid@example.test"))
        database.ledgerScopeDao().insertScope(LedgerScope(scope, uid, "Personal", "PERSONAL"))
    }

    private suspend fun transaction(id: String = "tx", amount: Double = 10.0, account: String = "default_cash") =
        Transaction(id, "Lunch", amount, "food", account, "EXPENSE", 1_700_000_000_000)

    @Test fun profileEditPreservesScopesAndTransactions() = runBlocking {
        user("alice", "scope-a")
        sessions.activateUser("alice")
        database.categoryDao().insertCategory(Category("food", "Food", "restaurant", "#000000", "EXPENSE"))
        repository.insertTransaction(transaction())
        database.userProfileDao().insertProfile(UserProfile("alice", "Updated", "alice@example.test"))
        assertNotNull(database.ledgerScopeDao().getScopeForProfile("scope-a", "alice"))
        assertNotNull(repository.getTransactionById("tx"))
        assertEquals("Updated", database.userProfileDao().getUserProfile("alice")?.displayName)
    }

    @Test fun accountSwitchRestoresOnlyOwnedScopeAndHidesOldTransactions() = runBlocking {
        user("alice", "scope-a"); user("bob", "scope-b")
        sessions.activateUser("alice")
        database.categoryDao().insertCategory(Category("food", "Food", "restaurant", "#000000", "EXPENSE"))
        repository.insertTransaction(transaction())
        sessions.clear()
        assertNull(preferences.activeUserId.first())
        assertNull(preferences.activeLedgerScopeId.first())
        assertTrue(repository.allTransactions.first().isEmpty())
        assertEquals("scope-b", sessions.activateUser("bob"))
        assertEquals("bob", preferences.activeUserId.first())
        assertTrue(repository.allTransactions.first().isEmpty())
        assertEquals("scope-a", sessions.activateUser("alice"))
        assertEquals(listOf("tx"), repository.allTransactions.first().map { it.id })
    }

    @Test fun foreignStoredScopeIsRejectedAndNewUserRequiresSetup() = runBlocking {
        user("alice", "scope-a"); user("bob", "scope-b")
        preferences.setActiveLedger("bob", "scope-a")
        assertEquals("scope-b", sessions.activateUser("bob"))
        assertNull(sessions.activateUser("new-user"))
        assertNull(preferences.activeLedgerScopeId.first())
        assertTrue(repository.allAccounts.first().isEmpty())
    }

    @Test fun scopedDefaultsPreserveExistingAccountAndTransactionReferences() = runBlocking {
        user("alice", "scope-a"); user("bob", "scope-b")
        database.accountDao().insertAccount(Account("default_cash", "Alice wallet", "CASH", "#000000",
            startingBalance = 123.0, ledgerScopeId = "scope-a"))
        sessions.activateUser("alice")
        database.categoryDao().insertCategory(Category("food", "Food", "restaurant", "#000000", "EXPENSE"))
        repository.insertTransaction(transaction())
        sessions.activateUser("bob")
        val bobAccount = DefaultAccounts.resolve(database.accountDao(), "default_cash", "scope-b")!!
        assertNotEquals("default_cash", bobAccount.id)
        assertEquals("Alice wallet", database.accountDao().getAccountById("default_cash", "scope-a")?.name)
        assertEquals(123.0, database.accountDao().getAccountById("default_cash", "scope-a")!!.startingBalance, 0.0)
        assertEquals("default_cash", database.transactionDao().getTransactionById("tx", "scope-a")!!.accountId)
        DefaultAccounts.ensure(database.accountDao(), "scope-b")
        assertEquals(3, database.accountDao().getAccountCount("scope-b"))
    }

    @Test fun newLedgerTransactionsResolveAliasesToItsOwnAccounts() = runBlocking {
        user("alice", "scope-a"); user("bob", "scope-b")
        sessions.activateUser("bob")
        database.categoryDao().insertCategory(Category("food", "Food", "restaurant", "#000000", "EXPENSE"))
        repository.insertTransaction(transaction(account = "upi_account"))
        assertEquals(DefaultAccounts.scopedId("upi_account", "scope-b"), repository.getTransactionById("tx")!!.accountId)
        assertEquals("upi", repository.getTransactionById("tx")!!.paymentMethod)
    }

    @Test fun writesRejectInvalidAmountsMissingScopeAndForeignAccounts() = runBlocking {
        val validator = com.example.ai.validation.TransactionValidator(repository)
        val unnamed = com.example.ai.model.CandidateTransaction(10.0, com.example.ai.model.Direction.EXPENSE,
            null, "food", "cash", null, rawTranscript = "paid ten", confidence = com.example.ai.model.ConfidenceScore(1f))
        assertTrue(validator.validateFields(unnamed).contains(com.example.ai.model.ValidationError.EntityMissing))
        user("alice", "scope-a"); user("bob", "scope-b")
        sessions.activateUser("alice")
        database.categoryDao().insertCategory(Category("food", "Food", "restaurant", "#000000", "EXPENSE"))
        for (amount in listOf(0.0, -1.0, Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY)) {
            assertTrue(runCatching { repository.insertTransaction(transaction(amount = amount)) }.isFailure)
        }
        val foreign = DefaultAccounts.scopedId("default_cash", "scope-a")
        sessions.activateUser("bob")
        assertTrue(runCatching { repository.insertTransaction(transaction(account = foreign)) }.isFailure)
        assertTrue(runCatching { repository.insertTransaction(transaction().copy(ledgerScopeId = "scope-a")) }.isFailure)
        sessions.clear()
        assertTrue(runCatching { repository.insertTransaction(transaction()) }.isFailure)
        assertEquals(0, database.transactionDao().getTransactionCount("scope-a"))
        assertEquals(0, database.transactionDao().getTransactionCount("scope-b"))
    }

    @Test fun updateAffectingNoRowsReportsFailure() = runBlocking {
        user("alice", "scope-a")
        sessions.activateUser("alice")
        database.categoryDao().insertCategory(Category("food", "Food", "restaurant", "#000000", "EXPENSE"))
        repository.insertTransaction(transaction())
        val original = repository.getTransactionById("tx")!!
        // Model a row removed between the preflight lookup and the UPDATE.
        val racingDao = java.lang.reflect.Proxy.newProxyInstance(TransactionDao::class.java.classLoader,
            arrayOf(TransactionDao::class.java)) { _, method, _ ->
            when (method.name) {
                "getTransactionById" -> original
                "updateTransaction" -> 0
                else -> error("Unexpected DAO call: ${method.name}")
            }
        } as TransactionDao
        val racingRepository = TransactionRepository(racingDao, database.categoryDao(), database.accountDao(), preferences)
        val result = TransactionSaveOperation(racingRepository::updateTransaction).save(original.copy(amount = 99.0))
        assertTrue(result.isFailure)
    }

    @Test fun signedOutSnapshotReadsDoNotExposeAnonymousLegacyRows() = runBlocking {
        database.categoryDao().insertCategory(Category("food", "Food", "restaurant", "#000000", "EXPENSE"))
        database.accountDao().insertAccount(Account("default_cash", "Legacy", "CASH", "#000000", ledgerScopeId = ""))
        database.transactionDao().insertTransaction(transaction().copy(ledgerScopeId = ""))
        sessions.clear()
        assertTrue(repository.getAllTransactionsOnce().isEmpty())
        assertTrue(repository.getDistinctTitles().isEmpty())
        assertTrue(repository.getByDateRange(0, Long.MAX_VALUE).isEmpty())
        assertNull(repository.getIncomeExpenseTotalsForRange(0, Long.MAX_VALUE))
    }

    @Test fun collidingTransactionIdsAndForeignUpdatesCannotOverwriteAnotherLedger() = runBlocking {
        user("alice", "scope-a"); user("bob", "scope-b")
        sessions.activateUser("alice")
        database.categoryDao().insertCategory(Category("food", "Food", "restaurant", "#000000", "EXPENSE"))
        repository.insertTransaction(transaction())
        val original = repository.getTransactionById("tx")!!
        assertTrue(runCatching { repository.updateTransaction(original.copy(amount = Double.POSITIVE_INFINITY)) }.isFailure)
        sessions.activateUser("bob")
        assertTrue(runCatching { repository.insertTransaction(transaction(amount = 99.0)) }.isFailure)
        assertTrue(runCatching { repository.updateTransaction(original.copy(ledgerScopeId = null, amount = 99.0)) }.isFailure)
        assertEquals(10.0, database.transactionDao().getTransactionById("tx", "scope-a")!!.amount, 0.0)
        assertEquals(0, database.transactionDao().getTransactionCount("scope-b"))
    }

    @Test fun referencedAccountCannotFallBackToAnotherLedgersGlobalCashAccount() = runBlocking {
        user("alice", "scope-a"); user("bob", "scope-b")
        sessions.activateUser("bob")
        database.categoryDao().insertCategory(Category("food", "Food", "restaurant", "#000000", "EXPENSE"))
        repository.insertTransaction(transaction())
        val accountId = repository.getTransactionById("tx")!!.accountId
        val accounts = AccountRepository(database.accountDao(), database.transactionDao(), preferences)
        assertTrue(runCatching { accounts.deleteAccountById(accountId) }.isFailure)
        assertEquals(accountId, repository.getTransactionById("tx")!!.accountId)
        assertNotNull(accounts.getAccountById(accountId))
    }

    @Test fun sameUserRestoreRetainsValidDefaultButSignOutAndSwitchClearIt() = runBlocking {
        user("alice", "scope-a"); user("bob", "scope-b")
        sessions.activateUser("alice")
        val id = DefaultAccounts.resolve(database.accountDao(), "upi_account", "scope-a")!!.id
        preferences.setDefaultAccountId(id)
        sessions.activateUser("alice")
        assertEquals(id, preferences.defaultAccountId.first())
        sessions.activateUser("bob")
        assertNull(preferences.defaultAccountId.first())
        preferences.setDefaultAccountId(DefaultAccounts.scopedId("default_cash", "scope-b"))
        sessions.clear()
        assertNull(preferences.defaultAccountId.first())
    }

    @Test fun setupIsIdempotentAndClaimsEmptyLegacyScopeOnlyForSoleProfile() = runBlocking {
        val profile = UserProfile("alice", "Alice", "alice@example.test")
        database.accountDao().insertAccount(Account("legacy", "Legacy", "CASH", "#000000", ledgerScopeId = ""))
        val stitcher = DatabaseStitcher(database)
        val scope = stitcher.saveProfileAndScope(profile, "Personal", "PERSONAL")
        assertEquals(scope, stitcher.saveProfileAndScope(profile.copy(displayName = "Edited"), "Personal", "PERSONAL"))
        assertNotNull(database.accountDao().getAccountById("legacy", scope))
        user("bob", "scope-b")
        database.accountDao().insertAccount(Account("anonymous", "Unknown owner", "CASH", "#000000"))
        stitcher.stitchUnassignedData("bob", "scope-b")
        assertNull(database.accountDao().getAccountById("anonymous", "scope-b"))
        database.userProfileDao().insertProfile(UserProfile("bob", "Bob", "bob@example.test", isDeleted = true))
        stitcher.stitchUnassignedData("alice", scope)
        assertNull(database.accountDao().getAccountById("anonymous", scope))
        assertTrue(runCatching { stitcher.stitchUnassignedData("bob", scope) }.isFailure)
    }
}
