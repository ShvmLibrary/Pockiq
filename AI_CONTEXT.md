# AI Project Context & Architecture Guidelines – Pockiq

> **Refer to this AI Project Context Document before making any changes to the codebase.**

---

## 1. Project Overview

**Pockiq** is a smart, privacy-centric personal finance tracker for Android designed to automate expense tracking. It solves the tedious problem of manual input by automatically importing UPI and bank transaction details from system notifications and SMS messages.

### Main Capabilities
* **Auto-Import Engine**: Captures UPI transactions in real-time from SMS messages and payment app notifications (GPay, PhonePe, Paytm, CRED, etc.).
* **Pending Review Drafts**: Retains imported transactions as unapproved drafts, letting the user verify, modify, categorize, ignore, or split them before finalizing.
* **Split & Ledger ("Others' Money")**: Track money lent or borrowed per person using an isolated ledger system.
* **Wallet Balance Tracking**: Distinctly records Bank and Cash balances with manual adjustment triggers.
* **Monthly Budgets**: Displays current month progress bars, calculates over-spending limits, and supports in-app budget edits.
* **Smart Category Ordering**: Prioritizes frequently used categories and supports custom category creation.
* **Home Screen Widget**: A fast widget provider supporting quick-adds directly from the home screen.

---

## 2. Tech Stack

* **Language**: Kotlin 1.9+ (JVM Target 17)
* **UI Framework**: Jetpack Compose (Material 3 components)
* **Architecture**: Offline-First MVVM + Repository Pattern
* **Database**: Room (SQLite) with custom `Date` and `Enum` TypeConverters
* **Concurrency**: Kotlin Coroutines + Flow (asynchronous data streams)
* **Navigation**: AndroidX Navigation 3
* **Minimum SDK**: Android 7.0 (API 24)
* **Target / Compile SDK**: Android 16 (API 36)

---

## 3. Folder & Architecture Structure

The project resides inside `/app/src/main/java/com/example/pockiq` and is organized as follows:

```
Pockiq/
├── data/
│   ├── db/
│   │   ├── AppDatabase.kt     # Room database builder & configuration
│   │   ├── Converters.kt      # Room Date & Enum TypeConverters
│   │   ├── Daos.kt            # DAOs: TransactionDao, WalletDao, OtherMoneyDao, CategoryDao
│   │   └── Entities.kt        # Entities: TransactionEntity, WalletEntity, OtherMoneyEntity, CategoryEntity
│   ├── DataRepository.kt      # Repository marker interface (unused placeholder)
│   └── WalletRepository.kt    # PRODUCTION CRITICAL singleton repository layer
├── theme/
│   ├── Color.kt               # WalletColors HSL palette & Compose colors
│   ├── Theme.kt               # Light/Dark dynamic color palettes
│   └── Type.kt                # Typography definitions
├── ui/
│   ├── dashboard/
│   │   └── DashboardScreen.kt # Home dashboard, net worth overview, and draft review sheet
│   ├── main/
│   │   ├── MainScreen.kt      # Main Container screen
│   │   └── MainScreenViewModel.kt
│   ├── others/
│   │   ├── AddOtherMoneyScreen.kt # Record lent/borrowed transactions
│   │   ├── OthersMoneyScreen.kt   # Others' money aggregate balances per person
│   │   └── PersonLedgerScreen.kt  # Individual ledger details with renaming actions
│   ├── transactions/
│   │   ├── AddTransactionScreen.kt # Add or edit transaction details
│   │   ├── AnalysisScreen.kt       # Charts and category breakdowns
│   │   └── TransactionsScreen.kt   # Searchable, categorizable transactional list
│   └── wallet/
│       └── WalletScreen.kt         # Bank/Cash manual adjustment screen
├── MainActivity.kt                 # Entry point, SMS permissions sync on launch
├── Navigation.kt                   # Compose Navigation graph
├── NavigationKeys.kt               # Safe routes definition
├── UpiNotificationListenerService.kt # Notification listener for real-time payment alerts
├── UpiSimulationReceiver.kt        # Simulates UPI events for testing
├── UpiSmsReceiver.kt              # BroadcastReceiver for live SMS messages
└── PockiqWidgetProvider.kt       # Home screen AppWidget provider
```

---

## 4. Offline Database & Core Logic

### Room Database Schema (`Entities.kt`)

* **`TransactionEntity`** (`tableName = "transactions"`):
  * `id`: `Long` (PrimaryKey, AutoGenerate)
  * `type`: `TransactionType` (`INCOME` | `EXPENSE`)
  * `amount`: `Double`
  * `category`: `String`
  * `note`: `String`
  * `date`: `Date`
  * `walletSource`: `WalletSource` (`BANK` | `CASH`)
  * `isDraft`: `Boolean` (Indicates transaction is pending manual review)

* **`WalletEntity`** (`tableName = "wallet"`):
  * `id`: `Int = 1` (PrimaryKey, Single Row)
  * `bankBalance`: `Double`
  * `cashBalance`: `Double`
  * `monthlyBudget`: `Double`
  * `lastUpdated`: `Date`

* **`OtherMoneyEntity`** (`tableName = "other_money"`):
  * `id`: `Long` (PrimaryKey, AutoGenerate)
  * `personName`: `String`
  * `direction`: `OtherMoneyDirection` (`RECEIVED` | `GIVEN`)
  * `amount`: `Double`
  * `date`: `Date`
  * `note`: `String`

* **`CategoryEntity`** (`tableName = "categories"`):
  * `id`: `Long` (PrimaryKey, AutoGenerate)
  * `name`: `String`
  * `type`: `TransactionType`

### Auto-Import Deduction & Deduplication Rules

Pockiq uses overlapping safety nets to ensure transactions are imported reliably without duplication:

1. **SMS Sync on Startup (`MainActivity.kt`)**:
   On launch, checks for missed bank alerts in the local SMS inbox spanning the **last 48 hours** (`Date().time - 48 * 60 * 60 * 1000`).
2. **Live SMS Broadcast (`UpiSmsReceiver.kt`)**:
   Listens to `android.provider.Telephony.SMS_RECEIVED` events.
3. **Notification Service (`UpiNotificationListenerService.kt`)**:
   Captures active system notifications from packages matching financial keywords (`pay`, `bank`, `wallet`, `money`, `cred`, `slice` or GPay/BHIM exact names).
4. **Duplicate Deduplication Windows**:
   * **120-second Window**: An incoming alert is rejected if an identical finalized transaction (same amount & type) exists within 2 minutes of the alert timestamp.
   * **60-second Cross-Source Draft Window**: Prevents duplicate drafts when a transaction triggers *both* an SMS alert and a payment notification. Rejects if a draft with the same amount & type exists within 60 seconds.
   * **60-second Others' Money Window**: Rejects rapid duplicate additions to the ledger to prevent double-taps.

---

## 5. Build, Signing & Deployment Process

The deployment pipeline remains highly sensitive and requires strict verification steps.

### Release Configuration Checklist
1. **Keystore Signing**: Build release configurations must configure `signingConfigs` inside `app/build.gradle.kts` using a secure release signing keystore.
2. **Application ID**: The publishing bundle identifier is registered as `io.pockiq.app`. Do not rename it.
3. **Privacy Policy**: Google Play requires an active privacy policy URL due to sensitive permissions (`RECEIVE_SMS`, `READ_SMS`, `NotificationListener`).
4. **Launcher Icon**: Default assets in `res/mipmap-*` should be replaced with custom, optimized vector art before a Google Play compilation.
5. **Shrinking & Optimization**:
   * ProGuard is enabled via `isMinifyEnabled = true` and `isShrinkResources = true`.
   * Keep rules for Room entities and custom types must be verified inside `proguard-rules.pro`.

---

## 6. Critical / Do-Not-Touch Modules

The following files represent core security, transaction lifecycle, and storage components. **Do not modify these modules without explicit review and manual test confirmation.**

* [HIGH RISK] [WalletRepository.kt](app/src/main/java/com/example/pockiq/data/WalletRepository.kt): Coordinates all database access, thread-safe transaction logic, pre-populates category schemas, and maintains single-instance safety.
* [PRODUCTION CRITICAL] [UpiSmsReceiver.kt](app/src/main/java/com/example/pockiq/UpiSmsReceiver.kt): Formulates core regex patterns and synchronizes live incoming bank alerts.
* [PRODUCTION CRITICAL] [UpiNotificationListenerService.kt](app/src/main/java/com/example/pockiq/UpiNotificationListenerService.kt): Handles whitelisted notification tracking. Breaking this will stop real-time notifications on production devices.
* [HIGH RISK] [AppDatabase.kt](app/src/main/java/com/example/pockiq/data/db/AppDatabase.kt): Orchestrates Room database creation and handles fallback-to-destructive migrations.

---

## 7. AI Context Rules

* **Always Reference Context**: Check `AI_CONTEXT.md` first before writing code or changing structures.
* **Strict Deduplication Compliance**: When implementing features that record transactions, lent assets, or cash logs, you **must** preserve duplicate prevention checks using standard timing windows (`first()` data checks).
* **Compose State Hoisting**: Ensure Jetpack Compose components follow state hosting patterns. Maintain viewmodel or database flows in screens (e.g., `collectAsState`). Do not instantiate database instances locally inside Compose layouts.
* **Keep Dispatchers Aligned**: Perform Room database insertions, deletes, and updates on `Dispatchers.IO` using a launch context. Never block the main main-thread dispatcher.
* **Handle Battery Whitelists**: Retain logic for prompting battery optimization whitelists; the Notification Service relies on background persistence to bypass aggressive OEM process-killers (Samsung, Xiaomi, etc.).

---

## 8. Knowledge Base & Regex Dictionary

### SMS & Notification Auto-Parsing Regex
* **Amount Regex**: `(?i)(?:Rs\.?|INR|₹)\s*([\d,]+\.?\d*)`
  * Matches: `Rs. 500`, `₹25,000`, `INR 1.50`, etc.
* **Income Detection Rules**:
  * Body must match one: `received`, `credited`, `deposited` (case-insensitive).
  * Anything else matches `EXPENSE`.
* **Transaction ID / UTR Parsing**:
  * Matches pattern 1: `(?i)(?:utr|ref|reference|txn\s*id|transaction\s*id)[:\s#]*([a-zA-Z0-9]{12,})`
  * Matches pattern 2: `\b\d{12}\b` (standard 12-digit UPI reference number).

### Pre-Populated DB Categories
* **Income**: `Salary`, `Freelance`, `Investment`, `Gift`, `Refund`, `Interest`, `Other Income`
* **Expense**: `Food`, `Transport`, `Rent`, `Utilities`, `Shopping`, `Healthcare`, `Entertainment`, `Education`, `Sports`, `Travel`, `Other Expense`

---

## 9. Continuous Context Synchronization

This documentation represents the **single source of truth** for developers, AI assistants, and automated pipelines. 

* **Mandatory Sync Rule**: Any updates to the database schema, SMS/Notification parser regexes, package additions, or core navigation logic **must** be immediately documented inside `AI_CONTEXT.md`.
* **Feature Completeness Definition**: *"No feature is complete until the AI Project Context Document is updated."*
