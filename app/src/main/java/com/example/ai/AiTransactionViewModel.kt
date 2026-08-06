package com.example.ai

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.ai.extract.CandidateResolver
import com.example.ai.extract.TransactionExtractor
import com.example.ai.extract.TranscriptNormalizer
import com.example.ai.intent.IntentClassifier
import com.example.ai.model.*
import com.example.ai.state.AiState
import com.example.ai.state.FailureReason
import com.example.data.TransactionRepository
import com.example.data.AccountRepository
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import kotlinx.coroutines.Dispatchers

import com.example.ai.extract.PartialExtractionException
import com.example.ai.validation.TransactionValidator
import com.ledger.query.ai.SchemaHint
import com.ledger.query.engine.QueryEngine
import com.ledger.query.exec.QueryResult as NewQueryResult

class AiTransactionViewModel(
    private val transactionRepository: TransactionRepository,
    private val accountRepository: AccountRepository,
    private val intentClassifier: IntentClassifier,
    private val transactionExtractor: TransactionExtractor,
    private val normalizer: TranscriptNormalizer = TranscriptNormalizer(),
    private val resolver: CandidateResolver = CandidateResolver(),
    private val transactionValidator: TransactionValidator,
    private val queryEngine: QueryEngine
) : ViewModel() {

    /** In-memory entity name cache — refreshed after each commit and on init. */
    @Volatile private var entityNameCache: List<String> = emptyList()
    @Volatile private var knownCategoryNames: List<String> = emptyList()

    /** Signals when the entity/category cache has been populated at least once. */
    private val cacheReady = CompletableDeferred<Unit>()

    init {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                entityNameCache = transactionRepository.getDistinctTitles()
            } catch (e: Exception) {
                entityNameCache = emptyList()
            }
            try {
                knownCategoryNames = transactionRepository.allCategories.first().map { it.name.lowercase() }
            } catch (e: Exception) {
                knownCategoryNames = listOf("food", "rent", "shopping", "general", "transport", "salary")
            }
            cacheReady.complete(Unit)
        }
    }

    private val _state = MutableStateFlow<AiState>(AiState.Idle)
    val state: StateFlow<AiState> = _state.asStateFlow()

    fun onMicPressed() {
        _state.value = AiState.Listening
    }

    fun onListeningCancelled() {
        _state.value = AiState.Idle
    }

    fun onStopPressed() {
        val current = _state.value
        if (current !is AiState.Listening && current !is AiState.Transcribing) return
        val partial = (current as? AiState.Transcribing)?.partialText ?: ""
        _state.value = AiState.TranscriptReady(partial)
    }

    fun onPartialTranscript(partial: String) {
        _state.value = AiState.Transcribing(partial)
    }

    fun onTranscriptReady(transcript: String) {
        if (transcript.isBlank()) {
            _state.value = AiState.Failed(FailureReason.EmptyTranscript)
            return
        }
        _state.value = AiState.TranscriptReady(transcript)
        routeIntent(transcript)
    }

    private fun routeIntent(transcript: String) {
        _state.value = AiState.IntentRouting(transcript)
        val result = intentClassifier.classify(transcript)
        when (result.intent) {
            is AiIntent.Transaction -> {
                _state.value = AiState.Extracting(transcript, result.intent, result.language)
                extractTransaction(transcript)
            }
            is AiIntent.Query -> {
                _state.value = AiState.QueryProcessing(transcript)
                viewModelScope.launch(Dispatchers.IO) {
                    // Ensure entity/category cache is populated before routing
                    cacheReady.await()
                    try {
                        val schema = SchemaHint(
                            knownEntities = entityNameCache,
                            knownCategories = knownCategoryNames,
                            today = java.time.LocalDate.now()
                        )
                        when (val r = queryEngine.ask(transcript, schema)) {
                            is NewQueryResult.Scalar -> {
                                _state.value = AiState.QueryAnswered(QueryResponse(
                                    headline = r.label,
                                    totalAmount = r.amount,
                                    transactionCount = r.txnCount,
                                    transactionIds = r.transactionIds,
                                    timeContext = r.timeContext,
                                    direction = r.plan.direction.name,
                                    query = ParsedQuery(null, null, null, null, null, transcript)
                                ))
                            }
                            is NewQueryResult.Grouped -> {
                                val headline = r.buckets.joinToString(" | ") { "${it.key}: ₹${it.amount.toLong()}" }
                                _state.value = AiState.QueryAnswered(QueryResponse(
                                    headline = headline.ifBlank { r.label },
                                    totalAmount = r.buckets.sumOf { it.amount },
                                    transactionCount = r.buckets.sumOf { it.count },
                                    transactionIds = r.transactionIds,
                                    timeContext = r.timeContext,
                                    direction = r.plan.direction.name,
                                    query = ParsedQuery(null, null, null, null, null, transcript)
                                ))
                            }
                            is NewQueryResult.Analysis -> {
                                _state.value = AiState.QueryAnswered(QueryResponse(
                                    headline = r.narrative,
                                    totalAmount = 0.0,
                                    transactionCount = 0,
                                    transactionIds = emptyList(),
                                    query = ParsedQuery(null, null, null, null, null, transcript)
                                ))
                            }
                            is NewQueryResult.NeedsClarification -> {
                                val headline = "${r.question}: ${r.options.take(4).joinToString(" / ")}"
                                _state.value = AiState.QueryAnswered(QueryResponse(
                                    headline = headline,
                                    totalAmount = 0.0,
                                    transactionCount = 0,
                                    transactionIds = emptyList(),
                                    query = ParsedQuery(null, null, null, null, null, transcript)
                                ))
                            }
                            is NewQueryResult.Failed -> {
                                _state.value = AiState.Failed(
                                    FailureReason.QueryEngineError(r.reason), transcript
                                )
                            }
                            else -> _state.value = AiState.Failed(
                                FailureReason.QueryEngineError("Unexpected result type"), transcript
                            )
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                        _state.value = AiState.Failed(
                            FailureReason.QueryEngineError(e.javaClass.simpleName + ": " + e.message),
                            transcript
                        )
                    }
                }
            }
            is AiIntent.Unknown -> {
                _state.value = AiState.Failed(FailureReason.IntentUnclear, transcript)
            }
        }
    }

    private fun extractTransaction(transcript: String) {
        viewModelScope.launch {
            val normalized = normalizer.normalize(transcript)
            try {
                val rawCandidate = transactionExtractor.extract(normalized, transcript)
                if (rawCandidate == null) {
                    _state.value = AiState.Failed(FailureReason.ExtractionFailed("No candidate generated"), transcript)
                    return@launch
                }

                val resolvedEntity = rawCandidate.entity?.takeIf { it.isNotBlank() }
                    ?: rawCandidate.notes?.split(" ")?.firstOrNull()?.replaceFirstChar { it.uppercase() }
                    ?: "General"
                val candidate = if (resolvedEntity != rawCandidate.entity) {
                    rawCandidate.copy(entity = resolvedEntity)
                } else {
                    rawCandidate
                }

                val resolvedCategoryId = resolver.resolveCategoryId(candidate.categoryHint)
                val fieldErrors = transactionValidator.validateFields(candidate)
                val duplicateError = transactionValidator.checkDuplicate(candidate, resolvedCategoryId)
                val allErrors = fieldErrors + listOfNotNull(duplicateError)

                if (allErrors.isEmpty()
                    && ConfidenceScore.tier(candidate.confidence.overall) == ConfidenceTier.HIGH
                    && candidate.isComplete()) {
                    _state.value = AiState.AutoCommitting(candidate)
                } else {
                    _state.value = AiState.ConfirmationRequired(candidate, allErrors)
                }
            } catch (e: PartialExtractionException) {
                val prefillType = when (e.direction) {
                    Direction.INCOME -> "INCOME"
                    Direction.CREDIT -> "CREDIT"
                    Direction.DEBIT -> "DEBIT"
                    else -> "EXPENSE"
                }
                _state.value = AiState.ManualFallback(
                    prefillAmount = e.amount,
                    prefillType = if (e.direction != null) prefillType else null,
                    rawTranscript = transcript
                )
            } catch (e: Exception) {
                _state.value = AiState.Failed(FailureReason.ExtractionFailed(e.message), transcript)
            }
        }
    }

    fun onManualFallbackDismissed() {
        _state.value = AiState.Idle
    }

    fun overrideDuplicateAndSave() {
        val currentState = _state.value
        if (currentState !is AiState.ConfirmationRequired) return
        val candidate = currentState.candidate
        val fieldErrors = transactionValidator.validateFields(candidate)
        if (fieldErrors.isEmpty() && candidate.isComplete()) {
            _state.value = AiState.AutoCommitting(candidate)
        } else {
            _state.value = AiState.ConfirmationRequired(candidate, fieldErrors)
        }
    }

    fun updateCandidateAndRevalidate(updatedCandidate: CandidateTransaction) {
        val currentState = _state.value
        if (currentState !is AiState.ConfirmationRequired) return
        val fieldErrors = transactionValidator.validateFields(updatedCandidate)
        val existingDuplicateError = currentState.errors.filterIsInstance<ValidationError.DuplicateSuspected>()
        val allErrors = fieldErrors + existingDuplicateError
        _state.value = AiState.ConfirmationRequired(updatedCandidate, allErrors)
    }

    fun onSttError(reason: FailureReason) {
        _state.value = AiState.Failed(reason)
    }

    fun onIntentClassified(transcript: String, intent: AiIntent) {
        if (intent is AiIntent.Unknown) {
            _state.value = AiState.Failed(FailureReason.IntentUnclear, transcript)
        } else {
            _state.value = AiState.Extracting(transcript, intent)
        }
    }

    fun onCandidateExtracted(candidate: CandidateTransaction) {
        _state.value = AiState.Validating(candidate)
    }

    fun onValidationComplete(
        candidate: CandidateTransaction,
        errors: List<ValidationError>,
        tier: ConfidenceTier
    ) {
        if (errors.isEmpty() && tier == ConfidenceTier.HIGH) {
            _state.value = AiState.AutoCommitting(candidate)
        } else {
            _state.value = AiState.ConfirmationRequired(candidate, errors)
        }
    }

    fun onCandidateEdited(updatedCandidate: CandidateTransaction) {
        val currentState = _state.value
        if (currentState is AiState.ConfirmationRequired) {
            _state.value = currentState.copy(candidate = updatedCandidate)
        }
    }

    fun onCommitConfirmed() {
        val currentState = _state.value
        if (currentState is AiState.ConfirmationRequired) {
            _state.value = AiState.Committed(transactionId = "pending", candidate = currentState.candidate)
        } else if (currentState is AiState.AutoCommitting) {
            _state.value = AiState.Committed(transactionId = "pending", candidate = currentState.candidate)
        }
    }

    fun onCommitDismissed() {
        _state.value = AiState.Idle
    }

    fun onQueryParsed(query: ParsedQuery) {
        _state.value = AiState.QueryProcessing(query.rawTranscript)
    }

    fun onQueryAnswered(response: QueryResponse) {
        _state.value = AiState.QueryAnswered(response)
    }

    fun markCommitted(transactionId: String, candidate: CandidateTransaction) {
        _state.value = AiState.Committed(transactionId, candidate)
        viewModelScope.launch {
            // Refresh entity cache so newly added names are queryable immediately
            try {
                entityNameCache = transactionRepository.getDistinctTitles()
            } catch (e: Exception) { /* keep existing cache */ }
            delay(1500L)
            if (_state.value is AiState.Committed) {
                _state.value = AiState.Idle
            }
        }
    }

    fun reset() {
        _state.value = AiState.Idle
    }
}
