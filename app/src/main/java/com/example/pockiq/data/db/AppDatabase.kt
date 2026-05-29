package com.example.pockiq.data.db

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters

@Database(
    entities = [TransactionEntity::class, OtherMoneyEntity::class, WalletEntity::class, CategoryEntity::class],
    version = 5,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun transactionDao(): TransactionDao
    abstract fun otherMoneyDao(): OtherMoneyDao
    abstract fun walletDao(): WalletDao
    abstract fun categoryDao(): CategoryDao
}
