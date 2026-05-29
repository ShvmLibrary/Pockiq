package com.example.pockiq

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.ui.NavDisplay
import com.example.pockiq.data.WalletRepository
import com.example.pockiq.ui.dashboard.DashboardScreen
import com.example.pockiq.ui.others.AddOtherMoneyScreen
import com.example.pockiq.ui.others.OthersMoneyScreen
import com.example.pockiq.ui.others.PersonLedgerScreen
import com.example.pockiq.ui.transactions.AddTransactionScreen
import com.example.pockiq.ui.transactions.AnalysisScreen
import com.example.pockiq.ui.transactions.TransactionsScreen
import com.example.pockiq.ui.wallet.WalletScreen

@Composable
fun MainNavigation(widgetAction: String? = null, widgetType: String? = null) {
    val context = LocalContext.current
    val repo    = remember { WalletRepository.getInstance(context) }
    val backStack = rememberNavBackStack(
        *if (widgetAction == "add_transaction") {
            arrayOf(Dashboard, AddTransaction(prefilledType = widgetType))
        } else {
            arrayOf(Dashboard)
        }
    )

    val currentDest = backStack.lastOrNull()
    val showBottomBar = currentDest is Dashboard ||
            currentDest is Wallet ||
            currentDest is Transactions ||
            currentDest is OthersMoney

    // Helper: navigate to a root tab (clear stack to that tab)
    fun navigateToTab(dest: Any) {
        // Pop everything off then add the new root
        while (backStack.size > 1) backStack.removeLastOrNull()
        if (backStack.lastOrNull() != dest) {
            backStack.removeLastOrNull()
            backStack.add(dest as androidx.navigation3.runtime.NavKey)
        }
    }

    Scaffold(
        bottomBar = {
            if (showBottomBar) {
                NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
                    NavigationBarItem(
                        selected = currentDest is Dashboard,
                        onClick  = { if (currentDest !is Dashboard) { while (backStack.size > 1) backStack.removeLastOrNull(); if (backStack.lastOrNull() !is Dashboard) { backStack.removeLastOrNull(); backStack.add(Dashboard) } } },
                        icon     = { Icon(Icons.Filled.Home, "Dashboard") },
                        label    = { Text("Home") }
                    )
                    NavigationBarItem(
                        selected = currentDest is Wallet,
                        onClick  = { if (currentDest !is Wallet) { while (backStack.size > 1) backStack.removeLastOrNull(); backStack.removeLastOrNull(); backStack.add(Wallet) } },
                        icon     = { Icon(Icons.Filled.AccountBalanceWallet, "Wallet") },
                        label    = { Text("Wallet") }
                    )
                    NavigationBarItem(
                        selected = currentDest is Transactions,
                        onClick  = { if (currentDest !is Transactions) { while (backStack.size > 1) backStack.removeLastOrNull(); backStack.removeLastOrNull(); backStack.add(Transactions) } },
                        icon     = { Icon(Icons.Filled.Receipt, "Transactions") },
                        label    = { Text("Txns") }
                    )
                    NavigationBarItem(
                        selected = currentDest is OthersMoney,
                        onClick  = { if (currentDest !is OthersMoney) { while (backStack.size > 1) backStack.removeLastOrNull(); backStack.removeLastOrNull(); backStack.add(OthersMoney()) } },
                        icon     = { Icon(Icons.Filled.People, "Others") },
                        label    = { Text("Others") }
                    )
                }
            }
        }
    ) { innerPadding ->
        val bottomPadding = innerPadding.calculateBottomPadding()

        NavDisplay(
            backStack     = backStack,
            onBack        = { backStack.removeLastOrNull() },
            modifier      = Modifier.fillMaxSize(),
            entryProvider = entryProvider {
                entry<Dashboard> {
                    DashboardScreen(
                        repo              = repo,
                        bottomPadding     = bottomPadding,
                        onAddTransaction  = { backStack.add(AddTransaction()) },
                        onGoToWallet      = { while (backStack.size > 1) backStack.removeLastOrNull(); if (backStack.lastOrNull() !is Wallet) { backStack.removeLastOrNull(); backStack.add(Wallet) } },
                        onGoToOthers      = { while (backStack.size > 1) backStack.removeLastOrNull(); if (backStack.lastOrNull() !is OthersMoney) { backStack.removeLastOrNull(); backStack.add(OthersMoney()) } },
                        onEditTransaction = { id -> backStack.add(AddTransaction(id)) },
                        onNavigateToAddOther = { amount, note, direction, draftId ->
                            backStack.add(AddOtherMoney(
                                prefilledAmount = amount,
                                prefilledNote = note,
                                prefilledDirection = direction,
                                draftIdToDelete = draftId
                            ))
                        },
                        onNavigateToOthersWithSplit = { amount, note, direction, draftId ->
                            backStack.add(OthersMoney(
                                splitAmount = amount,
                                splitNote = note,
                                splitDirection = direction,
                                splitDraftId = draftId
                            ))
                        },
                        onNavigateToAnalysis = { type ->
                            backStack.add(Analysis(type))
                        }
                    )
                }
                entry<Wallet> {
                    WalletScreen(repo = repo, bottomPadding = bottomPadding)
                }
                entry<Transactions> {
                    TransactionsScreen(
                        repo          = repo,
                        bottomPadding = bottomPadding,
                        onAdd         = { backStack.add(AddTransaction()) },
                        onEdit        = { id -> backStack.add(AddTransaction(id)) },
                        onNavigateToAnalysis = { type -> backStack.add(Analysis(type)) }
                    )
                }
                entry<AddTransaction> { key ->
                    AddTransactionScreen(
                        repo              = repo,
                        editTransactionId = key.transactionId,
                        prefilledType     = key.prefilledType,
                        onDone            = { backStack.removeLastOrNull() }
                    )
                }
                entry<OthersMoney> { key ->
                    OthersMoneyScreen(
                        repo          = repo,
                        bottomPadding = bottomPadding,
                        splitAmount   = key.splitAmount,
                        splitNote     = key.splitNote,
                        splitDirection = key.splitDirection,
                        splitDraftId  = key.splitDraftId,
                        onAddEntry    = { name, amount, note, dir, draftId ->
                            backStack.add(AddOtherMoney(
                                personName = name,
                                prefilledAmount = amount,
                                prefilledNote = note,
                                prefilledDirection = dir,
                                draftIdToDelete = draftId
                            ))
                        },
                        onPersonClick = { name, amount, note, dir, draftId ->
                            if (amount != null) {
                                backStack.add(AddOtherMoney(
                                    personName = name,
                                    prefilledAmount = amount,
                                    prefilledNote = note,
                                    prefilledDirection = dir,
                                    draftIdToDelete = draftId
                                ))
                            } else {
                                backStack.add(PersonLedger(name))
                            }
                        }
                    )
                }
                entry<AddOtherMoney> { key ->
                    AddOtherMoneyScreen(
                        repo               = repo,
                        prefilledName      = key.personName,
                        prefilledAmount    = key.prefilledAmount,
                        prefilledNote      = key.prefilledNote,
                        prefilledDirection = key.prefilledDirection,
                        draftIdToDelete    = key.draftIdToDelete,
                        onDone             = {
                            backStack.removeLastOrNull() // always pop AddOtherMoney
                            // If this was a split flow, clean up the split-mode OthersMoney
                            // entry below so the split banner doesn't linger
                            if (key.draftIdToDelete != null) {
                                val prev = backStack.lastOrNull() as? OthersMoney
                                if (prev != null && prev.splitAmount != null) {
                                    backStack.removeLastOrNull()
                                    backStack.add(OthersMoney()) // clean, no split params
                                }
                            }
                        }
                    )
                }
                entry<PersonLedger> { key ->
                    PersonLedgerScreen(
                        repo          = repo,
                        personName    = key.personName,
                        bottomPadding = bottomPadding,
                        onBack        = { backStack.removeLastOrNull() }
                    )
                }
                entry<Analysis> { key ->
                    AnalysisScreen(
                        repo   = repo,
                        type   = key.type,
                        onBack = { backStack.removeLastOrNull() }
                    )
                }
            }
        )
    }
}
