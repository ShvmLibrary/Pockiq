package com.example.pockiq.ui.transactions

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.pockiq.data.WalletRepository
import com.example.pockiq.data.db.TransactionEntity
import com.example.pockiq.data.db.TransactionType
import com.example.pockiq.theme.WalletColors
import com.example.pockiq.ui.dashboard.formatCurrency
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TransactionsScreen(
    repo: WalletRepository,
    bottomPadding: androidx.compose.ui.unit.Dp = 0.dp,
    onAdd: () -> Unit,
    onEdit: (Long) -> Unit,
    onNavigateToAnalysis: (String) -> Unit
) {
    val context = LocalContext.current
    val allTxns by repo.getAllTransactions().collectAsState(initial = emptyList())
    val totalIncome by repo.totalIncome().collectAsState(initial = 0.0)
    val totalExpense by repo.totalExpense().collectAsState(initial = 0.0)
    var filter by remember { mutableStateOf("All") }
    var txnToDelete by remember { mutableStateOf<TransactionEntity?>(null) }
    
    // Save / Export states
    var showExportDialog by remember { mutableStateOf(false) }
    var selectedRange by remember { mutableStateOf("All Time") }
    var selectedSpecificMonth by remember { mutableStateOf("") }
    var dropdownExpanded by remember { mutableStateOf(false) }

    val scope = rememberCoroutineScope()
    val snackState = remember { SnackbarHostState() }

    val displayed = when (filter) {
        "Income"  -> allTxns.filter { it.type == TransactionType.INCOME }
        "Expense" -> allTxns.filter { it.type == TransactionType.EXPENSE }
        else      -> allTxns
    }

    Scaffold(
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = onAdd,
                icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                text = { Text("Add") },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor   = MaterialTheme.colorScheme.onPrimary,
                modifier       = Modifier.padding(bottom = bottomPadding)
            )
        },
        snackbarHost = { SnackbarHost(snackState) },
        contentWindowInsets = WindowInsets(0.dp)
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background),
            contentPadding = PaddingValues(bottom = 80.dp + bottomPadding)
        ) {
            // Header
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surface)
                        .statusBarsPadding()
                        .padding(horizontal = 20.dp, vertical = 20.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "Transactions",
                            style = MaterialTheme.typography.headlineLarge,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Bold
                        )
                        IconButton(
                            onClick = { showExportDialog = true },
                            colors = IconButtonDefaults.iconButtonColors(containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)),
                            modifier = Modifier.size(38.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Save,
                                contentDescription = "Export Transactions",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        TxnSummaryPill(
                            modifier = Modifier.weight(1f),
                            label   = "Income",
                            amount  = totalIncome,
                            color   = WalletColors.income,
                            onClick = { onNavigateToAnalysis("INCOME") }
                        )
                        TxnSummaryPill(
                            modifier = Modifier.weight(1f),
                            label   = "Expenses",
                            amount  = totalExpense,
                            color   = WalletColors.expense,
                            onClick = { onNavigateToAnalysis("EXPENSE") }
                        )
                    }
                }
            }

            // Filter Chips
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    listOf("All", "Income", "Expense").forEach { f ->
                        FilterChip(
                            selected = filter == f,
                            onClick  = { filter = f },
                            label    = { Text(f) },
                            colors   = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = MaterialTheme.colorScheme.primary,
                                selectedLabelColor     = MaterialTheme.colorScheme.onPrimary
                            )
                        )
                    }
                }
            }

            if (displayed.isEmpty()) {
                item {
                    Box(
                        modifier = Modifier.fillMaxWidth().padding(60.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                imageVector = Icons.Filled.Inbox,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                                modifier = Modifier.size(48.dp)
                            )
                            Spacer(Modifier.height(12.dp))
                            Text("No transactions found", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            } else {
                val grouped = displayed.groupBy {
                    SimpleDateFormat("MMMM yyyy", Locale.getDefault()).format(it.date)
                }
                grouped.forEach { (month, txns) ->
                    item {
                        Text(
                            month,
                            style    = MaterialTheme.typography.labelLarge,
                            color    = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)
                        )
                    }
                    items(txns, key = { it.id }) { txn ->
                        SwipeToDismissTxn(
                            txn = txn,
                            onEdit = { onEdit(txn.id) },
                            onDeleteTriggered = { txnToDelete = txn }
                        ) {
                            TxnItemCard(txn = txn)
                        }
                    }
                }
            }
        }
    }

    if (txnToDelete != null) {
        val txn = txnToDelete!!
        AlertDialog(
            onDismissRequest = { txnToDelete = null },
            title = { Text("Delete Transaction", fontWeight = FontWeight.Bold) },
            text = {
                Text("Are you sure you want to delete this transaction for ${formatCurrency(txn.amount)}?")
            },
            confirmButton = {
                Button(
                    onClick = {
                        val toDelete = txn
                        txnToDelete = null
                        scope.launch {
                            repo.deleteTransactionWithBalance(toDelete)
                            snackState.showSnackbar("Transaction deleted")
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { txnToDelete = null }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Export Dialog
    if (showExportDialog) {
        val availableMonths = remember(allTxns) {
            val sdf = SimpleDateFormat("MMMM yyyy", Locale.getDefault())
            allTxns.map { sdf.format(it.date) }.distinct()
        }
        if (selectedSpecificMonth.isEmpty() && availableMonths.isNotEmpty()) {
            selectedSpecificMonth = availableMonths.first()
        }

        AlertDialog(
            onDismissRequest = { showExportDialog = false },
            title = { Text("Save & Export List", fontWeight = FontWeight.Bold) },
            text = {
                Column(
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Select a time range to export as CSV format.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    
                    listOf("Last 7 Days", "Current Month", "All Time", "Specific Month").forEach { range ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .clickable { selectedRange = range }
                                .padding(vertical = 6.dp, horizontal = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = selectedRange == range,
                                onClick = { selectedRange = range }
                            )
                            Spacer(Modifier.width(10.dp))
                            Text(range, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface)
                        }
                    }

                    if (selectedRange == "Specific Month" && availableMonths.isNotEmpty()) {
                        Spacer(Modifier.height(4.dp))
                        Box(modifier = Modifier.fillMaxWidth()) {
                            OutlinedButton(
                                onClick = { dropdownExpanded = true },
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(12.dp),
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f))
                            ) {
                                Text(selectedSpecificMonth.ifEmpty { "Choose Month" }, color = MaterialTheme.colorScheme.onSurface)
                                Spacer(Modifier.weight(1f))
                                Icon(Icons.Filled.ArrowDropDown, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            DropdownMenu(
                                expanded = dropdownExpanded,
                                onDismissRequest = { dropdownExpanded = false },
                                modifier = Modifier.fillMaxWidth(0.75f).background(MaterialTheme.colorScheme.surface)
                            ) {
                                availableMonths.forEach { m ->
                                    DropdownMenuItem(
                                        text = { Text(m) },
                                        onClick = {
                                            selectedSpecificMonth = m
                                            dropdownExpanded = false
                                        }
                                    )
                                }
                            }
                        }
                    } else if (selectedRange == "Specific Month" && availableMonths.isEmpty()) {
                        Text(
                            "No historical transactions found.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.padding(start = 12.dp)
                        )
                    }
                }
            },
            confirmButton = {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    TextButton(
                        onClick = {
                            val filtered = filterTxnsByRange(allTxns, selectedRange, selectedSpecificMonth)
                            exportToCsv(
                                context = context,
                                txns = filtered,
                                title = "Pockiq Export ($selectedRange)",
                                isShare = false
                            ) { msg ->
                                scope.launch { snackState.showSnackbar(msg) }
                            }
                            showExportDialog = false
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Filled.ContentCopy, null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Copy CSV", maxLines = 1)
                    }

                    Button(
                        onClick = {
                            val filtered = filterTxnsByRange(allTxns, selectedRange, selectedSpecificMonth)
                            exportToCsv(
                                context = context,
                                txns = filtered,
                                title = "Pockiq Export ($selectedRange)",
                                isShare = true
                            ) { msg ->
                                scope.launch { snackState.showSnackbar(msg) }
                            }
                            showExportDialog = false
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Filled.Share, null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Share", maxLines = 1)
                    }
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showExportDialog = false },
                    modifier = Modifier.padding(end = 8.dp)
                ) {
                    Text("Cancel")
                }
            }
        )
    }
}

fun filterTxnsByRange(txns: List<TransactionEntity>, range: String, specificMonth: String): List<TransactionEntity> {
    val now = Date()
    val cal = Calendar.getInstance()
    return when (range) {
        "Last 7 Days" -> {
            val limit = now.time - 7L * 24 * 60 * 60 * 1000
            txns.filter { it.date.time >= limit }
        }
        "Current Month" -> {
            txns.filter { txn ->
                val txnCal = Calendar.getInstance().apply { time = txn.date }
                txnCal.get(Calendar.YEAR) == cal.get(Calendar.YEAR) &&
                txnCal.get(Calendar.MONTH) == cal.get(Calendar.MONTH)
            }
        }
        "Specific Month" -> {
            val sdf = SimpleDateFormat("MMMM yyyy", Locale.getDefault())
            txns.filter { txn ->
                sdf.format(txn.date) == specificMonth
            }
        }
        else -> txns
    }
}

fun exportToCsv(context: Context, txns: List<TransactionEntity>, title: String, isShare: Boolean, onSnackbarMsg: (String) -> Unit) {
    val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
    val csv = StringBuilder()
    csv.append("Date,Category,Type,Amount,Source,Note\n")
    for (tx in txns) {
        val dateStr = sdf.format(tx.date)
        val noteSafe = tx.note.replace("\"", "\"\"")
        csv.append("\"$dateStr\",\"${tx.category}\",\"${tx.type.name}\",${tx.amount},\"${tx.walletSource.name}\",\"$noteSafe\"\n")
    }
    
    if (isShare) {
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, title)
            putExtra(Intent.EXTRA_TEXT, csv.toString())
        }
        context.startActivity(Intent.createChooser(intent, "Share CSV"))
    } else {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newPlainText(title, csv.toString())
        clipboard.setPrimaryClip(clip)
        onSnackbarMsg("CSV copied to clipboard!")
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SwipeToDismissTxn(
    txn: TransactionEntity,
    onEdit: () -> Unit,
    onDeleteTriggered: () -> Unit,
    content: @Composable () -> Unit
) {
    val dismissState = rememberSwipeToDismissBoxState(
        confirmValueChange = { value ->
            when (value) {
                SwipeToDismissBoxValue.StartToEnd -> {
                    onEdit()
                    false
                }
                SwipeToDismissBoxValue.EndToStart -> {
                    onDeleteTriggered()
                    false
                }
                else -> false
            }
        }
    )

    SwipeToDismissBox(
        state = dismissState,
        backgroundContent = {
            val direction = dismissState.dismissDirection
            val color = when (direction) {
                SwipeToDismissBoxValue.StartToEnd -> MaterialTheme.colorScheme.primary
                SwipeToDismissBoxValue.EndToStart -> WalletColors.expense
                else -> Color.Transparent
            }
            val alignment = when (direction) {
                SwipeToDismissBoxValue.StartToEnd -> Alignment.CenterStart
                SwipeToDismissBoxValue.EndToStart -> Alignment.CenterEnd
                else -> Alignment.Center
            }
            val icon = when (direction) {
                SwipeToDismissBoxValue.StartToEnd -> Icons.Filled.Edit
                SwipeToDismissBoxValue.EndToStart -> Icons.Filled.Delete
                else -> null
            }
            val paddingSide = 20.dp

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 20.dp, vertical = 4.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(color),
                contentAlignment = alignment
            ) {
                if (icon != null) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = if (direction == SwipeToDismissBoxValue.StartToEnd) {
                            Modifier.padding(start = paddingSide)
                        } else {
                            Modifier.padding(end = paddingSide)
                        }
                    )
                }
            }
        },
        content = {
            content()
        }
    )
}

@Composable
fun TxnItemCard(txn: TransactionEntity) {
    val isIncome = txn.type == TransactionType.INCOME
    val color    = if (isIncome) WalletColors.income else WalletColors.expense
    val icon     = if (isIncome) Icons.Filled.ArrowDownward else Icons.Filled.ArrowUpward
    val sdf      = SimpleDateFormat("dd MMM, hh:mm a", Locale.getDefault())

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 4.dp),
        shape  = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.08f)),
        elevation = CardDefaults.cardElevation(0.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment    = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                modifier = Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(color.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(22.dp))
                }
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(txn.category, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    if (txn.note.isNotBlank()) {
                        Text(txn.note, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    Text(sdf.format(txn.date), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Surface(
                        shape = RoundedCornerShape(4.dp),
                        color = MaterialTheme.colorScheme.secondaryContainer
                    ) {
                        Text(
                            txn.walletSource.name,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                            style    = MaterialTheme.typography.labelMedium,
                            color    = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                    }
                }
            }
            Spacer(Modifier.width(8.dp))
            Text(
                "${if (isIncome) "+" else "-"}${formatCurrency(txn.amount)}",
                style      = MaterialTheme.typography.titleMedium,
                color      = color,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
fun TxnSummaryPill(
    modifier: Modifier,
    label: String,
    amount: Double,
    color: Color,
    onClick: () -> Unit
) {
    Card(
        modifier = modifier.clickable { onClick() },
        shape    = RoundedCornerShape(12.dp),
        colors   = CardDefaults.cardColors(containerColor = color.copy(alpha = 0.12f)),
        border   = BorderStroke(1.dp, Color.White.copy(alpha = 0.08f)),
        elevation = CardDefaults.cardElevation(0.dp)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(label, style = MaterialTheme.typography.labelMedium, color = color)
            Text(formatCurrency(amount), style = MaterialTheme.typography.titleMedium, color = color, fontWeight = FontWeight.Bold)
        }
    }
}
