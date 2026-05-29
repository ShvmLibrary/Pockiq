package com.example.pockiq.ui.wallet

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.pockiq.data.WalletRepository
import com.example.pockiq.theme.WalletColors
import com.example.pockiq.ui.dashboard.formatCurrency
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WalletScreen(repo: WalletRepository, bottomPadding: androidx.compose.ui.unit.Dp = 0.dp) {
    val wallet by repo.getWallet().collectAsState(initial = null)
    val scope  = rememberCoroutineScope()

    var editBank  by remember { mutableStateOf(false) }
    var editCash  by remember { mutableStateOf(false) }
    var bankInput by remember { mutableStateOf("") }
    var cashInput by remember { mutableStateOf("") }
    val snackState = remember { SnackbarHostState() }

    val bankBal = wallet?.bankBalance ?: 0.0
    val cashBal = wallet?.cashBalance ?: 0.0
    val total   = bankBal + cashBal

    val bankFraction = if (total > 0) (bankBal / total).toFloat() else 0.5f
    val animatedFraction by animateFloatAsState(
        targetValue    = bankFraction,
        animationSpec  = tween(800),
        label          = "bankFraction"
    )

    Scaffold(
        snackbarHost = { SnackbarHost(snackState) },
        contentWindowInsets = WindowInsets(0.dp)
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .verticalScroll(rememberScrollState())
                .padding(bottom = bottomPadding)
        ) {
            // Clean header with no color gradients
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(horizontal = 24.dp, vertical = 24.dp)
            ) {
                Column {
                    Text("My Wallet", style = MaterialTheme.typography.headlineLarge, color = MaterialTheme.colorScheme.onBackground, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(4.dp))
                    Text("Total Balance", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(8.dp))
                    Text(
                        formatCurrency(total),
                        style      = MaterialTheme.typography.displayLarge,
                        color      = MaterialTheme.colorScheme.onBackground,
                        fontWeight = FontWeight.ExtraBold
                    )
                }
            }

            Spacer(Modifier.height(8.dp))

            // Distribution Bar with neutral, minimalist tones
            Column(modifier = Modifier.padding(horizontal = 24.dp)) {
                Text("Distribution", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onBackground)
                Spacer(Modifier.height(8.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(8.dp)
                        .clip(RoundedCornerShape(4.dp))
                ) {
                    Box(
                        modifier = Modifier
                            .weight(animatedFraction.coerceAtLeast(0.01f))
                            .fillMaxHeight()
                            .background(MaterialTheme.colorScheme.primary)
                    )
                    Box(
                        modifier = Modifier
                            .weight((1f - animatedFraction).coerceAtLeast(0.01f))
                            .fillMaxHeight()
                            .background(MaterialTheme.colorScheme.outlineVariant)
                    )
                }
                Spacer(Modifier.height(6.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    LegendDot("Bank", MaterialTheme.colorScheme.primary)
                    LegendDot("Cash", MaterialTheme.colorScheme.outlineVariant)
                }
            }

            Spacer(Modifier.height(20.dp))

            // Bank Card
            WalletCard(
                label    = "Bank Account",
                balance  = bankBal,
                icon     = Icons.Filled.AccountBalance,
                color    = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(horizontal = 20.dp),
                onEdit   = { bankInput = bankBal.toString(); editBank = true }
            )

            Spacer(Modifier.height(12.dp))

            // Cash Card
            WalletCard(
                label    = "Cash in Hand",
                balance  = cashBal,
                icon     = Icons.Filled.Payments,
                color    = MaterialTheme.colorScheme.secondary,
                modifier = Modifier.padding(horizontal = 20.dp),
                onEdit   = { cashInput = cashBal.toString(); editCash = true }
            )

            Spacer(Modifier.height(24.dp))
        }
    }

    if (editBank) {
        BalanceEditDialog(
            title         = "Update Bank Balance",
            current       = bankBal,
            input         = bankInput,
            onInputChange = { bankInput = it },
            onDismiss     = { editBank = false },
            onSave        = {
                val newBank = bankInput.toDoubleOrNull()
                if (newBank != null) {
                    scope.launch {
                        repo.updateWallet(bank = newBank, cash = cashBal)
                        snackState.showSnackbar("Bank balance updated!")
                    }
                    editBank = false
                }
            }
        )
    }

    if (editCash) {
        BalanceEditDialog(
            title         = "Update Cash Balance",
            current       = cashBal,
            input         = cashInput,
            onInputChange = { cashInput = it },
            onDismiss     = { editCash = false },
            onSave        = {
                val newCash = cashInput.toDoubleOrNull()
                if (newCash != null) {
                    scope.launch {
                        repo.updateWallet(bank = bankBal, cash = newCash)
                        snackState.showSnackbar("Cash balance updated!")
                    }
                    editCash = false
                }
            }
        )
    }
}

@Composable
fun LegendDot(label: String, color: Color) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(10.dp).clip(RoundedCornerShape(2.dp)).background(color))
        Spacer(Modifier.width(4.dp))
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
fun WalletCard(label: String, balance: Double, icon: androidx.compose.ui.graphics.vector.ImageVector, color: Color, modifier: Modifier = Modifier, onEdit: () -> Unit) {
    Card(
        modifier  = modifier.fillMaxWidth(),
        shape     = RoundedCornerShape(20.dp),
        colors    = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)),
        border    = BorderStroke(1.dp, Color.White.copy(alpha = 0.08f)),
        elevation = CardDefaults.cardElevation(0.dp)
    ) {
        Row(
            modifier              = Modifier.fillMaxWidth().padding(20.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment     = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(color.copy(alpha = 0.12f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(22.dp))
                }
                Spacer(Modifier.width(16.dp))
                Column {
                    Text(label, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
                    Spacer(Modifier.height(4.dp))
                    Text(formatCurrency(balance), style = MaterialTheme.typography.headlineMedium, color = color, fontWeight = FontWeight.Bold)
                }
            }
            IconButton(
                onClick = onEdit,
                colors  = IconButtonDefaults.iconButtonColors(containerColor = color.copy(alpha = 0.15f))
            ) {
                Icon(Icons.Filled.Edit, contentDescription = "Edit", tint = color)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BalanceEditDialog(
    title: String,
    current: Double,
    input: String,
    onInputChange: (String) -> Unit,
    onDismiss: () -> Unit,
    onSave: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title, style = MaterialTheme.typography.titleLarge) },
        text  = {
            Column {
                Text("Current: ${formatCurrency(current)}", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value         = input,
                    onValueChange = onInputChange,
                    label         = { Text("New balance") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    prefix        = { Text("₹") },
                    singleLine    = true,
                    modifier      = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton  = { Button(onClick = onSave) { Text("Save") } },
        dismissButton  = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}
