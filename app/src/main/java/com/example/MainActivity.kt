package com.example

import android.os.Bundle
import androidx.fragment.app.FragmentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.zIndex
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.compose.composable
import androidx.navigation.toRoute
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.ui.theme.MyApplicationTheme
import com.example.ui.theme.appColors
import com.example.ui.viewmodel.LedgerViewModel
import com.example.ui.viewmodel.LedgerViewModelFactory
import com.example.ui.screens.HomeDashboard
import com.example.ui.screens.TransactionsScreen
import com.example.ui.screens.SettingsScreen
import com.example.ui.screens.GeneralSettingsScreen
import com.example.ui.screens.AccountListScreen
import com.example.ui.screens.AccountDetailScreen
import com.example.ui.viewmodel.SettingsViewModel
import com.example.ui.viewmodel.SettingsViewModelFactory
import com.example.ai.AiTransactionViewModel
import com.example.ai.AiTransactionViewModelFactory
import com.example.ai.extract.TransactionExtractor
import com.example.ai.extract.GeminiTransactionExtractor
import com.example.ai.intent.IntentClassifier
import com.example.ai.intent.RuleBasedIntentClassifier
import com.example.ai.stt.SttProvider
import com.example.ai.stt.AndroidNativeSttProvider

import android.view.WindowManager
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.ViewModel
import com.example.ui.screens.ChangePINScreen
import com.example.ui.security.SecuritySettingsScreen
import com.example.ui.screens.SetPINScreen
import com.example.ui.screens.CurrencyPickerScreen
import com.example.ui.screens.BackupRestoreScreen
import com.example.ui.screens.RemindersScreen
import com.example.ui.screens.RecurringRemindersListScreen
import com.example.ui.security.SecurityViewModel
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.first

sealed class Route(val route: String, val title: String, val icon: ImageVector) {
    object Home : Route("home", "Dashboard", Icons.Default.Home)
    object Transactions : Route("transactions", "Transactions", Icons.AutoMirrored.Filled.List)
    object Settings : Route("settings", "Settings", Icons.Default.Settings)
    object GeneralSettings : Route("general_settings", "General", Icons.Default.Settings)
    object SecuritySettings : Route("security_settings", "Security", Icons.Default.Settings)
    object SetPIN : Route("set_pin", "Set PIN", Icons.Default.Settings)
    object ChangePIN : Route("change_pin", "Change PIN", Icons.Default.Settings)
    object CurrencyPicker : Route("currency_picker", "Change Currency", Icons.Default.Settings)
    object BackupRestore : Route("backup_restore", "Backup & Restore", Icons.Default.Settings)
    object Reminders : Route("reminders", "Reminders", Icons.Default.Settings)
    object RecurringRemindersList : Route("recurring_reminders_list", "Recurring Reminders", Icons.Default.Settings)
    object AccountList : Route("account_list", "Accounts", Icons.Default.AccountBalance)
    object AccountDetail : Route("account_detail/{accountId}", "Account Details", Icons.Default.Settings) {
        fun createRoute(accountId: String) = "account_detail/$accountId"
    }
}

val bottomNavItems = listOf(Route.Home, Route.Transactions, Route.Settings)

class MainActivity : FragmentActivity() {
    private lateinit var securityViewModel: SecurityViewModel

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        
        val app = application as LedgerApplication
        
        val securityFactory = object : ViewModelProvider.Factory {
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return SecurityViewModel(app.securityRepository) as T
            }
        }
        securityViewModel = ViewModelProvider(this, securityFactory).get(SecurityViewModel::class.java)

        lifecycleScope.launch {
            repeatOnLifecycle(androidx.lifecycle.Lifecycle.State.STARTED) {
                kotlinx.coroutines.flow.combine(
                    securityViewModel.hidePreviewInSwitcher,
                    securityViewModel.screenshotProtection
                ) { hidePreview, screenshot ->
                    hidePreview || screenshot
                }.collect { secure ->
                    if (secure) {
                        window.setFlags(WindowManager.LayoutParams.FLAG_SECURE,
                                        WindowManager.LayoutParams.FLAG_SECURE)
                    } else {
                        window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
                    }
                }
            }
        }

        val initialThemeMode = kotlinx.coroutines.runBlocking(kotlinx.coroutines.Dispatchers.IO) {
            try {
                app.userPreferences.theme.first()
            } catch (e: Exception) {
                "System"
            }
        }
        
        setContent {
            val ledgerFactory = LedgerViewModelFactory(app.repository, app.accountRepository, app.analyticsService)
            val viewModel: LedgerViewModel = viewModel(factory = ledgerFactory)
            
            val settingsFactory = SettingsViewModelFactory(application, app.userPreferences, app.repository)
            val settingsViewModel: SettingsViewModel = viewModel(factory = settingsFactory)

            val intentClassifier: IntentClassifier = RuleBasedIntentClassifier()
            val transactionExtractor: TransactionExtractor = GeminiTransactionExtractor()
            val aiFactory = AiTransactionViewModelFactory(app.repository, app.accountRepository, intentClassifier, transactionExtractor, app.analyticsRepository)
            val aiViewModel: AiTransactionViewModel = viewModel(factory = aiFactory)

            val sttProvider: SttProvider = AndroidNativeSttProvider(this)

            val isLocked by com.example.data.security.AppLockManager.isLocked.collectAsStateWithLifecycle()
            val appLockEnabled by securityViewModel.appLockEnabled.collectAsStateWithLifecycle()

            val themeModeState by settingsViewModel.theme.collectAsStateWithLifecycle()
            var themeMode by remember { mutableStateOf(initialThemeMode) }

            LaunchedEffect(themeModeState) {
                themeMode = themeModeState
            }

            val darkTheme = when (themeMode) {
                "Light" -> false
                "Dark" -> true
                else -> isSystemInDarkTheme()
            }

            MyApplicationTheme(darkTheme = darkTheme) {
                Box(modifier = Modifier.fillMaxSize()) {
                    MainScreen(
                        viewModel = viewModel, 
                        settingsViewModel = settingsViewModel,
                        securityViewModel = securityViewModel,
                        aiViewModel = aiViewModel,
                        sttProvider = sttProvider,
                        transactionRepository = app.repository
                    )
                    
                    if (appLockEnabled && isLocked) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .zIndex(Float.MAX_VALUE)
                        ) {
                            com.example.ui.security.LockScreen(
                                viewModel = securityViewModel,
                                activity = this@MainActivity,
                                onUnlock = { com.example.data.security.AppLockManager.unlock() }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun MainScreen(
    viewModel: LedgerViewModel, 
    settingsViewModel: SettingsViewModel,
    securityViewModel: SecurityViewModel,
    aiViewModel: AiTransactionViewModel,
    sttProvider: SttProvider,
    transactionRepository: com.example.data.TransactionRepository
) {
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination
    val showBottomBar = currentDestination?.route in bottomNavItems.map { it.route }

    Scaffold(
        bottomBar = {
            if (showBottomBar) {
                NavigationBar(
                    containerColor = MaterialTheme.appColors.tabBackground,
                    contentColor = MaterialTheme.appColors.tabInactive
                ) {
                    bottomNavItems.forEach { screen ->
                        val isSelected = currentDestination?.hierarchy?.any { it.route == screen.route } == true
                        NavigationBarItem(
                            icon = { Icon(screen.icon, contentDescription = screen.title) },
                            label = { Text(screen.title, fontSize = 10.sp, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal) },
                            selected = isSelected,
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = MaterialTheme.appColors.tabActive,
                                selectedTextColor = MaterialTheme.appColors.tabActive,
                                indicatorColor = MaterialTheme.appColors.tabActiveBg,
                                unselectedIconColor = MaterialTheme.appColors.tabInactive,
                                unselectedTextColor = MaterialTheme.appColors.tabInactive
                            ),
                            onClick = {
                                navController.navigate(screen.route) {
                                    popUpTo(navController.graph.findStartDestination().id) {
                                        saveState = true
                                    }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            }
                        )
                    }
                }
            }
        }
    ) { innerPadding ->
        Box(modifier = Modifier.padding(innerPadding)) {
            NavHost(
                navController = navController,
                startDestination = Route.Home.route,
                modifier = Modifier.fillMaxSize()
            ) {
                composable(Route.Home.route) {
                    HomeDashboard(
                        viewModel = viewModel,
                        settingsViewModel = settingsViewModel,
                        aiViewModel = aiViewModel,
                        sttProvider = sttProvider,
                        navController = navController,
                        onNavigateToTransactions = { 
                            navController.navigate(Route.Transactions.route) {
                                popUpTo(navController.graph.findStartDestination().id) {
                                    saveState = true
                                }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                        onNavigateToAccounts = {
                            navController.navigate(Route.AccountList.route)
                        }
                    )
                }
                composable<com.example.ui.navigation.FilteredLedger> { backStackEntry ->
                    val route = backStackEntry.toRoute<com.example.ui.navigation.FilteredLedger>()
                    com.example.ui.screens.FilteredLedgerScreen(filter = route, transactionRepository = transactionRepository)
                }
                composable(Route.Transactions.route) {
                    TransactionsScreen(
                        viewModel = viewModel,
                        settingsViewModel = settingsViewModel
                    )
                }
                composable(Route.Settings.route) {
                    SettingsScreen(
                        viewModel = settingsViewModel,
                        onNavigateToGeneral = { navController.navigate(Route.GeneralSettings.route) },
                        onNavigateToSecurity = { navController.navigate(Route.SecuritySettings.route) },
                        onNavigateToCurrency = { navController.navigate(Route.CurrencyPicker.route) },
                        onNavigateToBackup = { navController.navigate(Route.BackupRestore.route) },
                        onNavigateToReminders = { navController.navigate(Route.Reminders.route) },
                        onNavigateToAccounts = { navController.navigate(Route.AccountList.route) }
                    )
                }
                composable(Route.AccountList.route) {
                    AccountListScreen(
                        viewModel = viewModel,
                        settingsViewModel = settingsViewModel,
                        onBack = { navController.popBackStack() },
                        onNavigateToDetail = { accountId ->
                            navController.navigate(Route.AccountDetail.createRoute(accountId))
                        }
                    )
                }
                composable(
                    route = Route.AccountDetail.route,
                    arguments = listOf(androidx.navigation.navArgument("accountId") { type = androidx.navigation.NavType.StringType })
                ) { backStackEntry ->
                    val accountId = backStackEntry.arguments?.getString("accountId") ?: ""
                    AccountDetailScreen(
                        accountId = accountId,
                        viewModel = viewModel,
                        settingsViewModel = settingsViewModel,
                        onBack = { navController.popBackStack() }
                    )
                }
                composable(Route.GeneralSettings.route) {
                    GeneralSettingsScreen(
                        viewModel = settingsViewModel, 
                        onBack = { navController.popBackStack() },
                        onNavigateToCurrency = { navController.navigate(Route.CurrencyPicker.route) }
                    )
                }
                composable(Route.SecuritySettings.route) {
                    val activity = LocalContext.current as FragmentActivity
                    SecuritySettingsScreen(
                        viewModel = securityViewModel,
                        activity = activity,
                        onBack = { navController.popBackStack() },
                        onNavigateToSetPin = { navController.navigate(Route.SetPIN.route) },
                        onNavigateToChangePin = { navController.navigate(Route.ChangePIN.route) }
                    )
                }
                composable(Route.SetPIN.route) {
                    SetPINScreen(
                        viewModel = securityViewModel,
                        onBack = { navController.popBackStack() },
                        onSuccess = { navController.popBackStack() }
                    )
                }
                composable(Route.ChangePIN.route) {
                    ChangePINScreen(
                        viewModel = securityViewModel,
                        onBack = { navController.popBackStack() },
                        onSuccess = { navController.popBackStack() }
                    )
                }
                composable(Route.CurrencyPicker.route) {
                    CurrencyPickerScreen(
                        viewModel = settingsViewModel,
                        onBack = { navController.popBackStack() }
                    )
                }
                composable(Route.BackupRestore.route) {
                    BackupRestoreScreen(
                        onBack = { navController.popBackStack() }
                    )
                }
                composable(Route.Reminders.route) {
                    RemindersScreen(
                        onBack = { navController.popBackStack() },
                        onNavigateToRecurring = { navController.navigate(Route.RecurringRemindersList.route) }
                    )
                }
                composable(Route.RecurringRemindersList.route) {
                    RecurringRemindersListScreen(
                        onBack = { navController.popBackStack() }
                    )
                }
            }
        }
    }
}
