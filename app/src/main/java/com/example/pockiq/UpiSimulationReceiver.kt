package com.example.pockiq

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.example.pockiq.data.WalletRepository
import com.example.pockiq.data.db.TransactionEntity
import com.example.pockiq.data.db.TransactionType
import com.example.pockiq.data.db.WalletSource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.Date
import java.util.regex.Pattern

class UpiSimulationReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == "com.example.pockiq.SIMULATE_UPI") {
            val body = intent.getStringExtra("body") ?: return
            val sender = intent.getStringExtra("sender") ?: "Bank SMS"
            parseAndSaveTransaction(context, sender, body)
        }
    }

    companion object {
        fun parseAndSaveTransaction(context: Context, sender: String, body: String) {
            // Regex to find amounts like ₹500, Rs 500, or INR 500
            val amountPattern = Pattern.compile("(?i)(?:Rs\\.?|INR|₹)\\s*([\\d,]+\\.?\\d*)")
            val matcher = amountPattern.matcher(body)
            if (matcher.find()) {
                val amountStr = matcher.group(1)?.replace(",", "") ?: return
                val amount = amountStr.toDoubleOrNull() ?: return

                // Detect credit/debit
                val isIncome = body.contains("received", ignoreCase = true) ||
                               body.contains("credited", ignoreCase = true) ||
                               body.contains("deposited", ignoreCase = true)

                val type = if (isIncome) TransactionType.INCOME else TransactionType.EXPENSE
                val repo = WalletRepository.getInstance(context)

                CoroutineScope(Dispatchers.IO).launch {
                    repo.addTransaction(
                        TransactionEntity(
                            type = type,
                            amount = amount,
                            category = "Uncategorized",
                            note = "Auto-imported from $sender",
                            date = Date(),
                            walletSource = WalletSource.BANK,
                            isDraft = true
                        )
                    )
                }
            }
        }
    }
}
