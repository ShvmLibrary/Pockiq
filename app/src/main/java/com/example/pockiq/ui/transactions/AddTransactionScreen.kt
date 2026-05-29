package com.example.pockiq.ui.transactions

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.example.pockiq.data.WalletRepository
import com.example.pockiq.data.db.CategoryEntity
import com.example.pockiq.data.db.TransactionEntity
import com.example.pockiq.data.db.TransactionType
import com.example.pockiq.data.db.WalletSource
import com.example.pockiq.theme.WalletColors
import kotlinx.coroutines.launch
import java.util.*
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun AddTransactionScreen(
    repo: WalletRepository,
    editTransactionId: Long? = null,
    prefilledType: String? = null,
    onDone: () -> Unit
) {
    var type         by remember {
        mutableStateOf(
            if (prefilledType?.uppercase() == "INCOME") TransactionType.INCOME
            else TransactionType.EXPENSE
        )
    }
    var amount       by remember { mutableStateOf("") }
    var category     by remember { mutableStateOf("") }
    var note         by remember { mutableStateOf("") }
    var walletSource by remember { mutableStateOf(WalletSource.BANK) }
    var error        by remember { mutableStateOf("") }

    if (editTransactionId != null) {
        LaunchedEffect(editTransactionId) {
            repo.getTransactionById(editTransactionId)?.let { existing ->
                type         = existing.type
                amount       = existing.amount.toString()
                category     = existing.category
                note         = existing.note
                walletSource = existing.walletSource
            }
        }
    }

    var showAddDialog    by remember { mutableStateOf(false) }
    var newCategoryName  by remember { mutableStateOf("") }
    var showDeleteDialog by remember { mutableStateOf<CategoryEntity?>(null) }
    var showCalculator   by remember { mutableStateOf(false) }
    // Edit mode state (for delete)
    var isEditMode       by remember { mutableStateOf(false) }

    // Drag-to-reorder state
    var draggedIndex  by remember { mutableStateOf<Int?>(null) }
    var dragOffset    by remember { mutableStateOf(0f) }
    // Use a plain HashMap (no recomposition on update) for measured chip widths
    val measuredWidths = remember { HashMap<Int, Float>() }

    val scope  = rememberCoroutineScope()
    val wallet by repo.getWallet().collectAsState(initial = null)

    val isIncome    = type == TransactionType.INCOME
    val accentColor = if (isIncome) WalletColors.income else WalletColors.expense
    val categories  by repo.getCategories(type).collectAsState(initial = emptyList())
    val allTxns     by repo.getAllTransactions().collectAsState(initial = emptyList())

    val context = LocalContext.current
    val prefs = remember(context) {
        context.getSharedPreferences("pockiq_categories_order", android.content.Context.MODE_PRIVATE)
    }
    var manualOrderStr by remember(type) {
        mutableStateOf(prefs.getString("category_order_${type.name}", "") ?: "")
    }

    val manualOrderList = remember(manualOrderStr) {
        if (manualOrderStr.isBlank()) emptyList() else manualOrderStr.split(",")
    }

    val frequencies = remember(allTxns, type) {
        allTxns.filter { it.type == type }
            .groupBy { it.category }
            .mapValues { it.value.size }
    }

    val sortedCategories = remember(categories, frequencies, manualOrderList) {
        categories.sortedWith(Comparator { a, b ->
            val idxA = manualOrderList.indexOf(a.name)
            val idxB = manualOrderList.indexOf(b.name)
            when {
                idxA != -1 && idxB != -1 -> idxA.compareTo(idxB)
                idxA != -1               -> -1
                idxB != -1               ->  1
                else -> {
                    val freqA = frequencies[a.name] ?: 0
                    val freqB = frequencies[b.name] ?: 0
                    if (freqA != freqB) freqB.compareTo(freqA)
                    else a.name.compareTo(b.name)
                }
            }
        })
    }

    // Live working copy for real-time drag reordering — resets when DB list changes (and not dragging)
    var workingCategories by remember(sortedCategories) { mutableStateOf(sortedCategories.toList()) }
    LaunchedEffect(sortedCategories) {
        if (draggedIndex == null) workingCategories = sortedCategories.toList()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .verticalScroll(rememberScrollState())
            .navigationBarsPadding()
    ) {
        // ── Top bar ─────────────────────────────────────────────────────────
        Row(
            modifier          = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 20.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onDone) {
                Icon(Icons.Filled.ArrowBack, contentDescription = "Back",
                    tint = MaterialTheme.colorScheme.onBackground)
            }
            Spacer(Modifier.width(8.dp))
            Text(
                "Add Transaction",
                style      = MaterialTheme.typography.headlineMedium,
                color      = MaterialTheme.colorScheme.onBackground,
                fontWeight = FontWeight.Bold
            )
        }

        // ── Type Toggle ──────────────────────────────────────────────────────
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
        ) {
            listOf(TransactionType.INCOME, TransactionType.EXPENSE).forEach { t ->
                val selected = type == t
                val color    = if (t == TransactionType.INCOME) WalletColors.income else WalletColors.expense
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(12.dp))
                        .background(if (selected) color else Color.Transparent)
                        .clickable { type = t; category = ""; isEditMode = false }
                        .padding(vertical = 12.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        t.name.lowercase().replaceFirstChar { it.uppercase() },
                        color      = if (selected) MaterialTheme.colorScheme.background
                                     else MaterialTheme.colorScheme.onSurfaceVariant,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }

        Spacer(Modifier.height(20.dp))

        // ── Amount ───────────────────────────────────────────────────────────
        Column(modifier = Modifier.padding(horizontal = 20.dp)) {
            Text("Amount", style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(6.dp))
            OutlinedTextField(
                value           = amount,
                onValueChange   = { amount = it; error = "" },
                modifier        = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                prefix          = { Text("₹", fontWeight = FontWeight.Bold, color = accentColor) },
                trailingIcon    = {
                    IconButton(onClick = { showCalculator = true }) {
                        Icon(Icons.Filled.Calculate, contentDescription = "Calculator",
                            tint = accentColor, modifier = Modifier.size(24.dp))
                    }
                },
                singleLine = true,
                shape      = RoundedCornerShape(12.dp),
                colors     = OutlinedTextFieldDefaults.colors(focusedBorderColor = accentColor)
            )
        }

        Spacer(Modifier.height(20.dp))

        // ── Category ─────────────────────────────────────────────────────────
        Column(modifier = Modifier.padding(horizontal = 20.dp)) {

            // Label row with Edit / Done toggle
            Row(
                modifier             = Modifier.fillMaxWidth(),
                verticalAlignment    = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("Category", style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)

                if (workingCategories.isNotEmpty()) {
                    TextButton(
                        onClick            = { isEditMode = !isEditMode },
                        contentPadding     = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                    ) {
                        Icon(
                            imageVector        = if (isEditMode) Icons.Filled.Check else Icons.Filled.Edit,
                            contentDescription = if (isEditMode) "Done editing" else "Edit categories",
                            modifier           = Modifier.size(14.dp),
                            tint               = if (isEditMode) accentColor
                                                 else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(
                            if (isEditMode) "Done" else "Edit",
                            style = MaterialTheme.typography.labelSmall,
                            color = if (isEditMode) accentColor
                                    else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }



            Spacer(Modifier.height(8.dp))

            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment     = Alignment.CenterVertically
            ) {
                items(workingCategories.size) { i ->
                    val catEntity = workingCategories[i]
                    val catName   = catEntity.name
                    val selected  = category == catName
                    val isDragged = draggedIndex == i

                    // Outer Box handles: measurement, float offset, scale, z-order, drag gesture
                    Box(
                        contentAlignment = Alignment.TopEnd,
                        modifier = Modifier
                            .onGloballyPositioned { coords ->
                                measuredWidths[i] = coords.size.width.toFloat()
                            }
                            .offset {
                                if (isDragged) IntOffset(dragOffset.roundToInt(), 0)
                                else IntOffset.Zero
                            }
                            .graphicsLayer {
                                if (isDragged) {
                                    scaleX          = 1.07f
                                    scaleY          = 1.07f
                                    shadowElevation = 14.dp.toPx()
                                    alpha           = 0.92f
                                }
                            }
                            .zIndex(if (isDragged) 10f else 0f)
                            .then(
                                // Drag gesture is only active outside edit mode
                                if (!isEditMode) {
                                    Modifier.pointerInput(i, workingCategories.size) {
                                        detectDragGesturesAfterLongPress(
                                            onDragStart = {
                                                draggedIndex = i
                                                dragOffset   = 0f
                                            },
                                            onDragEnd = {
                                                // Persist the final live order
                                                val names = workingCategories
                                                    .map { it.name }.joinToString(",")
                                                prefs.edit()
                                                    .putString("category_order_${type.name}", names)
                                                    .apply()
                                                manualOrderStr = names
                                                draggedIndex   = null
                                                dragOffset     = 0f
                                            },
                                            onDragCancel = {
                                                draggedIndex = null
                                                dragOffset   = 0f
                                            },
                                            onDrag = { change, dragAmount ->
                                                change.consume()
                                                dragOffset += dragAmount.x
                                                val curIdx = draggedIndex
                                                    ?: return@detectDragGesturesAfterLongPress
                                                val GAP = 8.dp.toPx()

                                                when {
                                                    // ── Dragging right: swap with next when center crosses half of next chip ──
                                                    dragOffset > 0f && curIdx < workingCategories.size - 1 -> {
                                                        val nextW = measuredWidths[curIdx + 1]
                                                            ?: 90.dp.toPx()
                                                        if (dragOffset >= nextW / 2f) {
                                                            val m = workingCategories.toMutableList()
                                                            m.add(curIdx + 1, m.removeAt(curIdx))
                                                            workingCategories = m
                                                            // Swap stored widths so they stay aligned with positions
                                                            val myW = measuredWidths[curIdx] ?: nextW
                                                            measuredWidths[curIdx]     = nextW
                                                            measuredWidths[curIdx + 1] = myW
                                                            // Adjust visual offset so chip doesn't jump
                                                            dragOffset -= (nextW + GAP)
                                                            draggedIndex = curIdx + 1
                                                        }
                                                    }
                                                    // ── Dragging left: swap with previous when center crosses half of prev chip ──
                                                    dragOffset < 0f && curIdx > 0 -> {
                                                        val prevW = measuredWidths[curIdx - 1]
                                                            ?: 90.dp.toPx()
                                                        if (dragOffset <= -(prevW / 2f)) {
                                                            val m = workingCategories.toMutableList()
                                                            m.add(curIdx - 1, m.removeAt(curIdx))
                                                            workingCategories = m
                                                            val myW = measuredWidths[curIdx] ?: prevW
                                                            measuredWidths[curIdx]     = prevW
                                                            measuredWidths[curIdx - 1] = myW
                                                            dragOffset += (prevW + GAP)
                                                            draggedIndex = curIdx - 1
                                                        }
                                                    }
                                                }
                                            }
                                        )
                                    }
                                } else Modifier
                            )
                    ) {
                        // Inner Surface: visual chip
                        Surface(
                            shape  = RoundedCornerShape(8.dp),
                            color  = when {
                                isDragged -> accentColor.copy(alpha = 0.88f)
                                selected  -> accentColor
                                else      -> MaterialTheme.colorScheme.surfaceVariant
                            },
                            border = BorderStroke(
                                1.dp,
                                if (selected || isDragged) accentColor
                                else MaterialTheme.colorScheme.outlineVariant
                            ),
                            modifier = Modifier
                                .padding(vertical = 4.dp)
                                .clickable(enabled = !isEditMode) { category = catName }
                        ) {
                            Text(
                                text     = catName,
                                style    = MaterialTheme.typography.labelMedium,
                                color    = if (selected || isDragged)
                                               MaterialTheme.colorScheme.background
                                           else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                            )
                        }

                        // ✕ Delete badge — visible only in edit mode
                        if (isEditMode) {
                            Box(
                                modifier = Modifier
                                    .size(18.dp)
                                    .offset(x = 5.dp, y = (-5).dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.error)
                                    .clickable { showDeleteDialog = catEntity },
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector        = Icons.Filled.Close,
                                    contentDescription = "Delete $catName",
                                    tint               = Color.White,
                                    modifier           = Modifier.size(10.dp)
                                )
                            }
                        }
                    }
                }

                // Add New chip
                item {
                    FilterChip(
                        selected = false,
                        onClick  = { showAddDialog = true; isEditMode = false },
                        label    = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Filled.Add, contentDescription = null,
                                    modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("Add New", style = MaterialTheme.typography.labelMedium)
                            }
                        },
                        shape = RoundedCornerShape(8.dp)
                    )
                }
            }
        }

        Spacer(Modifier.height(20.dp))

        // ── Wallet Source ────────────────────────────────────────────────────
        Column(modifier = Modifier.padding(horizontal = 20.dp)) {
            Text("Pay from", style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                listOf(WalletSource.BANK to "Bank", WalletSource.CASH to "Cash").forEach { (src, label) ->
                    val selected = walletSource == src
                    FilterChip(
                        selected = selected,
                        onClick  = { walletSource = src },
                        label    = { Text(label) },
                        colors   = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = accentColor,
                            selectedLabelColor     = MaterialTheme.colorScheme.background
                        )
                    )
                }
            }
        }

        Spacer(Modifier.height(20.dp))

        // ── Note ─────────────────────────────────────────────────────────────
        Column(modifier = Modifier.padding(horizontal = 20.dp)) {
            Text("Note (optional)", style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(6.dp))
            OutlinedTextField(
                value         = note,
                onValueChange = { note = it },
                modifier      = Modifier.fillMaxWidth(),
                placeholder   = { Text("Add a note...") },
                shape         = RoundedCornerShape(12.dp),
                maxLines      = 3
            )
        }

        AnimatedVisibility(error.isNotBlank()) {
            Text(
                error,
                color    = MaterialTheme.colorScheme.error,
                style    = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp)
            )
        }

        Spacer(Modifier.height(28.dp))

        // ── Save Button ───────────────────────────────────────────────────────
        Button(
            onClick = {
                val amt = amount.toDoubleOrNull()
                when {
                    amt == null || amt <= 0 -> error = "Enter a valid amount"
                    category.isBlank()      -> error = "Please select a category"
                    else -> {
                        scope.launch {
                            val w = wallet
                            if (w != null) {
                                var newBank = w.bankBalance
                                var newCash = w.cashBalance

                                if (editTransactionId != null) {
                                    val oldTx = repo.getTransactionById(editTransactionId)
                                    if (oldTx != null) {
                                        val oldDelta = if (oldTx.type == TransactionType.INCOME) -oldTx.amount else oldTx.amount
                                        when (oldTx.walletSource) {
                                            WalletSource.BANK -> newBank += oldDelta
                                            WalletSource.CASH -> newCash += oldDelta
                                        }
                                    }
                                }

                                val delta = if (type == TransactionType.INCOME) amt else -amt
                                when (walletSource) {
                                    WalletSource.BANK -> newBank += delta
                                    WalletSource.CASH -> newCash += delta
                                }
                                repo.updateWallet(newBank, newCash)
                            }

                            repo.addTransaction(
                                TransactionEntity(
                                    id           = editTransactionId ?: 0,
                                    type         = type,
                                    amount       = amt,
                                    category     = category,
                                    note         = note,
                                    date         = Date(),
                                    walletSource = walletSource
                                )
                            )
                            onDone()
                        }
                    }
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .height(54.dp),
            shape  = RoundedCornerShape(14.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = accentColor,
                contentColor   = MaterialTheme.colorScheme.background
            )
        ) {
            Icon(Icons.Filled.Check, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text("Save Transaction", fontWeight = FontWeight.Bold)
        }

        Spacer(Modifier.height(24.dp))
    }

    // ── Dialogs ───────────────────────────────────────────────────────────────

    if (showAddDialog) {
        AlertDialog(
            onDismissRequest = { showAddDialog = false; newCategoryName = "" },
            title = { Text("Add New Category") },
            text  = {
                Column {
                    Text(
                        "Create a custom ${type.name.lowercase()} category.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(12.dp))
                    OutlinedTextField(
                        value         = newCategoryName,
                        onValueChange = { newCategoryName = it },
                        label         = { Text("Category Name (e.g. Movies)") },
                        singleLine    = true,
                        modifier      = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (newCategoryName.isNotBlank()) {
                            scope.launch {
                                repo.addCategory(newCategoryName.trim(), type)
                                category = newCategoryName.trim()
                                showAddDialog   = false
                                newCategoryName = ""
                            }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = accentColor)
                ) { Text("Add") }
            },
            dismissButton = {
                TextButton(onClick = { showAddDialog = false; newCategoryName = "" }) {
                    Text("Cancel")
                }
            }
        )
    }

    if (showDeleteDialog != null) {
        val catToDelete = showDeleteDialog!!
        AlertDialog(
            onDismissRequest = { showDeleteDialog = null },
            title = { Text("Delete Category?") },
            text  = {
                Text("Delete \"${catToDelete.name}\"? This won't remove existing transactions.")
            },
            confirmButton = {
                Button(
                    onClick = {
                        scope.launch {
                            repo.deleteCategory(catToDelete)
                            if (category == catToDelete.name) category = ""
                            showDeleteDialog = null
                        }
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error
                    )
                ) { Text("Delete") }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = null }) { Text("Cancel") }
            }
        )
    }

    if (showCalculator) {
        CalculatorDialog(
            initialValue = amount,
            accentColor  = accentColor,
            onDismiss    = { showCalculator = false },
            onApply      = { result -> amount = result; error = "" }
        )
    }
}

// ── Calculator Dialog ─────────────────────────────────────────────────────────

@Composable
fun CalculatorDialog(
    initialValue: String,
    accentColor:  Color,
    onDismiss:    () -> Unit,
    onApply:      (String) -> Unit
) {
    var expression by remember { mutableStateOf(initialValue.ifBlank { "" }) }
    val evaluatedResult by remember(expression) {
        derivedStateOf {
            val res = evaluateExpression(expression)
            if (res != null) {
                if (res % 1.0 == 0.0) res.toLong().toString()
                else String.format(Locale.US, "%.2f", res)
            } else ""
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Calculator", fontWeight = FontWeight.Bold) },
        text  = {
            Column(
                modifier            = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape    = RoundedCornerShape(12.dp),
                    colors   = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                    ),
                    border   = BorderStroke(1.dp, Color.White.copy(alpha = 0.08f))
                ) {
                    Column(
                        modifier            = Modifier.fillMaxWidth().padding(16.dp),
                        horizontalAlignment = Alignment.End
                    ) {
                        Text(
                            text     = expression.ifEmpty { "0" },
                            style    = MaterialTheme.typography.titleLarge.copy(fontSize = 26.sp),
                            color    = MaterialTheme.colorScheme.onSurface,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            text       = if (evaluatedResult.isNotEmpty()) "= $evaluatedResult" else "",
                            style      = MaterialTheme.typography.titleMedium,
                            color      = accentColor,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                val buttons = listOf(
                    listOf("C", "DEL", "/", "*"),
                    listOf("7", "8", "9", "-"),
                    listOf("4", "5", "6", "+"),
                    listOf("1", "2", "3", "="),
                    listOf("0", ".", "Cancel", "Apply")
                )

                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    buttons.forEach { row ->
                        Row(
                            modifier            = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            row.forEach { char ->
                                val isDigit  = char.first().isDigit() || char == "."
                                val isAction = char == "Cancel" || char == "Apply"
                                val btnBg = when {
                                    char == "Apply"  -> accentColor
                                    char == "Cancel" -> MaterialTheme.colorScheme.surfaceVariant
                                    isDigit          -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
                                    else             -> MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                                }
                                val btnText = when {
                                    char == "Apply"  -> MaterialTheme.colorScheme.background
                                    char == "Cancel" -> MaterialTheme.colorScheme.onSurfaceVariant
                                    isDigit          -> MaterialTheme.colorScheme.onSurface
                                    else             -> accentColor
                                }

                                Button(
                                    onClick = {
                                        when (char) {
                                            "C"      -> expression = ""
                                            "DEL"    -> if (expression.isNotEmpty())
                                                            expression = expression.dropLast(1)
                                            "="      -> if (evaluatedResult.isNotEmpty())
                                                            expression = evaluatedResult
                                            "Cancel" -> onDismiss()
                                            "Apply"  -> {
                                                val finalVal = if (evaluatedResult.isNotEmpty())
                                                    evaluatedResult else expression
                                                if (finalVal.isNotEmpty() &&
                                                    (evaluateExpression(finalVal) != null || finalVal.toDoubleOrNull() != null)) {
                                                    onApply(finalVal)
                                                }
                                                onDismiss()
                                            }
                                            else -> {
                                                val lastChar = expression.lastOrNull()
                                                val isOp     = char in listOf("+", "-", "*", "/")
                                                val isLastOp = lastChar in listOf('+', '-', '*', '/')
                                                expression = if (isOp && isLastOp)
                                                    expression.dropLast(1) + char
                                                else
                                                    expression + char
                                            }
                                        }
                                    },
                                    modifier        = Modifier.weight(1f).height(48.dp),
                                    shape           = RoundedCornerShape(10.dp),
                                    colors          = ButtonDefaults.buttonColors(
                                        containerColor = btnBg, contentColor = btnText),
                                    contentPadding  = PaddingValues(0.dp)
                                ) {
                                    Text(char, fontWeight = FontWeight.Bold,
                                        style = MaterialTheme.typography.bodyLarge)
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {}
    )
}

// ── Expression Evaluator ──────────────────────────────────────────────────────

fun evaluateExpression(expr: String): Double? {
    if (expr.isBlank()) return null
    return try {
        val clean = expr.replace(" ", "").replace("x", "*").replace("X", "*")
        SimpleParser(clean).parse()
    } catch (e: Exception) { null }
}

class SimpleParser(val input: String) {
    private var pos = 0

    fun parse(): Double {
        var result = parseTerm()
        while (pos < input.length) {
            when (input[pos]) {
                '+' -> { pos++; result += parseTerm() }
                '-' -> { pos++; result -= parseTerm() }
                else -> break
            }
        }
        return result
    }

    private fun parseTerm(): Double {
        var result = parseFactor()
        while (pos < input.length) {
            when (input[pos]) {
                '*' -> { pos++; result *= parseFactor() }
                '/' -> {
                    pos++
                    val d = parseFactor()
                    if (d == 0.0) throw ArithmeticException("Division by zero")
                    result /= d
                }
                else -> break
            }
        }
        return result
    }

    private fun parseFactor(): Double {
        if (pos >= input.length) throw NoSuchElementException("Unexpected end")
        var isNegative = false
        if      (input[pos] == '-') { isNegative = true; pos++ }
        else if (input[pos] == '+') { pos++ }
        val start = pos
        while (pos < input.length && (input[pos].isDigit() || input[pos] == '.')) pos++
        if (start == pos) throw IllegalArgumentException("Expected number at $start")
        val value = input.substring(start, pos).toDoubleOrNull() ?: 0.0
        return if (isNegative) -value else value
    }
}
