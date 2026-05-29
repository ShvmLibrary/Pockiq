package com.example.pockiq.ui.others

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.pockiq.data.WalletRepository
import com.example.pockiq.data.db.OtherMoneyDirection
import com.example.pockiq.data.db.OtherMoneyEntity
import com.example.pockiq.theme.WalletColors
import com.example.pockiq.ui.transactions.CalculatorDialog
import kotlinx.coroutines.launch
import java.util.*
import android.content.Context
import androidx.compose.ui.platform.LocalContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddOtherMoneyScreen(
    repo: WalletRepository,
    prefilledName: String = "",
    prefilledAmount: Double? = null,
    prefilledNote: String? = null,
    prefilledDirection: String? = null,
    draftIdToDelete: Long? = null,
    onDone: () -> Unit
) {
    var personName by remember { mutableStateOf(prefilledName) }
    var amount by remember { mutableStateOf(prefilledAmount?.toString() ?: "") }
    var direction by remember {
        mutableStateOf(
            if (prefilledDirection == "GIVEN") OtherMoneyDirection.GIVEN
            else OtherMoneyDirection.RECEIVED
        )
    }
    var note by remember { mutableStateOf(prefilledNote ?: "") }
    var error by remember { mutableStateOf("") }
    var showCalculator by remember { mutableStateOf(false) }

    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    val isReceived = direction == OtherMoneyDirection.RECEIVED
    val accentColor = if (isReceived) WalletColors.income else WalletColors.expense

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .verticalScroll(rememberScrollState())
            .navigationBarsPadding()
    ) {
        // Top bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 20.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onDone) {
                Icon(Icons.Filled.ArrowBack, null, tint = MaterialTheme.colorScheme.onBackground)
            }
            Spacer(Modifier.width(8.dp))
            Text(
                "Others' Money Entry",
                style = MaterialTheme.typography.headlineMedium,
                color = WalletColors.other,
                fontWeight = FontWeight.Bold
            )
        }

        // Direction Toggle
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
        ) {
            listOf(
                OtherMoneyDirection.RECEIVED to "Received",
                OtherMoneyDirection.GIVEN to "Given Back"
            ).forEach { (dir, label) ->
                val selected = direction == dir
                val color = if (dir == OtherMoneyDirection.RECEIVED) WalletColors.income else WalletColors.expense
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(12.dp))
                        .background(if (selected) color else Color.Transparent)
                        .clickable { direction = dir }
                        .padding(vertical = 12.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        label,
                        color = if (selected) MaterialTheme.colorScheme.background else MaterialTheme.colorScheme.onSurfaceVariant,
                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal
                    )
                }
            }
        }

        Spacer(Modifier.height(20.dp))

        // Person Name
        Column(modifier = Modifier.padding(horizontal = 20.dp)) {
            Text("Person Name", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(6.dp))
            OutlinedTextField(
                value = personName,
                onValueChange = { personName = it; error = "" },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("e.g. Rahul Kumar") },
                leadingIcon = { Icon(Icons.Filled.Person, null) },
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
                colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = WalletColors.other)
            )
        }

        Spacer(Modifier.height(16.dp))

        // Amount Input Field with Calculator Button
        Column(modifier = Modifier.padding(horizontal = 20.dp)) {
            Text("Amount", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(6.dp))
            OutlinedTextField(
                value = amount,
                onValueChange = { amount = it; error = "" },
                modifier = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                prefix = { Text("₹", fontWeight = FontWeight.Bold, color = accentColor) },
                trailingIcon = {
                    IconButton(onClick = { showCalculator = true }) {
                        Icon(
                            imageVector = Icons.Filled.Calculate,
                            contentDescription = "Calculator",
                            tint = accentColor,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                },
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
                colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = accentColor)
            )
        }

        Spacer(Modifier.height(16.dp))

        // Context / Source / Destination
        Column(modifier = Modifier.padding(horizontal = 20.dp)) {
            Text(
                if (isReceived) "Source / Context" else "Destination / Purpose",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(6.dp))
            OutlinedTextField(
                value = note,
                onValueChange = { note = it },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text(if (isReceived) "e.g. For office party, from savings..." else "e.g. Returned from rent, grocery...") },
                shape = RoundedCornerShape(12.dp),
                maxLines = 3
            )
        }

        AnimatedVisibility(error.isNotBlank()) {
            Text(
                error,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp)
            )
        }

        Spacer(Modifier.height(28.dp))

        // Save Button
        Button(
            onClick = {
                val amt = amount.toDoubleOrNull()
                when {
                    personName.isBlank() -> error = "Enter person name"
                    amt == null || amt <= 0 -> error = "Enter a valid amount"
                    else -> {
                        scope.launch {
                            repo.addOtherMoney(
                                OtherMoneyEntity(
                                    personName = personName.trim(),
                                    direction = direction,
                                    amount = amt,
                                    date = Date(),
                                    note = note.trim()
                                )
                            )
                            if (draftIdToDelete != null) {
                                val draft = repo.getTransactionById(draftIdToDelete)
                                if (draft != null) {
                                    // ── Add to ignored set so startup sync never re-imports this SMS ──
                                    val prefs = context.getSharedPreferences("pockiq_ignored", Context.MODE_PRIVATE)
                                    val ignoredSet = prefs.getStringSet("ignored_keys", emptySet<String>()) ?: emptySet<String>()
                                    val utrRegex = java.util.regex.Pattern.compile("\\[UTR: ([a-zA-Z0-9]+)\\]")
                                    val matcher = utrRegex.matcher(draft.note)
                                    val sig = if (matcher.find()) {
                                        val utr = matcher.group(1)
                                        if (utr != null && utr.isNotBlank()) "UTR_$utr"
                                        else "${draft.amount}_${draft.type.name}_${draft.date.time}"
                                    } else {
                                        "${draft.amount}_${draft.type.name}_${draft.date.time}"
                                    }
                                    prefs.edit().putStringSet("ignored_keys", ignoredSet.toMutableSet().also { it.add(sig) }).apply()
                                    // ─────────────────────────────────────────────────────────────────
                                    repo.deleteTransaction(draft)
                                }
                            }
                            onDone()
                        }
                    }
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .height(54.dp),
            shape = RoundedCornerShape(14.dp),
            colors = ButtonDefaults.buttonColors(containerColor = WalletColors.other, contentColor = Color(0xFF1A1A1A))
        ) {
            Icon(Icons.Filled.Check, null)
            Spacer(Modifier.width(8.dp))
            Text("Save Entry", fontWeight = FontWeight.Bold)
        }

        Spacer(Modifier.height(24.dp))
    }

    if (showCalculator) {
        CalculatorDialog(
            initialValue = amount,
            accentColor = accentColor,
            onDismiss = { showCalculator = false },
            onApply = { result ->
                amount = result
                error = ""
            }
        )
    }
}
