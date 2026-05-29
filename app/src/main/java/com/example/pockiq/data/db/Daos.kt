package com.example.pockiq.data.db

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface TransactionDao {
    @Query("SELECT * FROM transactions WHERE isDraft = 0 ORDER BY date DESC")
    fun getAllTransactions(): Flow<List<TransactionEntity>>

    @Query("SELECT * FROM transactions WHERE type = :type AND isDraft = 0 ORDER BY date DESC")
    fun getByType(type: TransactionType): Flow<List<TransactionEntity>>

    @Query("SELECT * FROM transactions WHERE date >= :from AND date <= :to AND isDraft = 0 ORDER BY date DESC")
    fun getByDateRange(from: Long, to: Long): Flow<List<TransactionEntity>>

    @Query("SELECT SUM(amount) FROM transactions WHERE type = 'INCOME' AND isDraft = 0")
    fun totalIncome(): Flow<Double?>

    @Query("SELECT SUM(amount) FROM transactions WHERE type = 'EXPENSE' AND isDraft = 0")
    fun totalExpense(): Flow<Double?>

    @Query("SELECT * FROM transactions WHERE id = :id")
    suspend fun getTransactionById(id: Long): TransactionEntity?

    @Query("SELECT * FROM transactions WHERE isDraft = 1 ORDER BY date DESC")
    fun getDraftTransactions(): Flow<List<TransactionEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(transaction: TransactionEntity)

    @Delete
    suspend fun delete(transaction: TransactionEntity)
}

@Dao
interface WalletDao {
    @Query("SELECT * FROM wallet WHERE id = 1")
    fun getWallet(): Flow<WalletEntity?>

    @Query("SELECT * FROM wallet WHERE id = 1")
    suspend fun getWalletSync(): WalletEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(wallet: WalletEntity)
}

@Dao
interface OtherMoneyDao {
    @Query("SELECT * FROM other_money ORDER BY date DESC")
    fun getAll(): Flow<List<OtherMoneyEntity>>

    @Query("SELECT * FROM other_money WHERE personName = :name ORDER BY date DESC")
    fun getByPerson(name: String): Flow<List<OtherMoneyEntity>>

    @Query("SELECT DISTINCT personName FROM other_money ORDER BY personName ASC")
    fun getDistinctPersons(): Flow<List<String>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entry: OtherMoneyEntity)

    @Delete
    suspend fun delete(entry: OtherMoneyEntity)

    @Query("UPDATE other_money SET personName = :newName WHERE personName = :oldName")
    suspend fun renamePerson(oldName: String, newName: String)
}

@Dao
interface CategoryDao {
    @Query("SELECT * FROM categories WHERE type = :type ORDER BY id ASC")
    fun getCategoriesByType(type: TransactionType): Flow<List<CategoryEntity>>

    @Query("SELECT COUNT(*) FROM categories")
    suspend fun getCount(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(category: CategoryEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(categories: List<CategoryEntity>)

    @Delete
    suspend fun delete(category: CategoryEntity)
}
