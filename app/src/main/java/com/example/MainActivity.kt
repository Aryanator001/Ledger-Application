package com.example

import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.fragment.app.FragmentActivity
import androidx.activity.compose.setContent
import androidx.activity.SystemBarStyle
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
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.activity.compose.LocalActivity
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
import com.example.ui.screens.AnalyticsScreen
import com.example.ui.screens.GeneralSettingsScreen
import com.example.ui.screens.AccountListScreen
import com.example.ui.screens.AccountDetailScreen
import com.example.ui.analytics.AnalyticsViewModel
import com.example.ui.analytics.AnalyticsViewModelFactory
import com.example.ui.analytics.GeminiInsightsService
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
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
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
    object Analytics : Route("analytics", "Analytics", Icons.Default.BarChart)
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
    object Login : Route("login", "Login", Icons.Default.Settings)
    object ProfileSetup : Route("profile_setup", "Profile Setup", Icons.Default.Settings)
}

val bottomNavItems = listOf(Route.Home, Route.Transactions, Route.Analytics, Route.Settings)

class MainActivity : FragmentActivity() {
    private lateinit var securityViewModel: SecurityViewModel
    private var isThemeLoaded = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val splashScreen = installSplashScreen()
        splashScreen.setKeepOnScreenCondition { !isThemeLoaded }

        val app = application as LedgerApplication

        lifecycleScope.launch {
            val hasOnboarded = app.userPreferences.hasCompletedOnboarding.first()
            if (!hasOnboarded) {
                if (ContextCompat.checkSelfPermission(this@MainActivity, Manifest.permission.RECORD_AUDIO)
                    != PackageManager.PERMISSION_GRANTED) {
                    // FragmentActivity only permits request codes in the lower 16 bits, so we
                    // request directly with a fixed code instead of the ActivityResult registry
                    // (which generates 32-bit codes and crashes here).
                    ActivityCompat.requestPermissions(
                        this@MainActivity,
                        arrayOf(Manifest.permission.RECORD_AUDIO),
                        REQUEST_CODE_RECORD_AUDIO
                    )
                }
            }
        }
        
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
                val mode = app.userPreferences.theme.first()
                isThemeLoaded = true
                mode
            } catch (e: Exception) {
                isThemeLoaded = true
                "System"
            }
        }

        // Use explicit SystemBarStyle (not the default auto) so the OnPreDrawListener
        // auto-detector is never installed — otherwise it continuously overrides the
        // isAppearanceLightStatusBars flag set in MyApplicationTheme's SideEffect.
        val isSystemDark = (resources.configuration.uiMode and
            android.content.res.Configuration.UI_MODE_NIGHT_MASK) ==
            android.content.res.Configuration.UI_MODE_NIGHT_YES
        val initialDarkTheme = when (initialThemeMode) {
            "Light" -> false
            "Dark" -> true
            else -> isSystemDark
        }
        enableEdgeToEdge(
            statusBarStyle = if (initialDarkTheme) {
                SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
            } else {
                SystemBarStyle.light(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT)
            },
            navigationBarStyle = if (initialDarkTheme) {
                SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
            } else {
                SystemBarStyle.light(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT)
            }
        )

        setContent {
            val ledgerFactory = LedgerViewModelFactory(app.repository, app.accountRepository, app.analyticsService)
            val viewModel: LedgerViewModel = viewModel(factory = ledgerFactory)
            
            val settingsFactory = SettingsViewModelFactory(application, app.userPreferences, app.repository)
            val settingsViewModel: SettingsViewModel = viewModel(factory = settingsFactory)

            val intentClassifier: IntentClassifier = RuleBasedIntentClassifier()
            val transactionExtractor: TransactionExtractor = GeminiTransactionExtractor()
            val aiFactory = AiTransactionViewModelFactory(app.repository, app.accountRepository, intentClassifier, transactionExtractor, app.analyticsRepository)
            val aiViewModel: AiTransactionViewModel = viewModel(factory = aiFactory)

            val analyticsFactory = AnalyticsViewModelFactory(app.repository, app.analyticsService, GeminiInsightsService())
            val analyticsViewModel: AnalyticsViewModel = viewModel(factory = analyticsFactory)
            
            val authFactory = com.example.ui.viewmodel.AuthViewModelFactory(app.authRepository, app.securityStore)
            val authViewModel: com.example.ui.viewmodel.AuthViewModel = viewModel(factory = authFactory)

            val sttProvider: SttProvider = AndroidNativeSttProvider(this)

            val isLocked by com.example.data.security.AppLockManager.isLocked.collectAsStateWithLifecycle()
            val appLockEnabled by securityViewModel.appLockEnabled.collectAsStateWithLifecycle()

            val themeModeState by settingsViewModel.theme.collectAsStateWithLifecycle()
            var themeMode by remember { mutableStateOf(initialThemeMode) }

            val activeSession by authViewModel.activeSession.collectAsStateWithLifecycle()

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
                        analyticsViewModel = analyticsViewModel,
                        authViewModel = authViewModel,
                        sttProvider = sttProvider,
                        transactionRepository = app.repository,
                        app = app,
                        startDestination = if (activeSession == null) Route.Login.route else Route.Home.route
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

    companion object {
        private const val REQUEST_CODE_RECORD_AUDIO = 1001
    }
}

@Composable
fun MainScreen(
    viewModel: LedgerViewModel,
    settingsViewModel: SettingsViewModel,
    securityViewModel: SecurityViewModel,
    aiViewModel: AiTransactionViewModel,
    analyticsViewModel: com.example.ui.analytics.AnalyticsViewModel,
    authViewModel: com.example.ui.viewmodel.AuthViewModel,
    sttProvider: SttProvider,
    transactionRepository: com.example.data.TransactionRepository,
    app: LedgerApplication,
    startDestination: String
) {
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination
    val showBottomBar = currentDestination?.route in bottomNavItems.map { it.route }

    // Lock startDestination at first composition — NavHost uses it as a remember key,
    // so letting it change when activeSession goes non-null would recreate the graph
    // and navigate straight to Home, bypassing ProfileSetup.
    val rememberedStartDestination = remember { startDestination }

    val hasCompletedOnboarding by app.userPreferences.hasCompletedOnboarding
        .collectAsStateWithLifecycle(initialValue = startDestination != Route.Login.route)

    // If session becomes null, route to login
    val activeSession by authViewModel.activeSession.collectAsStateWithLifecycle()
    LaunchedEffect(activeSession) {
        if (activeSession == null && currentDestination?.route != Route.Login.route) {
            navController.navigate(Route.Login.route) {
                popUpTo(0) { inclusive = true }
            }
        }
    }

    Scaffold(
        containerColor = MaterialTheme.appColors.bgBase,
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
                startDestination = rememberedStartDestination,
                modifier = Modifier.fillMaxSize()
            ) {
                composable(Route.Login.route) {
                    com.example.ui.screens.LoginScreen(
                        authViewModel = authViewModel,
                        onLoginSuccess = {
                            val dest = if (hasCompletedOnboarding) Route.Home.route else Route.ProfileSetup.route
                            navController.navigate(dest) {
                                popUpTo(Route.Login.route) { inclusive = true }
                            }
                        }
                    )
                }
                composable(Route.ProfileSetup.route) {
                    com.example.ui.screens.ProfileSetupScreen(
                        authViewModel = authViewModel,
                        databaseStitcher = com.example.data.DatabaseStitcher(app.database),
                        userProfileDao = app.database.userProfileDao(),
                        ledgerScopeDao = app.database.ledgerScopeDao(),
                        userPreferences = app.userPreferences,
                        onSetupComplete = {
                            navController.navigate(Route.Home.route) {
                                popUpTo(Route.ProfileSetup.route) { inclusive = true }
                            }
                        }
                    )
                }
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
                composable(Route.Analytics.route) {
                    AnalyticsScreen(
                        viewModel = analyticsViewModel,
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
                    val activity = LocalActivity.current as FragmentActivity
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
