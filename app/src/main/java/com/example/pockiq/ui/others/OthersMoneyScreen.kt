package com.example.pockiq.ui.others

import androidx.compose.foundation.background
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.pockiq.data.WalletRepository
import com.example.pockiq.data.db.OtherMoneyDirection
import com.example.pockiq.theme.WalletColors
import com.example.pockiq.ui.dashboard.formatCurrency
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OthersMoneyScreen(
    repo: WalletRepository,
    bottomPadding: androidx.compose.ui.unit.Dp = 0.dp,
    splitAmount: Double? = null,
    splitNote: String? = null,
    splitDirection: String? = null,
    splitDraftId: Long? = null,
    onAddEntry: (String, Double?, String?, String?, Long?) -> Unit,
    onPersonClick: (String, Double?, String?, String?, Long?) -> Unit
) {
    val allEntries by repo.getAllOtherMoney().collectAsState(initial = emptyList())
    val persons by repo.getDistinctPersons().collectAsState(initial = emptyList())

    val totalHolding = allEntries
        .filter { it.direction == OtherMoneyDirection.RECEIVED }
        .sumOf { it.amount }
    val totalGiven = allEntries
        .filter { it.direction == OtherMoneyDirection.GIVEN }
        .sumOf { it.amount }
    val net = totalHolding - totalGiven

    Scaffold(
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { onAddEntry("", splitAmount, splitNote, splitDirection, splitDraftId) },
                icon = { Icon(Icons.Filled.Add, null) },
                text = { Text("Add Entry") },
                containerColor = WalletColors.other,
                contentColor = Color(0xFF1A1A1A),
                modifier = Modifier.padding(bottom = bottomPadding)
            )
        },
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
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .padding(horizontal = 20.dp, vertical = 24.dp)
                ) {
                    Column {
                        Text(
                            "Others' Money",
                            style = MaterialTheme.typography.headlineLarge,
                            color = WalletColors.other,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "Track money you hold on behalf of others",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            // Split Selection Helper Banner
            if (splitAmount != null) {
                item {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp, vertical = 4.dp),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = WalletColors.other.copy(alpha = 0.12f)
                        ),
                        border = BorderStroke(1.dp, WalletColors.other.copy(alpha = 0.25f))
                    ) {
                        Row(
                            modifier = Modifier.padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Info,
                                contentDescription = null,
                                tint = WalletColors.other,
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(Modifier.width(12.dp))
                            Column {
                                Text(
                                    "Split Draft Selection Mode",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = WalletColors.other,
                                    fontWeight = FontWeight.Bold
                                )
                                Spacer(Modifier.height(2.dp))
                                Text(
                                    "Select an existing person below (or click Add Entry) to split and assign this transaction of ₹${formatCurrency(splitAmount)}.",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                }
            }

            // Summary cards
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OtherSummaryCard(modifier = Modifier.weight(1f), label = "Received", amount = totalHolding, color = WalletColors.income)
                    OtherSummaryCard(modifier = Modifier.weight(1f), label = "Given Back", amount = totalGiven, color = WalletColors.expense)
                }
            }

            item { Spacer(Modifier.height(10.dp)) }

            // Net banner
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = if (net >= 0) WalletColors.income.copy(alpha = 0.12f) else WalletColors.expense.copy(alpha = 0.12f)
                    )
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            if (net >= 0) "You currently hold" else "You have given back more",
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

            item { Spacer(Modifier.height(20.dp)) }

            item {
                Text(
                    "People",
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onBackground,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(horizontal = 20.dp)
                )
                Spacer(Modifier.height(8.dp))
            }

            if (persons.isEmpty()) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(60.dp),
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
                            Text("No entries yet", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            } else {
                items(persons) { person ->
                    val personEntries = allEntries.filter { it.personName == person }
                    val personNet = personEntries.sumOf {
                        if (it.direction == OtherMoneyDirection.RECEIVED) it.amount else -it.amount
                    }
                    PersonCard(
                        name = person,
                        net = personNet,
                        entryCount = personEntries.size,
                        onClick = {
                            if (splitAmount != null) {
                                onAddEntry(person, splitAmount, splitNote, splitDirection, splitDraftId)
                            } else {
                                onPersonClick(person, null, null, null, null)
                            }
                        },
                        onAdd = {
                            onAddEntry(person, splitAmount, splitNote, splitDirection, splitDraftId)
                        }
                    )
                }
            }
        }
    }
}

@Composable
fun OtherSummaryCard(modifier: Modifier, label: String, amount: Double, color: Color) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = color.copy(alpha = 0.12f)),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.08f)),
        elevation = CardDefaults.cardElevation(0.dp)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(label, style = MaterialTheme.typography.labelMedium, color = color)
            Spacer(Modifier.height(4.dp))
            Text(formatCurrency(amount), style = MaterialTheme.typography.titleMedium, color = color, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
fun PersonCard(name: String, net: Double, entryCount: Int, onClick: () -> Unit, onAdd: () -> Unit) {
    val netColor = if (net >= 0) WalletColors.income else WalletColors.expense
    val initial = name.firstOrNull()?.uppercaseChar() ?: '?'

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 5.dp)
            .clickable { onClick() },
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.08f)),
        elevation = CardDefaults.cardElevation(0.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                // Avatar
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(WalletColors.other.copy(alpha = 0.2f)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        initial.toString(),
                        style = MaterialTheme.typography.titleLarge,
                        color = WalletColors.other,
                        fontWeight = FontWeight.Bold
                    )
                }
                Spacer(Modifier.width(12.dp))
                Column {
                    Text(name, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
                    Text("$entryCount transaction${if (entryCount != 1) "s" else ""}", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        if (net >= 0) "Holding" else "Over-given",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        formatCurrency(kotlin.math.abs(net)),
                        style = MaterialTheme.typography.titleMedium,
                        color = netColor,
                        fontWeight = FontWeight.Bold
                    )
                }
                Spacer(Modifier.width(8.dp))
                IconButton(onClick = onAdd, colors = IconButtonDefaults.iconButtonColors(containerColor = WalletColors.other.copy(alpha = 0.15f))) {
                    Icon(Icons.Filled.Add, null, tint = WalletColors.other)
                }
            }
        }
    }
}
