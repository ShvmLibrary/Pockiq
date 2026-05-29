package com.example.pockiq.data

import android.content.Context
import androidx.room.Room
import com.example.pockiq.data.db.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.util.Date

class WalletRepository private constructor(context: Context) {

    private val db = Room.databaseBuilder(
        context.applicationContext,
        AppDatabase::class.java,
        "pockiq.db"
    ).fallbackToDestructiveMigration(dropAllTables = true).build()

    private val transactionDao = db.transactionDao()
    private val walletDao      = db.walletDao()
    private val otherMoneyDao  = db.otherMoneyDao()
    private val categoryDao    = db.categoryDao()

    init {
        // Pre-populate default categories once if DB is empty
        CoroutineScope(Dispatchers.IO).launch {
            if (categoryDao.getCount() == 0) {
                val defaults = mutableListOf<CategoryEntity>()
                listOf(
                    "Salary", "Freelance", "Investment", "Gift",
                    "Refund", "Interest", "Other Income"
                ).forEach { name ->
                    defaults.add(CategoryEntity(name = name, type = TransactionType.INCOME))
                }
                listOf(
                    "Food", "Transport", "Rent", "Utilities", "Shopping",
                    "Healthcare", "Entertainment", "Education", "Sports",
                    "Travel", "Other Expense"
                ).forEach { name ->
                    defaults.add(CategoryEntity(name = name, type = TransactionType.EXPENSE))
                }
                categoryDao.insertAll(defaults)
            }
        }
    }

    // ── Wallet ────────────────────────────────────────────────────────────────
    fun getWallet(): Flow<WalletEntity> =
        walletDao.getWallet().map { it ?: WalletEntity() }

    suspend fun updateWallet(bank: Double, cash: Double) {
        val current = walletDao.getWalletSync() ?: WalletEntity()
        walletDao.upsert(current.copy(bankBalance = bank, cashBalance = cash, lastUpdated = Date()))
    }

    suspend fun updateBudget(budget: Double) {
        val current = walletDao.getWalletSync() ?: WalletEntity()
        walletDao.upsert(current.copy(monthlyBudget = budget, lastUpdated = Date()))
    }

    // ── Transactions ──────────────────────────────────────────────────────────
    fun getAllTransactions(): Flow<List<TransactionEntity>> =
        transactionDao.getAllTransactions()

    fun getDraftTransactions(): Flow<List<TransactionEntity>> =
        transactionDao.getDraftTransactions()

    fun getTransactionsByType(type: TransactionType): Flow<List<TransactionEntity>> =
        transactionDao.getByType(type)

    fun totalIncome(): Flow<Double>  = transactionDao.totalIncome().map  { it ?: 0.0 }
    fun totalExpense(): Flow<Double> = transactionDao.totalExpense().map { it ?: 0.0 }

    suspend fun getTransactionById(id: Long): TransactionEntity? =
        transactionDao.getTransactionById(id)

    suspend fun addTransaction(tx: TransactionEntity)    = transactionDao.insert(tx)
    suspend fun deleteTransaction(tx: TransactionEntity) = transactionDao.delete(tx)

    suspend fun deleteTransactionWithBalance(tx: TransactionEntity) {
        val w = walletDao.getWalletSync() ?: WalletEntity()
        val delta = if (tx.type == TransactionType.INCOME) -tx.amount else tx.amount
        var newBank = w.bankBalance
        var newCash = w.cashBalance
        when (tx.walletSource) {
            WalletSource.BANK -> newBank += delta
            WalletSource.CASH -> newCash += delta
        }
        walletDao.upsert(w.copy(bankBalance = newBank, cashBalance = newCash, lastUpdated = Date()))
        transactionDao.delete(tx)
    }

    // ── Others' Money ─────────────────────────────────────────────────────────
    fun getAllOtherMoney(): Flow<List<OtherMoneyEntity>>            = otherMoneyDao.getAll()
    fun getOtherMoneyByPerson(name: String): Flow<List<OtherMoneyEntity>> =
        otherMoneyDao.getByPerson(name)
    fun getDistinctPersons(): Flow<List<String>> = otherMoneyDao.getDistinctPersons()

    /**
     * Insert an other-money entry with deduplication.
     * Dedup window: 60 seconds — prevents double-entries from rapid taps
     * while still allowing the same person/amount to appear legitimately later.
     */
    suspend fun addOtherMoney(entry: OtherMoneyEntity) {
        val recent = otherMoneyDao.getAll().first()
        val isDuplicate = recent.any { existing ->
            existing.amount    == entry.amount    &&
            existing.direction == entry.direction &&
            existing.personName.equals(entry.personName, ignoreCase = true) &&
            Math.abs(existing.date.time - entry.date.time) < 60_000L // 60 s window
        }
        if (!isDuplicate) {
            otherMoneyDao.insert(entry)
        }
    }

    suspend fun deleteOtherMoney(entry: OtherMoneyEntity) = otherMoneyDao.delete(entry)
    suspend fun renamePerson(oldName: String, newName: String) =
        otherMoneyDao.renamePerson(oldName, newName)

    // ── Categories ────────────────────────────────────────────────────────────
    fun getCategories(type: TransactionType): Flow<List<CategoryEntity>> =
        categoryDao.getCategoriesByType(type)

    suspend fun addCategory(name: String, type: TransactionType) =
        categoryDao.insert(CategoryEntity(name = name, type = type))

    suspend fun deleteCategory(category: CategoryEntity) =
        categoryDao.delete(category)

    // ── Singleton ─────────────────────────────────────────────────────────────
    companion object {
        @Volatile private var INSTANCE: WalletRepository? = null

        /**
         * Returns the single shared instance of WalletRepository.
         * All callers (Activity, BroadcastReceiver, Service) must use this
         * to avoid creating multiple Room database connections.
         */
        fun getInstance(context: Context): WalletRepository =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: WalletRepository(context.applicationContext).also { INSTANCE = it }
            }
    }
}
