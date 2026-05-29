package com.example.pockiq.ui.dashboard

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.pockiq.data.WalletRepository
import com.example.pockiq.data.db.OtherMoneyDirection
import com.example.pockiq.data.db.TransactionEntity
import com.example.pockiq.data.db.TransactionType
import com.example.pockiq.data.db.WalletSource
import com.example.pockiq.theme.WalletColors
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.*
import kotlinx.coroutines.launch
import androidx.compose.ui.text.style.TextOverflow

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    repo: WalletRepository,
    bottomPadding: androidx.compose.ui.unit.Dp = 0.dp,
    onAddTransaction: () -> Unit,
    onGoToWallet: () -> Unit,
    onGoToOthers: () -> Unit,
    onEditTransaction: (Long) -> Unit,
    onNavigateToAddOther: (Double, String, String, Long) -> Unit,
    onNavigateToOthersWithSplit: (Double, String, String, Long) -> Unit,
    onNavigateToAnalysis: (String) -> Unit
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val wallet       by repo.getWallet().collectAsState(initial = null)
    val totalIncome  by repo.totalIncome().collectAsState(initial = 0.0)
    val totalExpense by repo.totalExpense().collectAsState(initial = 0.0)
    val recentTxns   by repo.getAllTransactions().collectAsState(initial = emptyList())
    val otherMoney   by repo.getAllOtherMoney().collectAsState(initial = emptyList())
    val draftTxns    by repo.getDraftTransactions().collectAsState(initial = emptyList())

    val bankBal  = wallet?.bankBalance ?: 0.0
    val cashBal  = wallet?.cashBalance ?: 0.0
    val netWorth = bankBal + cashBal
    val othersNet = otherMoney.sumOf {
        if (it.direction == OtherMoneyDirection.RECEIVED) it.amount else -it.amount
    }

    var visible by remember { mutableStateOf(false) }
    var txnToDelete by remember { mutableStateOf<TransactionEntity?>(null) }
    val snackState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) { visible = true }

    Scaffold(
        snackbarHost = { SnackbarHost(snackState) },
        contentWindowInsets = WindowInsets(0.dp)
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .padding(innerPadding),
            contentPadding = PaddingValues(bottom = 24.dp + bottomPadding)
        ) {
        // Hero header
        item {
            AnimatedVisibility(
                visible = visible,
                enter   = fadeIn(tween(600)) + slideInVertically(tween(600)) { -40 }
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .padding(horizontal = 20.dp, vertical = 28.dp)
                ) {
                    Column {
                        Text(
                            "Good day!",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "Pockiq",
                            style      = MaterialTheme.typography.headlineLarge,
                            color      = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }

        // Net Worth Card
        item {
            AnimatedVisibility(
                visible = visible,
                enter   = fadeIn(tween(700)) + slideInVertically(tween(700)) { 60 }
            ) {
                Card(
                    modifier  = Modifier.fillMaxWidth().padding(horizontal = 20.dp).clickable { onGoToWallet() },
                    shape     = RoundedCornerShape(24.dp),
                    colors    = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)),
                    border    = BorderStroke(1.dp, Color.White.copy(alpha = 0.08f)),
                    elevation = CardDefaults.cardElevation(0.dp)
                ) {
                    Box(
                        modifier = Modifier.padding(24.dp)
                    ) {
                        Column {
                            Text("Total Net Worth", style = MaterialTheme.typography.labelLarge, color = Color(0xFFB2BABB))
                            Spacer(Modifier.height(8.dp))
                            Text(
                                formatCurrency(netWorth),
                                style      = MaterialTheme.typography.displayLarge.copy(fontSize = 38.sp),
                                color      = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(Modifier.height(16.dp))
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                MiniBalanceChip("Bank", bankBal, Icons.Filled.AccountBalance, MaterialTheme.colorScheme.primary)
                                MiniBalanceChip("Cash", cashBal, Icons.Filled.Payments, MaterialTheme.colorScheme.secondary)
                            }
                        }
                    }
                }
            }
        }

        item { Spacer(Modifier.height(12.dp)) }

        // Monthly Budget Card
        item {
            AnimatedVisibility(
                visible = visible,
                enter   = fadeIn(tween(750)) + slideInVertically(tween(750)) { 70 }
            ) {
                var showBudgetDialog by remember { mutableStateOf(false) }
                var budgetInput by remember { mutableStateOf("") }
                // Uses the hoisted `scope` from DashboardScreen — no need for a new one here

                Card(
                    modifier  = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
                    shape     = RoundedCornerShape(16.dp),
                    colors    = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)),
                    border    = BorderStroke(1.dp, Color.White.copy(alpha = 0.08f)),
                    elevation = CardDefaults.cardElevation(0.dp)
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        val monthlyBudget = wallet?.monthlyBudget ?: 0.0
                        val currentMonthExpenses = recentTxns
                            .filter { it.type == TransactionType.EXPENSE && isCurrentMonth(it.date) }
                            .sumOf { it.amount }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(28.dp)
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(Icons.Filled.AccountBalanceWallet, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(14.dp))
                                }
                                Spacer(Modifier.width(8.dp))
                                Text("Monthly Budget", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurface)
                            }
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                if (monthlyBudget > 0.0) {
                                    val budgetProgress = (currentMonthExpenses / monthlyBudget).toFloat()
                                    val progressPercent = (budgetProgress * 100).toInt()
                                    Text(
                                        "$progressPercent%",
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = if (budgetProgress > 1f) WalletColors.expense else MaterialTheme.colorScheme.primary
                                    )
                                    Spacer(Modifier.width(8.dp))
                                }
                                IconButton(
                                    onClick = {
                                        budgetInput = if ((wallet?.monthlyBudget ?: 0.0) > 0.0) wallet!!.monthlyBudget.toString() else ""
                                        showBudgetDialog = true
                                    },
                                    colors = IconButtonDefaults.iconButtonColors(containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)),
                                    modifier = Modifier.size(24.dp)
                                ) {
                                    Icon(Icons.Filled.Edit, contentDescription = "Edit Budget", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(12.dp))
                                }
                            }
                        }

                        if (monthlyBudget <= 0.0) {
                            Spacer(Modifier.height(8.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    "No budget set for this month",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                TextButton(
                                    onClick = { budgetInput = ""; showBudgetDialog = true },
                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                    modifier = Modifier.height(28.dp)
                                ) {
                                    Text("Set Budget", style = MaterialTheme.typography.labelSmall)
                                }
                            }
                        } else {
                            val budgetProgress = (currentMonthExpenses / monthlyBudget).toFloat()
                            
                            Spacer(Modifier.height(6.dp))

                            LinearProgressIndicator(
                                progress = { budgetProgress.coerceIn(0f, 1f) },
                                modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)),
                                color = when {
                                    budgetProgress > 1f -> WalletColors.expense
                                    budgetProgress > 0.8f -> MaterialTheme.colorScheme.secondary
                                    else -> MaterialTheme.colorScheme.primary
                                },
                                trackColor = MaterialTheme.colorScheme.outlineVariant
                            )

                            Spacer(Modifier.height(4.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    "${formatCurrency(currentMonthExpenses)} of ${formatCurrency(monthlyBudget)} used",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                if (budgetProgress > 1f) {
                                    Text(
                                        "Exceeded by ${formatCurrency(currentMonthExpenses - monthlyBudget)}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = WalletColors.expense,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                } else {
                                    Text(
                                        "${formatCurrency(monthlyBudget - currentMonthExpenses)} left",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }

                if (showBudgetDialog) {
                    AlertDialog(
                        onDismissRequest = { showBudgetDialog = false },
                        title = { Text("Set Monthly Budget", fontWeight = FontWeight.Bold) },
                        text = {
                            Column {
                                Text("Enter your spending budget limit for this month.", style = MaterialTheme.typography.bodyMedium)
                                Spacer(Modifier.height(12.dp))
                                OutlinedTextField(
                                    value = budgetInput,
                                    onValueChange = { budgetInput = it },
                                    label = { Text("Budget Limit") },
                                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Decimal),
                                    prefix = { Text("₹") },
                                    singleLine = true,
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                        },
                        confirmButton = {
                            Button(
                                onClick = {
                                    val newBudget = budgetInput.toDoubleOrNull() ?: 0.0
                                    scope.launch {
                                        repo.updateBudget(newBudget)
                                        showBudgetDialog = false
                                    }
                                }
                            ) {
                                Text("Save")
                            }
                        },
                        dismissButton = {
                            TextButton(onClick = { showBudgetDialog = false }) {
                                Text("Cancel")
                            }
                        }
                    )
                }
            }
        }

        item { Spacer(Modifier.height(12.dp)) }

        // Draft Transactions Review Card
        if (draftTxns.isNotEmpty()) {
            item {
                AnimatedVisibility(
                    visible = visible,
                    enter   = fadeIn(tween(750)) + slideInVertically(tween(750)) { 70 }
                ) {
                    var showReviewSheet by remember { mutableStateOf(false) }

                    Card(
                        modifier  = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
                        shape     = RoundedCornerShape(16.dp),
                        colors    = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)),
                        border    = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.25f)),
                        elevation = CardDefaults.cardElevation(0.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth().clickable { showReviewSheet = true }.padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(36.dp)
                                        .clip(CircleShape)
                                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        Icons.Filled.NotificationsActive,
                                        null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                                Spacer(Modifier.width(12.dp))
                                Column {
                                    Text(
                                        "Pending UPI Reviews",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                    Text(
                                        "${draftTxns.size} unapproved UPI transactions",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                            Icon(
                                Icons.Filled.ChevronRight,
                                contentDescription = "Review",
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    }

                    if (showReviewSheet) {
                        DraftReviewBottomSheet(
                            drafts = draftTxns,
                            repo = repo,
                            onNavigateToAddOther = onNavigateToAddOther,
                            onNavigateToOthersWithSplit = onNavigateToOthersWithSplit,
                            onDismiss = { showReviewSheet = false }
                        )
                    }
                }
            }
            item { Spacer(Modifier.height(12.dp)) }
        }

        // Income / Expense Row
        item {
            AnimatedVisibility(
                visible = visible,
                enter   = fadeIn(tween(800)) + slideInVertically(tween(800)) { 80 }
            ) {
                Row(
                    modifier              = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    SummaryCard(
                        modifier = Modifier.weight(1f),
                        label    = "Income",
                        amount   = totalIncome,
                        icon     = Icons.Filled.ArrowDownward,
                        color    = WalletColors.income,
                        onClick  = { onNavigateToAnalysis("INCOME") }
                    )
                    SummaryCard(
                        modifier = Modifier.weight(1f),
                        label    = "Expenses",
                        amount   = totalExpense,
                        icon     = Icons.Filled.ArrowUpward,
                        color    = WalletColors.expense,
                        onClick  = { onNavigateToAnalysis("EXPENSE") }
                    )
                }
            }
        }

        item { Spacer(Modifier.height(16.dp)) }

        // Recent Transactions Header
        item {
            Row(
                modifier              = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment     = Alignment.CenterVertically
            ) {
                Text("Recent Activity", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onBackground)
                TextButton(onClick = onAddTransaction) {
                    Icon(Icons.Filled.Add, null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Add")
                }
            }
        }

        if (recentTxns.isEmpty()) {
            item {
                Box(Modifier.fillMaxWidth().padding(40.dp), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = Icons.Filled.Inbox,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                            modifier = Modifier.size(48.dp)
                        )
                        Spacer(Modifier.height(12.dp))
                        Text("No transactions yet", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        } else {
            items(recentTxns.take(10), key = { it.id }) { txn ->
                SwipeToDismissTxn(
                    txn = txn,
                    onEdit = { onEditTransaction(txn.id) },
                    onDeleteTriggered = { txnToDelete = txn }
                ) {
                    TransactionListItem(txn)
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
}
}

@Composable
fun MiniBalanceChip(label: String, amount: Double, icon: ImageVector, color: Color) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(color.copy(alpha = 0.12f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, null, tint = color, modifier = Modifier.size(18.dp))
        }
        Spacer(Modifier.width(10.dp))
        Column {
            Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(formatCurrency(amount), style = MaterialTheme.typography.titleMedium, color = color, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
fun SummaryCard(modifier: Modifier = Modifier, label: String, amount: Double, icon: ImageVector, color: Color, onClick: () -> Unit = {}) {
    Card(
        modifier  = modifier.clickable { onClick() },
        shape     = RoundedCornerShape(16.dp),
        colors    = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)),
        border    = BorderStroke(1.dp, Color.White.copy(alpha = 0.08f)),
        elevation = CardDefaults.cardElevation(0.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier         = Modifier.size(32.dp).clip(CircleShape).background(color.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(icon, null, tint = color, modifier = Modifier.size(18.dp))
                }
                Spacer(Modifier.width(8.dp))
                Text(label, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(Modifier.height(8.dp))
            Text(formatCurrency(amount), style = MaterialTheme.typography.titleLarge, color = color, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
fun TransactionListItem(txn: TransactionEntity) {
    val isIncome = txn.type == TransactionType.INCOME
    val color    = if (isIncome) WalletColors.income else WalletColors.expense
    val icon     = if (isIncome) Icons.Filled.ArrowDownward else Icons.Filled.ArrowUpward
    val sdf      = SimpleDateFormat("dd MMM", Locale.getDefault())

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

fun formatCurrency(amount: Double): String {
    val fmt = NumberFormat.getCurrencyInstance(Locale("en", "IN"))
    return fmt.format(amount)
}

fun isCurrentMonth(date: Date): Boolean {
    val cal = Calendar.getInstance()
    val txnCal = Calendar.getInstance()
    txnCal.time = date
    return cal.get(Calendar.YEAR) == txnCal.get(Calendar.YEAR) &&
           cal.get(Calendar.MONTH) == txnCal.get(Calendar.MONTH)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DraftReviewBottomSheet(
    drafts: List<TransactionEntity>,
    repo: WalletRepository,
    onNavigateToAddOther: (Double, String, String, Long) -> Unit,
    onNavigateToOthersWithSplit: (Double, String, String, Long) -> Unit,
    onDismiss: () -> Unit
) {
    // Copy/remember the initial list of drafts so it remains static during review
    // and doesn't shrink or shift indexes while db updates are in flight
    val localDrafts = remember { drafts }
    var currentIndex by remember { mutableStateOf(0) }
    val scope = rememberCoroutineScope()
    val wallet by repo.getWallet().collectAsState(initial = null)
    val otherMoney by repo.getAllOtherMoney().collectAsState(initial = emptyList())

    if (localDrafts.isEmpty() || currentIndex >= localDrafts.size) {
        LaunchedEffect(Unit) { onDismiss() }
        return
    }

    val currentDraft = localDrafts[currentIndex]
    val isIncome = currentDraft.type == TransactionType.INCOME
    val accentColor = if (isIncome) WalletColors.income else WalletColors.expense

    val categories by repo.getCategories(currentDraft.type).collectAsState(initial = emptyList())
    var selectedCategory by remember(currentDraft) { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "Review UPI Activity (${currentIndex + 1}/${localDrafts.size})",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Filled.Close, contentDescription = "Close")
                }
            }
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth()
            ) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = if (isIncome) "+ ${formatCurrency(currentDraft.amount)}" else "- ${formatCurrency(currentDraft.amount)}",
                            style = MaterialTheme.typography.headlineLarge,
                            fontWeight = FontWeight.Bold,
                            color = accentColor
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            currentDraft.note,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(Modifier.height(4.dp))
                        val sdf = SimpleDateFormat("dd MMM, hh:mm a", Locale.getDefault())
                        Text(
                            sdf.format(currentDraft.date),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Spacer(Modifier.height(16.dp))

                Text(
                    "Select Category",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(8.dp))

                androidx.compose.foundation.lazy.LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(categories.size) { index ->
                        val cat = categories[index].name
                        val selected = selectedCategory == cat
                        FilterChip(
                            selected = selected,
                            onClick = { selectedCategory = cat },
                            label = { Text(cat) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = accentColor,
                                selectedLabelColor = MaterialTheme.colorScheme.background
                            )
                        )
                    }
                }
            }
        },
        confirmButton = {
            val approveContext = androidx.compose.ui.platform.LocalContext.current
            Button(
                onClick = {
                    if (selectedCategory.isBlank()) return@Button
                    scope.launch {
                        val w = wallet ?: return@launch
                        var newBank = w.bankBalance
                        var newCash = w.cashBalance

                        val delta = if (isIncome) currentDraft.amount else -currentDraft.amount
                        when (currentDraft.walletSource) {
                            WalletSource.BANK -> newBank += delta
                            WalletSource.CASH -> newCash += delta
                        }

                        // ── Mark as ignored so startup sync never re-creates this as a draft ──
                        val prefs = approveContext.getSharedPreferences("pockiq_ignored", android.content.Context.MODE_PRIVATE)
                        val ignoredSet = prefs.getStringSet("ignored_keys", emptySet<String>()) ?: emptySet<String>()
                        val utrRegex = java.util.regex.Pattern.compile("\\[UTR: ([a-zA-Z0-9]+)\\]")
                        val m = utrRegex.matcher(currentDraft.note)
                        val sig = if (m.find()) {
                            val utr = m.group(1)
                            if (utr != null && utr.isNotBlank()) "UTR_$utr"
                            else "${currentDraft.amount}_${currentDraft.type.name}_${currentDraft.date.time}"
                        } else {
                            "${currentDraft.amount}_${currentDraft.type.name}_${currentDraft.date.time}"
                        }
                        prefs.edit().putStringSet("ignored_keys", ignoredSet.toMutableSet().also { it.add(sig) }).apply()
                        // ─────────────────────────────────────────────────────────────────────

                        repo.updateWallet(newBank, newCash)
                        repo.addTransaction(
                            currentDraft.copy(
                                category = selectedCategory,
                                isDraft = false
                            )
                        )

                        if (currentIndex + 1 >= localDrafts.size) {
                            onDismiss()
                        } else {
                            currentIndex++
                        }
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = accentColor),
                enabled = selectedCategory.isNotBlank()
            ) {
                Icon(Icons.Filled.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(4.dp))
                Text("Approve")
            }
        },
        dismissButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                // Ignore/Delete
                val context = androidx.compose.ui.platform.LocalContext.current
                TextButton(
                    onClick = {
                        scope.launch {
                            val prefs = context.getSharedPreferences("pockiq_ignored", android.content.Context.MODE_PRIVATE)
                            val ignoredSet: Set<String> = prefs.getStringSet("ignored_keys", emptySet<String>()) ?: emptySet<String>()
                            
                            val utrRegex = java.util.regex.Pattern.compile("\\[UTR: ([a-zA-Z0-9]+)\\]")
                            val matcher = utrRegex.matcher(currentDraft.note)
                            val sig = if (matcher.find()) {
                                val utr = matcher.group(1)
                                if (utr != null && utr.isNotBlank()) "UTR_$utr" else "${currentDraft.amount}_${currentDraft.type.name}_${currentDraft.date.time}"
                            } else {
                                "${currentDraft.amount}_${currentDraft.type.name}_${currentDraft.date.time}"
                            }
                            
                            val newSet = ignoredSet.toMutableSet()
                            newSet.add(sig)
                            prefs.edit().putStringSet("ignored_keys", newSet).apply()

                            repo.deleteTransaction(currentDraft)
                            if (currentIndex + 1 >= localDrafts.size) {
                                onDismiss()
                            } else {
                                currentIndex++
                            }
                        }
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) {
                    Icon(Icons.Filled.Delete, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Ignore")
                }

                // Assign to Other Person
                val splitContext = androidx.compose.ui.platform.LocalContext.current
                TextButton(
                    onClick = {
                        scope.launch {
                            // ── Mark as ignored so startup sync never re-creates this draft ──
                            val prefs = splitContext.getSharedPreferences("pockiq_ignored", android.content.Context.MODE_PRIVATE)
                            val ignoredSet: Set<String> = prefs.getStringSet("ignored_keys", emptySet<String>()) ?: emptySet<String>()
                            val utrRegex = java.util.regex.Pattern.compile("\\[UTR: ([a-zA-Z0-9]+)\\]")
                            val matcher = utrRegex.matcher(currentDraft.note)
                            val sig = if (matcher.find()) {
                                val utr = matcher.group(1)
                                if (utr != null && utr.isNotBlank()) "UTR_$utr"
                                else "${currentDraft.amount}_${currentDraft.type.name}_${currentDraft.date.time}"
                            } else {
                                "${currentDraft.amount}_${currentDraft.type.name}_${currentDraft.date.time}"
                            }
                            val newSet = ignoredSet.toMutableSet()
                            newSet.add(sig)
                            prefs.edit().putStringSet("ignored_keys", newSet).apply()
                            // ─────────────────────────────────────────────────────────────────

                            val personsList = otherMoney.map { it.personName }.distinct()
                            val dir = if (isIncome) "RECEIVED" else "GIVEN"
                            
                            if (personsList.isEmpty()) {
                                onNavigateToAddOther(currentDraft.amount, currentDraft.note, dir, currentDraft.id)
                            } else {
                                onNavigateToOthersWithSplit(currentDraft.amount, currentDraft.note, dir, currentDraft.id)
                            }
                            
                            if (currentIndex + 1 >= localDrafts.size) {
                                onDismiss()
                            } else {
                                currentIndex++
                            }
                        }
                    }
                ) {
                    Icon(Icons.Filled.People, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Split")
                }
            }
        }
    )
}
