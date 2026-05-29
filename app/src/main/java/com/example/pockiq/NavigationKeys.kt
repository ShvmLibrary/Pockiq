package com.example.pockiq

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable

@Serializable data object Main : NavKey
@Serializable data object Dashboard : NavKey
@Serializable data object Wallet : NavKey
@Serializable data object Transactions : NavKey
@Serializable data class AddTransaction(val transactionId: Long? = null, val prefilledType: String? = null) : NavKey
@Serializable data class OthersMoney(
    val splitAmount: Double? = null,
    val splitNote: String? = null,
    val splitDirection: String? = null,
    val splitDraftId: Long? = null
) : NavKey
@Serializable data class AddOtherMoney(
    val personName: String = "",
    val prefilledAmount: Double? = null,
    val prefilledNote: String? = null,
    val prefilledDirection: String? = null,
    val draftIdToDelete: Long? = null
) : NavKey
@Serializable data class PersonLedger(val personName: String) : NavKey
@Serializable data class Analysis(val type: String) : NavKey
