package com.example.pockiq.ui.others

import androidx.compose.foundation.background
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.pockiq.data.WalletRepository
import com.example.pockiq.data.db.OtherMoneyDirection
import com.example.pockiq.data.db.OtherMoneyEntity
import com.example.pockiq.theme.WalletColors
import com.example.pockiq.ui.dashboard.formatCurrency
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun PersonLedgerScreen(
    repo: WalletRepository,
    personName: String,
    bottomPadding: androidx.compose.ui.unit.Dp = 0.dp,
    onBack: () -> Unit
) {
    var currentPersonName by remember { mutableStateOf(personName) }
    val entries by repo.getOtherMoneyByPerson(currentPersonName).collectAsState(initial = emptyList())
    val scope = rememberCoroutineScope()
    val snackState = remember { SnackbarHostState() }

    var showRenameDialog by remember { mutableStateOf(false) }
    var newNameInput by remember { mutableStateOf("") }
    var entryToDelete by remember { mutableStateOf<OtherMoneyEntity?>(null) }

    val totalReceived = entries.filter { it.direction == OtherMoneyDirection.RECEIVED }.sumOf { it.amount }
    val totalGiven = entries.filter { it.direction == OtherMoneyDirection.GIVEN }.sumOf { it.amount }
    val net = totalReceived - totalGiven
    val initial = currentPersonName.firstOrNull()?.uppercaseChar() ?: '?'

    Scaffold(
        snackbarHost = { SnackbarHost(snackState) },
        contentWindowInsets = WindowInsets(0.dp)
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background),
            contentPadding = PaddingValues(bottom = 24.dp + bottomPadding)
        ) {
            // Header
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .padding(horizontal = 20.dp, vertical = 24.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = onBack) {
                            Icon(Icons.Filled.ArrowBack, null, tint = WalletColors.other)
                        }
                        Spacer(Modifier.width(8.dp))
                        Box(
                            modifier = Modifier
                                .size(56.dp)
                                .clip(CircleShape)
                                .background(WalletColors.other.copy(alpha = 0.25f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(initial.toString(), fontSize = 24.sp, color = WalletColors.other, fontWeight = FontWeight.Bold)
                        }
                        Spacer(Modifier.width(12.dp))
                        Column {
                            Text(
                                text = currentPersonName,
                                style = MaterialTheme.typography.headlineMedium,
                                color = WalletColors.other,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .combinedClickable(
                                        onLongClick = {
                                            newNameInput = currentPersonName
                                            showRenameDialog = true
                                        },
                                        onClick = {}
                                    )
                                    .padding(vertical = 2.dp, horizontal = 4.dp)
                            )
                            Text("${entries.size} entries", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }

            // Summary Row
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OtherSummaryCard(modifier = Modifier.weight(1f), label = "You Received", amount = totalReceived, color = WalletColors.income)
                    OtherSummaryCard(modifier = Modifier.weight(1f), label = "You Gave Back", amount = totalGiven, color = WalletColors.expense)
                }
            }

            item { Spacer(Modifier.height(8.dp)) }

            // Net
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp),
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = (if (net >= 0) WalletColors.income else WalletColors.expense).copy(alpha = 0.08f)
                    ),
                    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.08f)),
                    elevation = CardDefaults.cardElevation(0.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            if (net >= 0) "Still holding"
                            else "Over-returned by",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            formatCurrency(kotlin.math.abs(net)),
                            style = MaterialTheme.typography.titleLarge,
                            color = if (net >= 0) WalletColors.income else WalletColors.expense,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            item {
                Text(
                    "Ledger",
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onBackground,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp)
                )
            }

            if (entries.isEmpty()) {
                item {
                    Box(Modifier.fillMaxWidth().padding(40.dp), contentAlignment = Alignment.Center) {
                        Text("No entries yet", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            } else {
                items(entries, key = { it.id }) { entry ->
                    LedgerEntryCard(
                        entry = entry,
                        onDelete = { entryToDelete = entry }
                    )
                }
            }
        }
    }

    if (showRenameDialog) {
        AlertDialog(
            onDismissRequest = { showRenameDialog = false },
            title = { Text("Rename Contact", fontWeight = FontWeight.Bold) },
            text = {
                Column {
                    Text("Enter a new display name for this contact across all ledger transactions.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(12.dp))
                    OutlinedTextField(
                        value = newNameInput,
                        onValueChange = { newNameInput = it },
                        label = { Text("Display Name") },
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = WalletColors.other)
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val trimmed = newNameInput.trim()
                        if (trimmed.isNotBlank() && trimmed != currentPersonName) {
                            scope.launch {
                                repo.renamePerson(currentPersonName, trimmed)
                                currentPersonName = trimmed
                                showRenameDialog = false
                                snackState.showSnackbar("Contact renamed successfully")
                            }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = WalletColors.other, contentColor = Color(0xFF1A1A1A))
                ) {
                    Text("Rename", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showRenameDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    if (entryToDelete != null) {
        val entry = entryToDelete!!
        AlertDialog(
            onDismissRequest = { entryToDelete = null },
            title = { Text("Delete Entry", fontWeight = FontWeight.Bold) },
            text = { Text("Are you sure you want to delete this ledger entry of ₹${formatCurrency(entry.amount)}?") },
            confirmButton = {
                Button(
                    onClick = {
                        scope.launch {
                            repo.deleteOtherMoney(entry)
                            entryToDelete = null
                            snackState.showSnackbar("Entry deleted successfully")
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Delete", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { entryToDelete = null }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
fun LedgerEntryCard(entry: OtherMoneyEntity, onDelete: () -> Unit) {
    val isReceived = entry.direction == OtherMoneyDirection.RECEIVED
    val color = if (isReceived) WalletColors.income else WalletColors.expense
    val icon = if (isReceived) Icons.Filled.ArrowDownward else Icons.Filled.ArrowUpward
    val dirLabel = if (isReceived) "Received" else "Given Back"
    val sdf = SimpleDateFormat("dd MMM yyyy", Locale.getDefault())

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 4.dp),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.08f)),
        elevation = CardDefaults.cardElevation(0.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(color.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(icon, null, tint = color, modifier = Modifier.size(20.dp))
                }
                Spacer(Modifier.width(10.dp))
                Column {
                    Text(dirLabel, style = MaterialTheme.typography.titleMedium, color = color, fontWeight = FontWeight.SemiBold)
                    if (entry.note.isNotBlank()) {
                        Text(entry.note, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2)
                    }
                    Text(sdf.format(entry.date), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    "${if (isReceived) "+" else "-"}${formatCurrency(entry.amount)}",
                    style = MaterialTheme.typography.titleMedium,
                    color = color,
                    fontWeight = FontWeight.Bold
                )
                IconButton(onClick = onDelete, modifier = Modifier.size(28.dp)) {
                    Icon(Icons.Filled.Delete, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(16.dp))
                }
            }
        }
    }
}
