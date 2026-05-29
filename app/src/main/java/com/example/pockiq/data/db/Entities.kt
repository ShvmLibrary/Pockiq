package com.example.pockiq.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.Date

enum class TransactionType { INCOME, EXPENSE }
enum class WalletSource { BANK, CASH }

@Entity(tableName = "transactions")
data class TransactionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val type: TransactionType,
    val amount: Double,
    val category: String,
    val note: String = "",
    val date: Date,
    val walletSource: WalletSource,
    val isDraft: Boolean = false
)

@Entity(tableName = "wallet")
data class WalletEntity(
    @PrimaryKey val id: Int = 1,
    val bankBalance: Double = 0.0,
    val cashBalance: Double = 0.0,
    val monthlyBudget: Double = 0.0,
    val lastUpdated: Date = Date()
)

enum class OtherMoneyDirection { RECEIVED, GIVEN }

@Entity(tableName = "other_money")
data class OtherMoneyEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val personName: String,
    val direction: OtherMoneyDirection,
    val amount: Double,
    val date: Date,
    val note: String = ""
)

@Entity(tableName = "categories")
data class CategoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val type: TransactionType
)
