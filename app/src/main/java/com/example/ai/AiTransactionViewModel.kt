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
import com.example.data.AccountRepository
import com.example.data.TransactionRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay

import com.example.ai.extract.PartialExtractionException
import com.example.ai.validation.TransactionValidator
import com.example.ai.query.QueryEngine
import com.ledger.query.ai.SchemaHint
import com.ledger.query.engine.QueryEngine as NewQueryEngine
import com.ledger.query.exec.QueryResult as NewQueryResult

class AiTransactionViewModel(
    private val transactionRepository: TransactionRepository,
    private val accountRepository: AccountRepository,
    private val intentClassifier: IntentClassifier,
    private val transactionExtractor: TransactionExtractor,
    private val normalizer: TranscriptNormalizer = TranscriptNormalizer(),
    private val resolver: CandidateResolver = CandidateResolver(),
    private val transactionValidator: TransactionValidator,
    private val queryEngine: QueryEngine,
    private val newQueryEngine: NewQueryEngine? = null,
    private val knownCategoryNames: List<String> = listOf("food", "rent", "shopping", "general", "transport", "salary")
) : ViewModel() {

    /** In-memory entity name cache — refreshed after each commit and on init. */
    @Volatile private var entityNameCache: List<String> = emptyList()

    init {
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            entityNameCache = try { transactionRepository.getDistinctTitles() } catch (e: Exception) { emptyList() }
        }
    }

    private val _state = MutableStateFlow<AiState>(AiState.Idle)
    val state: StateFlow<AiState> = _state.asStateFlow()

    /** Modifies state to begin listening context */
    fun onMicPressed() {
        _state.value = AiState.Listening
    }

    /** Reverts state to idle if listening is canceled */
    fun onListeningCancelled() {
        _state.value = AiState.Idle
    }

    /**
     * Called when the user explicitly taps the Stop button.
     * Immediately hides the waveform/stop button so the UI feels responsive.
     * For Sarvam, the upload still completes in background and onTranscriptReady()
     * will override this state when the final transcript arrives.
     * For Android native, stop() already delivered onFinal synchronously, so
     * the state will already be past Transcribing when this runs (no-op).
     */
    fun onStopPressed() {
        val current = _state.value
        if (current !is AiState.Listening && current !is AiState.Transcribing) return
        val partial = (current as? AiState.Transcribing)?.partialText ?: ""
        _state.value = AiState.TranscriptReady(partial)
    }

    /** Updates state with partial transcript */
    fun onPartialTranscript(partial: String) {
        _state.value = AiState.Transcribing(partial)
    }

    /** Sets ready state with final STT transcript, then routes intent. */
    fun onTranscriptReady(transcript: String) {
        if (transcript.isBlank()) {
            _state.value = AiState.Failed(FailureReason.EmptyTranscript)
            return
        }
        _state.value = AiState.TranscriptReady(transcript)
        routeIntent(transcript)
    }

    /** Detects language and classifies intent, then advances to extraction or query. */
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
                viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
                    try {
                        if (newQueryEngine != null) {
                            val schema = SchemaHint(
                                knownEntities = entityNameCache,
                                knownCategories = knownCategoryNames,
                                today = java.time.LocalDate.now()
                            )
                            when (val r = newQueryEngine.ask(transcript, schema)) {
                                is NewQueryResult.Scalar -> {
                                    _state.value = AiState.QueryAnswered(QueryResponse(
                                        headline = r.label,
                                        totalAmount = r.amount,
                                        transactionCount = r.txnCount,
                                        transactionIds = emptyList(),
                                        query = ParsedQuery(null, null, null, null, null, transcript)
                                    ))
                                }
                                is NewQueryResult.Grouped -> {
                                    val headline = r.buckets.joinToString(" | ") { "${it.key}: ₹${it.amount.toLong()}" }
                                    _state.value = AiState.QueryAnswered(QueryResponse(
                                        headline = headline.ifBlank { r.label },
                                        totalAmount = r.buckets.sumOf { it.amount },
                                        transactionCount = r.buckets.sumOf { it.count },
                                        transactionIds = emptyList(),
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
                        } else {
                            when (val queryResult = queryEngine.process(transcript)) {
                                is com.example.ai.query.QueryResult.Success -> {
                                    _state.value = AiState.QueryAnswered(queryResult.response)
                                }
                                is com.example.ai.query.QueryResult.Failure -> {
                                    _state.value = AiState.Failed(queryResult.reason, transcript)
                                }
                            }
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

    /** Calls the extractor, checks confidence, routes to auto-save or confirmation. */
    private fun extractTransaction(transcript: String) {
        viewModelScope.launch {
            val normalized = normalizer.normalize(transcript)
            try {
                val rawCandidate = transactionExtractor.extract(normalized, transcript)
                if (rawCandidate == null) {
                    _state.value = AiState.Failed(FailureReason.ExtractionFailed("No candidate generated"), transcript)
                    return@launch
                }

                // Entity fallback: if Gemini returned null entity, derive one from notes or use "General"
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

    /**
     * Called when the user acknowledges a duplicate warning and wants to save anyway.
     * Re-validates field errors only (skips duplicate check).
     * If field errors remain, stays in ConfirmationRequired with just those errors.
     * If no field errors, proceeds to commit.
     */
    fun overrideDuplicateAndSave() {
        val currentState = _state.value
        if (currentState !is AiState.ConfirmationRequired) return

        val candidate = currentState.candidate
        val fieldErrors = transactionValidator.validateFields(candidate)

        if (fieldErrors.isEmpty() && candidate.isComplete()) {
            _state.value = AiState.AutoCommitting(candidate)
        } else {
            // Still has field errors — show them without the duplicate warning
            _state.value = AiState.ConfirmationRequired(candidate, fieldErrors)
        }
    }

    /**
     * Called when the user edits a field in the AiConfirmationCard.
     * Re-runs field validation on the updated candidate.
     * Preserves duplicate warning if it existed.
     */
    fun updateCandidateAndRevalidate(updatedCandidate: CandidateTransaction) {
        val currentState = _state.value
        if (currentState !is AiState.ConfirmationRequired) return

        val fieldErrors = transactionValidator.validateFields(updatedCandidate)
        val existingDuplicateError = currentState.errors.filterIsInstance<ValidationError.DuplicateSuspected>()
        val allErrors = fieldErrors + existingDuplicateError

        _state.value = AiState.ConfirmationRequired(updatedCandidate, allErrors)
    }

    /** Flags state with STT error block */
    fun onSttError(reason: FailureReason) {
        _state.value = AiState.Failed(reason)
    }

    /** Classifies intent type dynamically into state */
    fun onIntentClassified(transcript: String, intent: AiIntent) {
        if (intent is AiIntent.Unknown) {
            _state.value = AiState.Failed(FailureReason.IntentUnclear, transcript)
        } else {
            _state.value = AiState.Extracting(transcript, intent)
        }
    }

    /** Marks extraction success ready for validation context */
    fun onCandidateExtracted(candidate: CandidateTransaction) {
        _state.value = AiState.Validating(candidate)
    }

    /** Analyzes score block to decide user intervention requirement */
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

    /** Receives edits overriding current valid uncommitted scope */
    fun onCandidateEdited(updatedCandidate: CandidateTransaction) {
        val currentState = _state.value
        if (currentState is AiState.ConfirmationRequired) {
            _state.value = currentState.copy(candidate = updatedCandidate)
        }
    }

    /** Mutates commit block logic into confirmed pending structure */
    fun onCommitConfirmed() {
        val currentState = _state.value
        if (currentState is AiState.ConfirmationRequired) {
            _state.value = AiState.Committed(transactionId = "pending", candidate = currentState.candidate)
        } else if (currentState is AiState.AutoCommitting) {
            _state.value = AiState.Committed(transactionId = "pending", candidate = currentState.candidate)
        }
    }

    /** Rejects current confirmation screen cycle block back to root */
    fun onCommitDismissed() {
        _state.value = AiState.Idle
    }

    /** Shifts query metadata up for processor matching */
    fun onQueryParsed(query: ParsedQuery) {
        _state.value = AiState.QueryProcessing(query.rawTranscript)
    }

    /** Maps query responses forward out to client view targets */
    fun onQueryAnswered(response: QueryResponse) {
        _state.value = AiState.QueryAnswered(response)
    }

    /** Marks transaction as committed and auto-resets to Idle after a delay. */
    fun markCommitted(transactionId: String, candidate: CandidateTransaction) {
        _state.value = AiState.Committed(transactionId, candidate)
        viewModelScope.launch {
            // Refresh entity cache so new names are queryable immediately
            entityNameCache = try {
                transactionRepository.getDistinctTitles()
            } catch (e: Exception) { entityNameCache }
            delay(1500L)  // Allow toast to be visible.
            if (_state.value is AiState.Committed) {
                _state.value = AiState.Idle
            }
        }
    }

    /** Resets local block into baseline scope */
    fun reset() {
        _state.value = AiState.Idle
    }
}
