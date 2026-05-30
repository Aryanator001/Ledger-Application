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
import com.example.ai.model.ConfidenceScore
import com.example.ai.model.ConfidenceTier
import kotlinx.coroutines.delay

import com.example.ai.validation.TransactionValidator
import com.example.ai.query.QueryEngine

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
                        when (val queryResult = queryEngine.process(transcript)) {
                            is com.example.ai.query.QueryResult.Success -> {
                                _state.value = AiState.QueryAnswered(queryResult.response)
                            }
                            is com.example.ai.query.QueryResult.Failure -> {
                                _state.value = AiState.Failed(queryResult.reason, transcript)
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
                val candidate = transactionExtractor.extract(normalized, transcript)
                if (candidate == null) {
                    _state.value = AiState.Failed(FailureReason.ExtractionFailed("No candidate generated"), transcript)
                    return@launch
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
            } catch (e: Exception) {
                _state.value = AiState.Failed(FailureReason.ExtractionFailed(e.message), transcript)
            }
        }
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
