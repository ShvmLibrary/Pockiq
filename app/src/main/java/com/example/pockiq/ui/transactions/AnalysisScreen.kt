package com.example.pockiq.ui.transactions

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.pockiq.data.WalletRepository
import com.example.pockiq.data.db.TransactionEntity
import com.example.pockiq.data.db.TransactionType
import com.example.pockiq.theme.WalletColors
import com.example.pockiq.ui.dashboard.formatCurrency
import java.text.SimpleDateFormat
import java.util.*

data class CategoryShare(
    val category: String,
    val amount: Double,
    val percentage: Float,
    val color: Color
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AnalysisScreen(
    repo: WalletRepository,
    type: String,
    onBack: () -> Unit
) {
    val transactionType = if (type == "INCOME") TransactionType.INCOME else TransactionType.EXPENSE
    val themeColor = if (transactionType == TransactionType.INCOME) WalletColors.income else WalletColors.expense
    val allTxns by repo.getTransactionsByType(transactionType).collectAsState(initial = emptyList())

    val calendar = remember { mutableStateOf(Calendar.getInstance()) }
    val monthFormat = remember { SimpleDateFormat("MMMM yyyy", Locale.getDefault()) }
    val monthStr = remember(calendar.value) { monthFormat.format(calendar.value.time) }

    val filteredTxns = remember(allTxns, calendar.value) {
        allTxns.filter { txn ->
            val cal = Calendar.getInstance().apply { time = txn.date }
            cal.get(Calendar.YEAR) == calendar.value.get(Calendar.YEAR) &&
            cal.get(Calendar.MONTH) == calendar.value.get(Calendar.MONTH)
        }
    }

    val totalAmount = remember(filteredTxns) { filteredTxns.sumOf { it.amount } }

    val chartColors = listOf(
        Color(0xFF34C759), // Green
        Color(0xFF007AFF), // Blue
        Color(0xFFFF9500), // Orange
        Color(0xFFAF52DE), // Purple
        Color(0xFFFF2D55), // Red
        Color(0xFF5AC8FA), // Light Blue
        Color(0xFFFFCC00), // Yellow
        Color(0xFFE5E5EA), // Light Silver
        Color(0xFF8E8E93), // Slate
        Color(0xFFD1D1D6), // Grey
    )

    val categoryShares = remember(filteredTxns) {
        val grouped = filteredTxns.groupBy { it.category }
        if (totalAmount == 0.0) emptyList()
        else {
            grouped.entries.mapIndexed { index, entry ->
                val sum = entry.value.sumOf { it.amount }
                val color = chartColors[index % chartColors.size]
                CategoryShare(
                    category = entry.key,
                    amount = sum,
                    percentage = (sum / totalAmount).toFloat(),
                    color = color
                )
            }.sortedByDescending { it.amount }
        }
    }

    val topCategory = remember(categoryShares) { categoryShares.firstOrNull() }
    val averageTxn = remember(filteredTxns, totalAmount) {
        if (filteredTxns.isEmpty()) 0.0 else totalAmount / filteredTxns.size
    }

    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { visible = true }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = if (transactionType == TransactionType.INCOME) "Income Analytics" else "Expense Analytics",
                        fontWeight = FontWeight.Bold
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface)
            )
        },
        contentWindowInsets = WindowInsets(0.dp)
    ) { innerPadding ->
        AnimatedVisibility(
            visible = visible,
            enter = fadeIn(tween(600)) + slideInVertically(tween(600)) { 40 }
        ) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.background)
                    .padding(innerPadding),
                contentPadding = PaddingValues(bottom = 32.dp)
            ) {
                // Month Selector Header
                item {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)),
                        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.08f))
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            IconButton(
                                onClick = {
                                    val cal = Calendar.getInstance().apply {
                                        time = calendar.value.time
                                        add(Calendar.MONTH, -1)
                                    }
                                    calendar.value = cal
                                }
                            ) {
                                Icon(Icons.Filled.ChevronLeft, contentDescription = "Previous Month")
                            }

                            Text(
                                text = monthStr,
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )

                            IconButton(
                                onClick = {
                                    val cal = Calendar.getInstance().apply {
                                        time = calendar.value.time
                                        add(Calendar.MONTH, 1)
                                    }
                                    calendar.value = cal
                                }
                            ) {
                                Icon(Icons.Filled.ChevronRight, contentDescription = "Next Month")
                            }
                        }
                    }
                }

                // If no data
                if (filteredTxns.isEmpty()) {
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 80.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(
                                    imageVector = Icons.Filled.BarChart,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f),
                                    modifier = Modifier.size(72.dp)
                                )
                                Spacer(Modifier.height(16.dp))
                                Text(
                                    text = "No entries for this month",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    text = "Try adding a transaction or choosing another month",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                                )
                            }
                        }
                    }
                } else {
                    // Donut Chart Card
                    item {
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp),
                            shape = RoundedCornerShape(24.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)),
                            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.08f))
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(24.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(
                                    text = "Category Share",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Spacer(Modifier.height(24.dp))

                                // Donut Chart Rendering
                                Box(
                                    modifier = Modifier.size(200.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Canvas(modifier = Modifier.size(170.dp)) {
                                        var startAngle = -90f
                                        categoryShares.forEach { share ->
                                            val sweepAngle = share.percentage * 360f
                                            drawArc(
                                                color = share.color,
                                                startAngle = startAngle,
                                                sweepAngle = sweepAngle,
                                                useCenter = false,
                                                style = Stroke(
                                                    width = 20.dp.toPx()
                                                )
                                            )
                                            startAngle += sweepAngle
                                        }
                                    }
                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        Text(
                                            text = "Total Spent",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        Spacer(Modifier.height(4.dp))
                                        Text(
                                            text = formatCurrency(totalAmount),
                                            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                                            color = themeColor
                                        )
                                    }
                                }

                                Spacer(Modifier.height(24.dp))

                                // Legend / Details of Categories
                                categoryShares.forEach { share ->
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 6.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            modifier = Modifier.weight(1f)
                                        ) {
                                            Box(
                                                modifier = Modifier
                                                    .size(12.dp)
                                                    .clip(CircleShape)
                                                    .background(share.color)
                                            )
                                            Spacer(Modifier.width(10.dp))
                                            Text(
                                                text = share.category,
                                                style = MaterialTheme.typography.bodyMedium,
                                                fontWeight = FontWeight.SemiBold,
                                                color = MaterialTheme.colorScheme.onSurface,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(
                                                text = formatCurrency(share.amount),
                                                style = MaterialTheme.typography.bodyMedium,
                                                fontWeight = FontWeight.Bold,
                                                color = MaterialTheme.colorScheme.onSurface
                                            )
                                            Spacer(Modifier.width(12.dp))
                                            Surface(
                                                shape = RoundedCornerShape(6.dp),
                                                color = share.color.copy(alpha = 0.12f)
                                            ) {
                                                Text(
                                                    text = "${(share.percentage * 100).toInt()}%",
                                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                                    style = MaterialTheme.typography.labelSmall,
                                                    fontWeight = FontWeight.Bold,
                                                    color = share.color
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    item { Spacer(Modifier.height(16.dp)) }

                    // Key Stats Grid
                    item {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                StatCard(
                                    title = "Avg. Transaction",
                                    value = formatCurrency(averageTxn),
                                    icon = Icons.Filled.Calculate,
                                    color = MaterialTheme.colorScheme.secondary
                                )
                                Spacer(Modifier.height(12.dp))
                                StatCard(
                                    title = "Transaction Count",
                                    value = "${filteredTxns.size} items",
                                    icon = Icons.Filled.Numbers,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                            Column(modifier = Modifier.weight(1f)) {
                                StatCard(
                                    title = "Top Category",
                                    value = topCategory?.category ?: "None",
                                    subtitle = topCategory?.let { "${(it.percentage * 100).toInt()}% share" } ?: "",
                                    icon = Icons.Filled.TrendingUp,
                                    color = topCategory?.color ?: MaterialTheme.colorScheme.tertiary
                                )
                            }
                        }
                    }

                    item { Spacer(Modifier.height(20.dp)) }

                    // Detailed List Section Header
                    item {
                        Text(
                            text = "Monthly Ledger",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onBackground,
                            modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)
                        )
                    }

                    // Detailed List Items
                    val sdf = SimpleDateFormat("dd MMM, hh:mm a", Locale.getDefault())
                    items(filteredTxns, key = { it.id }) { txn ->
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 20.dp, vertical = 4.dp),
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)),
                            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.08f))
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(
                                    modifier = Modifier.weight(1f),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(40.dp)
                                            .clip(RoundedCornerShape(10.dp))
                                            .background(themeColor.copy(alpha = 0.15f)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        val itemIcon = if (transactionType == TransactionType.INCOME) Icons.Filled.ArrowDownward else Icons.Filled.ArrowUpward
                                        Icon(itemIcon, contentDescription = null, tint = themeColor, modifier = Modifier.size(20.dp))
                                    }
                                    Spacer(Modifier.width(12.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = txn.category,
                                            style = MaterialTheme.typography.titleMedium,
                                            color = MaterialTheme.colorScheme.onSurface,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        if (txn.note.isNotBlank()) {
                                            Text(
                                                text = txn.note,
                                                style = MaterialTheme.typography.bodyMedium,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }
                                        Text(
                                            text = sdf.format(txn.date),
                                            style = MaterialTheme.typography.labelMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        Surface(
                                            shape = RoundedCornerShape(4.dp),
                                            color = MaterialTheme.colorScheme.secondaryContainer
                                        ) {
                                            Text(
                                                text = txn.walletSource.name,
                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                                style = MaterialTheme.typography.labelMedium,
                                                color = MaterialTheme.colorScheme.onSecondaryContainer
                                            )
                                        }
                                    }
                                }
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    text = "${if (transactionType == TransactionType.INCOME) "+" else "-"}${formatCurrency(txn.amount)}",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = themeColor,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun StatCard(
    title: String,
    value: String,
    subtitle: String = "",
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    color: Color
) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.08f))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .background(color.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, null, tint = color, modifier = Modifier.size(16.dp))
            }
            Spacer(Modifier.height(12.dp))
            Text(title, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(4.dp))
            Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (subtitle.isNotBlank()) {
                Spacer(Modifier.height(2.dp))
                Text(subtitle, style = MaterialTheme.typography.labelSmall, color = color, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}
