package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.R
import com.example.data.Transaction
import com.example.data.TransactionWithCategory
import com.example.ui.components.cards.SummaryCard
import com.example.ui.components.cards.TransactionCard
import androidx.compose.material3.MaterialTheme
import com.example.ui.theme.appColors
import com.example.ui.theme.AppShapes
import com.example.ui.theme.AppTypography
import com.example.ui.viewmodel.LedgerViewModel
import com.example.ui.viewmodel.SettingsViewModel
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import com.example.ai.AiTransactionViewModel
import com.example.ai.state.AiState
import com.example.ai.state.FailureReason
import com.example.ai.stt.SttProvider
import com.example.ai.stt.SttCallbacks
import com.example.ai.stt.rememberRecordAudioPermissionState
import com.example.ui.components.ai.AiTranscriptCard
import com.example.ai.extract.CandidateResolver
import com.example.ai.model.Direction
import com.example.ai.model.ConfidenceScore
import com.example.ai.model.ConfidenceTier
import com.example.ui.components.ai.AiConfirmationCard

@Composable
fun HomeDashboard(
    viewModel: LedgerViewModel,
    settingsViewModel: SettingsViewModel,
    aiViewModel: AiTransactionViewModel,
    sttProvider: SttProvider,
    navController: androidx.navigation.NavController,
    onNavigateToTransactions: () -> Unit,
    onNavigateToAccounts: () -> Unit
) {
    val netWorth by viewModel.netWorth.collectAsStateWithLifecycle()
    val monthlyIncome by viewModel.monthlyIncome.collectAsStateWithLifecycle()
    val monthlyExpense by viewModel.monthlyExpense.collectAsStateWithLifecycle()
    val allTransactionsWithCategory by viewModel.allTransactionsWithCategory.collectAsStateWithLifecycle()
    val currencySymbol by settingsViewModel.currencySymbol.collectAsStateWithLifecycle()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.appColors.bgBase)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
        ) {
            SummaryCard(
                totalBalance = netWorth / 100.0,
                totalIncome = monthlyIncome / 100.0,
                totalExpense = monthlyExpense / 100.0,
                currencySymbol = currencySymbol,
                modifier = Modifier.clickable { onNavigateToAccounts() }
            )

            Spacer(modifier = Modifier.height(24.dp))
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(R.string.dashboard_recent_transactions),
                    style = AppTypography.headline3,
                    color = MaterialTheme.appColors.textPrimary
                )
                TextButton(onClick = onNavigateToTransactions) {
                    Text(stringResource(R.string.dashboard_see_all), color = MaterialTheme.appColors.primary)
                }
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            if (allTransactionsWithCategory.isEmpty()) {
                Box(modifier = Modifier.weight(1f), contentAlignment = androidx.compose.ui.Alignment.Center) {
                    Text(stringResource(R.string.dashboard_no_transactions), color = MaterialTheme.appColors.textTertiary)
                }
            } else {
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    contentPadding = PaddingValues(bottom = 80.dp) // Space for floating pill bar
                ) {
                    items(allTransactionsWithCategory.take(5)) { twc ->
                        TransactionCard(
                            transaction = twc.transaction,
                            onClick = { /* TODO details */ },
                            currencySymbol = currencySymbol,
                            category = twc.category
                        )
                    }
                }
            }

        } // End of Column inside Box

        var prompt by remember { mutableStateOf("") }
        var showTransactionSheet by remember { mutableStateOf(false) }
        var parsedAmount by remember { mutableStateOf<Double?>(null) }
        val context = androidx.compose.ui.platform.LocalContext.current

        val aiState by aiViewModel.state.collectAsStateWithLifecycle()
        val categories by viewModel.allCategories.collectAsStateWithLifecycle()
        val accounts by viewModel.allAccounts.collectAsStateWithLifecycle()
        val resolver = remember { CandidateResolver() }
        val permissionState = rememberRecordAudioPermissionState(context)

        // Auto-commit for high confidence AI transactions
        LaunchedEffect(aiState) {
            if (aiState is AiState.AutoCommitting) {
                val candidate = (aiState as AiState.AutoCommitting).candidate
                val categoryId = resolver.resolveCategoryId(candidate.categoryHint)
                val accountId = resolver.resolveAccountId(candidate.paymentMethodHint)
                val type = when (candidate.direction) {
                    Direction.INCOME -> "INCOME"
                    Direction.EXPENSE, Direction.TRANSFER -> "EXPENSE"
                    null -> "EXPENSE"
                }
                viewModel.addTransaction(
                    title = candidate.entity ?: context.getString(R.string.dashboard_voice_transaction),
                    amount = candidate.amount ?: 0.0,
                    type = type,
                    description = candidate.notes,
                    rawTranscript = candidate.rawTranscript,
                    categoryId = categoryId,
                    accountId = accountId
                )
                aiViewModel.markCommitted("saved", candidate)
                android.widget.Toast.makeText(context, context.getString(R.string.dashboard_auto_saved, candidate.entity ?: "", candidate.amount?.toLong() ?: 0L), android.widget.Toast.LENGTH_SHORT).show()
            }
        }

        // Release the recognizer when the dashboard leaves composition.
        DisposableEffect(Unit) {
            onDispose { sttProvider.release() }
        }

        val sttCallbacks = remember {
            object : SttCallbacks {
                override fun onListeningStarted() { /* no-op: state already Listening */ }
                override fun onPartial(text: String) { aiViewModel.onPartialTranscript(text) }
                override fun onFinal(text: String) { aiViewModel.onTranscriptReady(text) }
                override fun onError(reason: FailureReason) { aiViewModel.onSttError(reason) }
            }
        }

        fun startListening() {
            if (!permissionState.isGranted()) {
                permissionState.request { granted ->
                    if (granted) {
                        aiViewModel.onMicPressed()
                        sttProvider.start(sttCallbacks)
                    } else {
                        aiViewModel.onSttError(FailureReason.SttPermissionDenied)
                        android.widget.Toast.makeText(context, context.getString(R.string.dashboard_mic_permission), android.widget.Toast.LENGTH_LONG).show()
                    }
                }
            } else {
                aiViewModel.onMicPressed()
                sttProvider.start(sttCallbacks)
            }
        }

        fun stopListening() {
            sttProvider.stop()
        }

        fun dismissAi() {
            sttProvider.cancel()
            aiViewModel.reset()
        }

        fun commitTransaction(candidate: com.example.ai.model.CandidateTransaction) {
            val amount = candidate.amount ?: 0.0
            val direction = candidate.direction ?: Direction.EXPENSE
            val entity = candidate.entity ?: ""
            val categoryId = resolver.resolveCategoryId(candidate.categoryHint)
            val accountId = resolver.resolveAccountId(candidate.paymentMethodHint)
            val notes = candidate.notes
            val rawTranscript = candidate.rawTranscript

            val type = when (direction) {
                Direction.INCOME -> "INCOME"
                Direction.EXPENSE -> "EXPENSE"
                Direction.TRANSFER -> "EXPENSE"
            }
            val toAccountId = if (direction == Direction.TRANSFER) "default_cash" else null

            viewModel.addTransaction(
                title = entity,
                amount = amount,
                type = type,
                description = notes,
                rawTranscript = rawTranscript,
                categoryId = categoryId,
                accountId = accountId,
                toAccountId = toAccountId
            )

            // Get candidate from current AI state for the committed notification.
            val currentState = aiState
            if (currentState is AiState.ConfirmationRequired) {
                aiViewModel.markCommitted("saved", currentState.candidate)
            } else if (currentState is AiState.AutoCommitting) {
                aiViewModel.markCommitted("saved", currentState.candidate)
            }

            android.widget.Toast.makeText(context, context.getString(R.string.dashboard_transaction_saved), android.widget.Toast.LENGTH_SHORT).show()
        }

        // Floating Chat GPT pill bar replaced with manual parsing
        Column(
            modifier = Modifier
                .align(androidx.compose.ui.Alignment.BottomCenter)
                .padding(bottom = 16.dp)
        ) {
            when (val state = aiState) {
                is AiState.ConfirmationRequired -> {
                    AiConfirmationCard(
                        candidate = state.candidate,
                        categories = categories,
                        accounts = accounts,
                        resolver = resolver,
                        errors = state.errors,
                        onConfirm = { commitTransaction(state.candidate) },
                        onOverrideDuplicate = { aiViewModel.overrideDuplicateAndSave() },
                        onEdit = { updatedCandidate -> aiViewModel.updateCandidateAndRevalidate(updatedCandidate) },
                        onDismiss = { dismissAi() }
                    )
                }
                is AiState.QueryAnswered -> {
                    com.example.ui.components.ai.QueryAnsweredCard(
                        response = state.response,
                        onDismiss = { dismissAi() },
                        onViewTransactions = {
                            navController.navigate(
                                com.example.ui.navigation.FilteredLedger(
                                    entity = state.response.query.entity,
                                    direction = state.response.query.direction?.name,
                                    categoryId = state.response.query.categoryHint,
                                    timeRangeStart = state.response.query.timeRangeStart,
                                    timeRangeEnd = state.response.query.timeRangeEnd
                                )
                            )
                            dismissAi()
                        }
                    )
                }
                is AiState.QueryProcessing -> {
                    AiTranscriptCard(
                        state = AiState.Extracting(state.transcript, com.example.ai.model.AiIntent.Query),
                        onDismiss = { dismissAi() }
                    )
                }
                is AiState.Failed -> {
                    when (state.reason) {
                        is com.example.ai.state.FailureReason.NoDataYet -> {
                            com.example.ui.components.ai.QueryEmptyStateCard(
                                onDismiss = { dismissAi() }
                            )
                        }
                        else -> {
                            AiTranscriptCard(
                                state = state,
                                onDismiss = { dismissAi() }
                            )
                        }
                    }
                }
                else -> {
                    // Show transcript card for all other active AI states.
                    AiTranscriptCard(
                        state = aiState,
                        onDismiss = { dismissAi() }
                    )
                }
            }

            com.example.ui.components.inputs.ChatInputBar(
                value = prompt,
                onValueChange = { prompt = it },
                onSendClick = {
                    if (prompt.isNotBlank()) {
                        val amount = viewModel.parseAmountQuietly(prompt)
                        if (amount != null) {
                            parsedAmount = amount
                            showTransactionSheet = true
                        } else {
                            android.widget.Toast.makeText(context, context.getString(R.string.dashboard_include_amount), android.widget.Toast.LENGTH_SHORT).show()
                        }
                    }
                },
                onMicClick = {
                    when (aiState) {
                        is AiState.Listening, is AiState.Transcribing -> stopListening()
                        is AiState.ConfirmationRequired -> {
                            // If card is showing and user taps mic, dismiss and start new recording.
                            dismissAi()
                        }
                        else -> startListening()
                    }
                },
                onAddClick = { 
                    prompt = ""
                    parsedAmount = null
                    showTransactionSheet = true
                },
                placeholder = stringResource(R.string.chat_input_placeholder_alt),
                isListening = aiState is AiState.Listening || aiState is AiState.Transcribing
            )
        }

        if (showTransactionSheet) {
            com.example.ui.components.TransactionDetailSheet(
                viewModel = viewModel,
                initialAmount = parsedAmount,
                onDismiss = { 
                    showTransactionSheet = false 
                    prompt = ""
                    parsedAmount = null
                }
            )
        }
    }
}
