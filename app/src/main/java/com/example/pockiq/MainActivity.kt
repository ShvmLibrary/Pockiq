package com.example.pockiq

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.lifecycle.lifecycleScope
import com.example.pockiq.theme.PockiqTheme

import com.example.pockiq.data.WalletRepository
import com.example.pockiq.data.db.TransactionEntity
import com.example.pockiq.data.db.TransactionType
import com.example.pockiq.data.db.WalletSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.util.Date
import java.util.regex.Pattern

class MainActivity : ComponentActivity() {
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
      window.attributes.layoutInDisplayCutoutMode =
        WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
    }

    // Request RECEIVE_SMS and READ_SMS for robust automatic tracking
    val permissions = arrayOf(
        android.Manifest.permission.RECEIVE_SMS,
        android.Manifest.permission.READ_SMS
    )
    val missing = permissions.filter {
        androidx.core.content.ContextCompat.checkSelfPermission(this, it) != android.content.pm.PackageManager.PERMISSION_GRANTED
    }
    if (missing.isNotEmpty()) {
        androidx.core.app.ActivityCompat.requestPermissions(this, missing.toTypedArray(), 101)
    } else {
        syncMissedTransactions()
    }

    // Prompt once to disable battery optimisation — keeps NotificationListenerService alive on OEM devices
    requestBatteryOptimizationExemption()

    enableEdgeToEdge()
    val widgetAction = intent?.getStringExtra("action")
    val widgetType = intent?.getStringExtra("type")

    setContent {
      PockiqTheme { 
        Surface(
          modifier = Modifier.fillMaxSize(), 
          color = MaterialTheme.colorScheme.background
        ) { 
          MainNavigation(widgetAction = widgetAction, widgetType = widgetType) 
        } 
      }
    }
  }

  override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
    super.onRequestPermissionsResult(requestCode, permissions, grantResults)
    if (requestCode == 101) {
        syncMissedTransactions()
    }
  }

  private fun syncMissedTransactions() {
    if (androidx.core.content.ContextCompat.checkSelfPermission(this, android.Manifest.permission.READ_SMS)
        != android.content.pm.PackageManager.PERMISSION_GRANTED) {
        return
    }

    val contentResolver = contentResolver
    lifecycleScope.launch(Dispatchers.IO) {
        val cursor = contentResolver.query(
            android.net.Uri.parse("content://sms/inbox"),
            arrayOf("_id", "address", "body", "date"),
            "date > ?",
            arrayOf((Date().time - 48 * 60 * 60 * 1000).toString()), // check last 48 hours
            "date DESC"
        ) ?: return@launch

        val prefs = applicationContext.getSharedPreferences("pockiq_ignored", android.content.Context.MODE_PRIVATE)
        val ignoredSet = prefs.getStringSet("ignored_keys", emptySet()) ?: emptySet()

        val repo = WalletRepository.getInstance(applicationContext)
        val amountPattern = Pattern.compile("(?i)(?:Rs\\.?|INR|₹)\\s*([\\d,]+\\.?\\d*)")
        val recentTxns = repo.getAllTransactions().first()
        val recentDrafts = repo.getDraftTransactions().first()

        while (cursor.moveToNext()) {
            val address = cursor.getString(cursor.getColumnIndexOrThrow("address")) ?: "Bank"
            val body = cursor.getString(cursor.getColumnIndexOrThrow("body")) ?: ""
            val dateMillis = cursor.getLong(cursor.getColumnIndexOrThrow("date"))
            val date = Date(dateMillis)

            if (isTransactionSms(body)) {
                val matcher = amountPattern.matcher(body)
                if (matcher.find()) {
                    val amountStr = matcher.group(1)?.replace(",", "") ?: continue
                    val amount = amountStr.toDoubleOrNull() ?: continue

                    val isIncome = body.contains("received", ignoreCase = true) ||
                                   body.contains("credited", ignoreCase = true) ||
                                   body.contains("deposited", ignoreCase = true)

                    val type = if (isIncome) TransactionType.INCOME else TransactionType.EXPENSE
                    val utr = extractUtr(body)

                    // Check if transaction is ignored
                    val isIgnored = if (utr != null && ignoredSet.contains("UTR_$utr")) {
                        true
                    } else {
                        val sig = "${amount}_${type.name}_${date.time}"
                        ignoredSet.any { ignored ->
                            if (ignored.startsWith("UTR_")) {
                                false
                            } else {
                                val parts = ignored.split("_")
                                if (parts.size == 3) {
                                    val ignoredAmt = parts[0].toDoubleOrNull() ?: 0.0
                                    val ignoredType = parts[1]
                                    val ignoredTime = parts[2].toLongOrNull() ?: 0L
                                    ignoredAmt == amount && ignoredType == type.name && Math.abs(ignoredTime - date.time) < 15000
                                } else {
                                    false
                                }
                            }
                        }
                    }

                    if (isIgnored) continue

                    // Check duplicate
                    val isDuplicate = recentTxns.any { tx ->
                        if (utr != null && tx.note.contains("[UTR: $utr]")) {
                            true
                        } else {
                            tx.amount == amount && tx.type == type && Math.abs(date.time - tx.date.time) < 10000
                        }
                    } || recentDrafts.any { tx ->
                        if (utr != null && tx.note.contains("[UTR: $utr]")) {
                            true
                        } else {
                            tx.amount == amount && tx.type == type && Math.abs(date.time - tx.date.time) < 10000
                        }
                    }

                    if (!isDuplicate) {
                        val baseNote = "Auto-imported from $address"
                        val finalNote = if (utr != null) "$baseNote [UTR: $utr]" else baseNote
                        
                        repo.addTransaction(
                            TransactionEntity(
                                type = type,
                                amount = amount,
                                category = "Uncategorized",
                                note = finalNote,
                                date = date,
                                walletSource = WalletSource.BANK,
                                isDraft = true
                            )
                        )
                    }
                }
            }
        }
        cursor.close()
    }
  }

  private fun isTransactionSms(body: String): Boolean {
    val lower = body.lowercase()
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

  private fun requestBatteryOptimizationExemption() {
    val prefs = getSharedPreferences("pockiq_settings", Context.MODE_PRIVATE)
    // Only prompt once — don't nag on every launch
    if (prefs.getBoolean("battery_opt_prompted", false)) return

    val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
    if (!pm.isIgnoringBatteryOptimizations(packageName)) {
      prefs.edit().putBoolean("battery_opt_prompted", true).apply()
      try {
        // Opens the system dialog to whitelist this specific app
        val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
          data = Uri.parse("package:$packageName")
        }
        startActivity(intent)
      } catch (e: Exception) {
        // Fallback: open general battery optimization settings
        try {
          startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
        } catch (_: Exception) { /* device doesn't support this intent */ }
      }
    } else {
      // Already whitelisted — mark so we never prompt again
      prefs.edit().putBoolean("battery_opt_prompted", true).apply()
    }
  }
}
