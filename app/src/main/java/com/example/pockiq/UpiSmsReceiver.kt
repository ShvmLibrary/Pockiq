package com.example.pockiq

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.telephony.SmsMessage
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

class UpiSmsReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == "android.provider.Telephony.SMS_RECEIVED") {
            val bundle = intent.extras ?: return
            val pdus = bundle.get("pdus") as? Array<*> ?: return
            val format = bundle.getString("format")
            
            for (pdu in pdus) {
                val sms = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
                    SmsMessage.createFromPdu(pdu as ByteArray, format)
                } else {
                    @Suppress("DEPRECATION")
                    SmsMessage.createFromPdu(pdu as ByteArray)
                }
                
                val body = sms.messageBody ?: continue
                val sender = sms.originatingAddress ?: "Bank"
                
                if (isTransactionSms(body)) {
                    parseAndSaveTransaction(context, sender, body)
                }
            }
        }
    }

    private fun isTransactionSms(body: String): Boolean {
        val lower = body.lowercase()
        // Standard indicators for a financial bank/UPI alert transaction
        return (lower.contains("credited") || lower.contains("debited") || 
                lower.contains("sent") || lower.contains("received") || 
                lower.contains("transferred") || lower.contains("spent") || 
                lower.contains("paid")) && (lower.contains("rs") || lower.contains("inr") || lower.contains("₹"))
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

    private fun parseAndSaveTransaction(context: Context, sender: String, body: String) {
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

            val utr = extractUtr(body)
            val baseNote = "Auto-imported from $sender"
            val finalNote = if (utr != null) "$baseNote [UTR: $utr]" else baseNote

            CoroutineScope(Dispatchers.IO).launch {
                val recentTxns = repo.getAllTransactions().first()
                val isDuplicate = recentTxns.any { tx ->
                    if (utr != null && tx.note.contains("[UTR: $utr]")) {
                        true
                    } else {
                        // 120-second window: same amount+type within 2 minutes = duplicate
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
                        // 60-second window for drafts (cross-source: SMS + notification)
                        tx.amount == amount &&
                        tx.type == type &&
                        Math.abs(Date().time - tx.date.time) < 60_000L
                    }
                }
                
                if (!isDuplicate && !isDraftDuplicate) {
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
