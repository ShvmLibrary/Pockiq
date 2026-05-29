package com.example.pockiq

import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import com.example.pockiq.data.WalletRepository
import com.example.pockiq.data.db.TransactionEntity
import com.example.pockiq.data.db.TransactionType
import com.example.pockiq.data.db.WalletSource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.util.Date
import java.util.regex.Pattern

class UpiNotificationListenerService : NotificationListenerService() {

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        val packageName = sbn.packageName ?: return
        
        // Dynamic detection of any financial, banking, or UPI payment app on Play Store
        val isFinancialApp = packageName.contains("pay", ignoreCase = true) ||
                            packageName.contains("bank", ignoreCase = true) ||
                            packageName.contains("wallet", ignoreCase = true) ||
                            packageName.contains("money", ignoreCase = true) ||
                            packageName.contains("finance", ignoreCase = true) ||
                            packageName.contains("cred", ignoreCase = true) ||
                            packageName.contains("slice", ignoreCase = true) ||
                            packageName == "com.google.android.apps.nbu.paisa.user" || // GPay
                            packageName == "in.org.npci.upiapp"                        // BHIM
                       
        if (!isFinancialApp) return
        
        val extras = sbn.notification.extras
        val title = extras.getString("android.title") ?: ""
        val text = extras.getCharSequence("android.text")?.toString() ?: ""
        val body = "$title $text"
        
        parseAndSaveTransaction(packageName, body)
    }

    private fun extractUtr(body: String): String? {
        val patterns = listOf(
            Pattern.compile("(?i)(?:utr|ref|reference|txn\\s*id|transaction\\s*id)[:\\s#]*([a-zA-Z0-9]{12,})"),
            Pattern.compile("\\b\\d{12}\\b")
        )
        for (pattern in patterns) {
            val matcher = pattern.matcher(body)
            if (matcher.find()) {
                val match = matcher.group(1) ?: matcher.group(0)
                if (match != null && match.isNotBlank()) {
                    return match.trim()
                }
            }
        }
        return null
    }

    private fun parseAndSaveTransaction(appSource: String, body: String) {
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
            val repo = WalletRepository.getInstance(applicationContext)
            
            val utr = extractUtr(body)

            CoroutineScope(Dispatchers.IO).launch {
                val recentTxns = repo.getAllTransactions().first()
                val isDuplicate = recentTxns.any { tx ->
                    if (utr != null && tx.note.contains("[UTR: $utr]")) {
                        true
                    } else {
                        // 120-second window: prevents dedup misses for same-amount transactions
                        tx.amount == amount &&
                        tx.type == type &&
                        Math.abs(Date().time - tx.date.time) < 120_000L
                    }
                }
                
                val recentDrafts = repo.getDraftTransactions().first()
                val isDraftDuplicate = recentDrafts.any { tx ->
                    if (utr != null && tx.note.contains("[UTR: $utr]")) {
                        true
                    } else {
                        // 60-second cross-source window (notification + SMS for same tx)
                        tx.amount == amount &&
                        tx.type == type &&
                        Math.abs(Date().time - tx.date.time) < 60_000L
                    }
                }
                
                if (!isDuplicate && !isDraftDuplicate) {
                    val appName = when {
                        appSource.contains("google", ignoreCase = true) -> "GPay"
                        appSource.contains("phonepe", ignoreCase = true) -> "PhonePe"
                        appSource.contains("paytm", ignoreCase = true) -> "Paytm"
                        appSource.contains("cred", ignoreCase = true) -> "CRED"
                        appSource.contains("slice", ignoreCase = true) -> "Slice"
                        appSource.contains("hdfc", ignoreCase = true) -> "HDFC Bank"
                        appSource.contains("icici", ignoreCase = true) -> "ICICI Bank"
                        appSource.contains("axis", ignoreCase = true) -> "Axis Bank"
                        appSource.contains("sbi", ignoreCase = true) -> "SBI Bank"
                        appSource.contains("kotak", ignoreCase = true) -> "Kotak Bank"
                        appSource.contains("bajaj", ignoreCase = true) -> "Bajaj Pay"
                        else -> {
                            val parts = appSource.split(".")
                            parts.firstOrNull { it != "com" && it != "org" && it != "net" && it != "android" }
                                ?.replaceFirstChar { it.uppercase() } ?: "UPI App"
                        }
                    }
                    
                    val baseNote = "Auto-imported from $appName"
                    val finalNote = if (utr != null) "$baseNote [UTR: $utr]" else baseNote

                    repo.addTransaction(
                        TransactionEntity(
                            type = type,
                            amount = amount,
                            category = "Uncategorized",
                            note = finalNote,
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
