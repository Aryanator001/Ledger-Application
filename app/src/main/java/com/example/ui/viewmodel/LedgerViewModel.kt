package com.example.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.data.Transaction
import com.example.data.TransactionWithCategory
import com.example.data.TransactionRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import android.util.Log
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject

// ============================================================================
// MAIN VIEWMODEL CLASS
// ============================================================================

class LedgerViewModel(
    private val repository: TransactionRepository,
    val accountRepository: com.example.data.AccountRepository,
    private val analyticsService: com.example.data.AnalyticsService
) : ViewModel() {

    init {
        seedAccountsIfNeeded()
    }

    private fun seedAccountsIfNeeded() {
        viewModelScope.launch {
            if (accountRepository.getAccountCount() == 0) {
                seedAccounts()
                // Seed default categories
                val categories = listOf(
                    com.example.data.Category(id = "general", name = "General", icon = "more_horiz", colorHex = "#607D8B", type = "BOTH", isSystem = true, order = 0),
                    com.example.data.Category(id = "food", name = "Food", icon = "restaurant", colorHex = "#FF5722", type = "EXPENSE", isSystem = true, order = 1),
                    com.example.data.Category(id = "rent", name = "Rent", icon = "receipt_long", colorHex = "#FF9800", type = "EXPENSE", isSystem = true, order = 2),
                    com.example.data.Category(id = "shopping", name = "Shopping", icon = "shopping_bag", colorHex = "#E91E63", type = "EXPENSE", isSystem = true, order = 3)
                )
                categories.forEach { repository.insertCategory(it) }
            }
        }
    }

    // ========================================================================
    // SECTION 1: AI RESPONSE STATE
    // ========================================================================
    
    private val _aiChatResponse = MutableStateFlow<String?>(null)
    val aiChatResponse: StateFlow<String?> = _aiChatResponse

    fun dismissChatBubble() {
        _aiChatResponse.value = null
    }

    // ========================================================================
    // SECTION 2: AI QUERY HANDLER (Using REST API for stability)
    // ========================================================================
    
    fun executeUserQuery(userPrompt: String, currencySymbol: String = "₹") {
        viewModelScope.launch(Dispatchers.IO) {
            _aiChatResponse.value = "Thinking..."

            try {
                val apiKey = com.example.BuildConfig.GEMINI_API_KEY
                if (apiKey.isEmpty() || apiKey == "MY_GEMINI_API_KEY") {
                    _aiChatResponse.value = "Error: API Key missing"
                    return@launch
                }

                val client = OkHttpClient()
                val url = "https://generativelanguage.googleapis.com/v1beta/models/gemini-3.5-flash:generateContent?key=$apiKey"

                val functionDeclaration = JSONObject().apply {
                    put("name", "getPartyBalance")
                    put("description", "Fetches the current net balance for a person or party from the database. Positive numbers mean they owe me money (Credit), negative numbers mean I owe them money (Debit).")
                    put("parameters", JSONObject().apply {
                        put("type", "OBJECT")
                        put("properties", JSONObject().apply {
                            put("partyName", JSONObject().apply {
                                put("type", "STRING")
                                put("description", "The name of the party to search for. Use the most relevant name from the user's query.")
                            })
                        })
                        put("required", JSONArray().apply { put("partyName") })
                    })
                }

                val tools = JSONArray().apply {
                    put(JSONObject().apply {
                        put("functionDeclarations", JSONArray().apply { put(functionDeclaration) })
                    })
                }

                val systemInstruction = """
                    You are a professional financial ledger assistant for Indian business owners. 
                    You help users track their "Udhaar" (debts) and "Paisa" (money).
                    When a user asks about balance, total, or how much they owe/are owed, ALWAYS use the getPartyBalance tool.
                    NEVER guess a number. Use the value returned by the tool.
                    If the tool returns 0 or no data, say that you couldn't find any transactions for that name.
                    Speak in a friendly, conversational mix of Hindi and English (Hinglish) using Latin script.
                """.trimIndent()

                val requestJson = JSONObject().apply {
                    put("contents", JSONArray().apply {
                        put(JSONObject().apply {
                            put("role", "user")
                            put("parts", JSONArray().apply {
                                put(JSONObject().apply { put("text", userPrompt) })
                            })
                        })
                    })
                    put("tools", tools)
                    put("systemInstruction", JSONObject().apply {
                        put("parts", JSONArray().apply {
                            put(JSONObject().apply { put("text", systemInstruction) })
                        })
                    })
                    put("generationConfig", JSONObject().apply {
                        put("temperature", 0.1) // Low temperature for factual queries
                    })
                }

                val requestBody = requestJson.toString().toRequestBody("application/json".toMediaType())
                val request = Request.Builder().url(url).post(requestBody).build()
                val response = client.newCall(request).execute()
                val responseText = response.body?.string() ?: ""
                
                Log.d("GeminiQuery", "AI Response 1: $responseText")
                if (responseText.isEmpty()) {
                    _aiChatResponse.value = "Server is not responding. Please try again."
                    return@launch
                }

                val jsonResponse = JSONObject(responseText)
                
                // Check for errors in response
                if (jsonResponse.has("error")) {
                    val errorMsg = jsonResponse.getJSONObject("error").optString("message", "Unknown API error")
                    _aiChatResponse.value = "AI Error: $errorMsg"
                    return@launch
                }

                val candidates = jsonResponse.optJSONArray("candidates")
                if (candidates == null || candidates.length() == 0) {
                    _aiChatResponse.value = "I can't answer that right now (Safety/Filtering)."
                    return@launch
                }

                val firstCandidate = candidates.getJSONObject(0)
                val contentObj = firstCandidate.optJSONObject("content")
                val partsArray = contentObj?.optJSONArray("parts")
                
                if (partsArray == null || partsArray.length() == 0) {
                     _aiChatResponse.value = "AI returned an empty response."
                     return@launch
                }

                var textFound = ""
                var functionCallFound: JSONObject? = null

                for (i in 0 until partsArray.length()) {
                    val part = partsArray.getJSONObject(i)
                    if (part.has("functionCall")) {
                        functionCallFound = part.getJSONObject("functionCall")
                    } else if (part.has("text")) {
                        textFound += part.getString("text")
                    }
                }

                if (functionCallFound != null) {
                    val name = functionCallFound.getString("name")
                    val args = functionCallFound.optJSONObject("args") ?: JSONObject()

                    if (name == "getPartyBalance") {
                        val partyName = args.optString("partyName", "").trim()
                        if (partyName.isEmpty()) {
                            _aiChatResponse.value = "Please specify a name."
                            return@launch
                        }

                        val balance = repository.getPartyBalance(partyName)
                        
                        // Follow-up request with balance - provide it as part of a dialogue context
                        val followUpContext = """
                            User asked: "$userPrompt"
                            I searched the database for "$partyName" and the balance is $balance.
                            Now respond to the user in Hinglish.
                            Balance positive means they owe money. 
                            Balance negative means I owe them money.
                        """.trimIndent()
                        
                        val followUpJson = JSONObject().apply {
                            put("contents", JSONArray().apply {
                                put(JSONObject().apply {
                                    put("role", "user")
                                    put("parts", JSONArray().apply {
                                        put(JSONObject().apply { put("text", followUpContext) })
                                    })
                                })
                            })
                            put("systemInstruction", JSONObject().apply {
                                put("parts", JSONArray().apply {
                                    put(JSONObject().apply { put("text", systemInstruction) })
                                })
                            })
                        }
                        
                        val followUpBody = followUpJson.toString().toRequestBody("application/json".toMediaType())

                        val followUpRequest = Request.Builder().url(url).post(followUpBody).build()
                        val followUpResponse = client.newCall(followUpRequest).execute()
                        val followUpText = followUpResponse.body?.string() ?: ""
                        
                        Log.d("GeminiQuery", "AI Response 2: $followUpText")
                        val finalJson = JSONObject(followUpText)
                        val finalPart = finalJson.optJSONArray("candidates")?.optJSONObject(0)?.optJSONObject("content")?.optJSONArray("parts")?.optJSONObject(0)
                        val textAnswer = finalPart?.optString("text")
                        
                        _aiChatResponse.value = textAnswer?.trim() ?: "Aapka balance $currencySymbol$balance hai."
                    }
                } else if (textFound.isNotEmpty()) {
                    _aiChatResponse.value = textFound.trim()
                } else {
                    _aiChatResponse.value = "I'm having trouble connecting to the database. Try asking again differently."
                }
                
            } catch (e: Exception) {
                Log.e("GeminiQuery", "Error", e)
                _aiChatResponse.value = "Error encountered while thinking."
            }
        }
    }

    // ========================================================================
    // SECTION 4: TRANSACTION MANAGEMENT
    // ========================================================================

    val allCategories: StateFlow<List<com.example.data.Category>> = repository.allCategories
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    val allAccounts: StateFlow<List<com.example.data.Account>> = accountRepository.allAccounts
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    val allAccountsWithBalance: StateFlow<List<com.example.data.AccountWithBalance>> = accountRepository.getAccountWithBalanceListFlow()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    private val _selectedAccountId = MutableStateFlow("all")
    val selectedAccountId: StateFlow<String> = _selectedAccountId

    fun selectAccount(id: String) {
        _selectedAccountId.value = id
    }

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val transactionsByAccount: StateFlow<List<Transaction>> = _selectedAccountId.flatMapLatest { id ->
        repository.getTransactionsByAccount(id)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    // Retrieve all transactions from Room
    val allTransactions: StateFlow<List<Transaction>> = repository.allTransactions
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    val allTransactionsWithCategory: StateFlow<List<TransactionWithCategory>> = repository.allTransactionsWithCategory
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    // Calculate total balance (INCOME added, EXPENSE subtracted)
    val totalBalance: StateFlow<Double> = allTransactions.map { transactions ->
        transactions.fold(0.0) { sum, tx ->
            if (tx.type.equals("INCOME", ignoreCase = true) || tx.type.equals("CREDIT", ignoreCase = true)) {
                sum + tx.amount
            } else {
                sum - tx.amount
            }
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = 0.0
    )

    // Calculate net flow
    val netFlow: StateFlow<Double> = allTransactions.map { transactions ->
        transactions.fold(0.0) { sum, tx ->
            val factor = if (tx.type.equals("INCOME", ignoreCase = true) || tx.type.equals("CREDIT", ignoreCase = true)) 1.0 else -1.0
            sum + (tx.amount * factor)
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = 0.0
    )

    // Phase 2 Balance Card Metrics
    val netWorth: StateFlow<Long> = accountRepository.getNetWorthFlow().map { (it * 100).toLong() }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = 0L
        )

    val monthlyIncome: StateFlow<Long> = allTransactions.map { _ ->
        val cal = java.util.Calendar.getInstance()
        analyticsService.getMonthlyIncome(cal.get(java.util.Calendar.MONTH) + 1, cal.get(java.util.Calendar.YEAR))
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = 0L
    )

    val monthlyExpense: StateFlow<Long> = allTransactions.map { _ ->
        val cal = java.util.Calendar.getInstance()
        analyticsService.getMonthlySpend(cal.get(java.util.Calendar.MONTH) + 1, cal.get(java.util.Calendar.YEAR))
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = 0L
    )

    // Manual parser
    fun parseAmountQuietly(input: String): Double? {
        val regex = Regex("\\d+(\\.\\d+)?")
        val match = regex.find(input)
        return match?.value?.toDoubleOrNull()
    }

    // Check for duplicate transactions
    suspend fun checkDuplicate(title: String, amount: Double, type: String): Boolean {
        val window = System.currentTimeMillis() - 5 * 60 * 1000
        return repository.checkDuplicate(title.trim(), amount, type.uppercase().trim(), window)
    }

    // Update transaction
    fun updateTransaction(transaction: Transaction) {
        viewModelScope.launch {
            repository.updateTransaction(transaction)
        }
    }

    // Add new transaction
    fun addTransaction(
        title: String, 
        amount: Double, 
        type: String, 
        description: String? = null, 
        rawTranscript: String? = null, 
        categoryId: String = "general", 
        accountId: String = "default_cash", 
        toAccountId: String? = null,
        tags: String = "", 
        date: Long = System.currentTimeMillis()
    ) {
        viewModelScope.launch {
            val transaction = Transaction(
                title = title.trim(),
                amount = amount,
                categoryId = categoryId,
                accountId = accountId,
                toAccountId = toAccountId,
                type = type.uppercase().trim(),
                description = description?.trim(),
                rawTranscript = rawTranscript,
                tags = tags,
                date = date
            )
            repository.insertTransaction(transaction)
        }
    }

    // Add new account
    fun addAccount(
        name: String, 
        type: String, 
        startingBalance: Double, 
        color: String, 
        icon: String, 
        excludeFromTotal: Boolean = false
    ) {
        viewModelScope.launch {
            val currentMaxSort = accountRepository.allAccounts.first().maxOfOrNull { it.sortOrder } ?: 0
            val account = com.example.data.Account(
                name = name.trim(),
                type = type.trim(),
                startingBalance = startingBalance,
                color = color.trim(),
                icon = icon.trim(),
                excludeFromTotal = excludeFromTotal,
                sortOrder = currentMaxSort + 1,
                createdAt = System.currentTimeMillis()
            )
            accountRepository.insertAccount(account)
        }
    }

    fun updateAccount(account: com.example.data.Account) {
        viewModelScope.launch {
            accountRepository.updateAccount(account)
        }
    }

    // Delete account with options
    fun deleteAccount(id: String, reassignToAccountId: String? = null, deleteAllTransactions: Boolean = false) {
        viewModelScope.launch {
            val transactions = repository.allTransactions.first()
            if (deleteAllTransactions) {
                transactions.forEach { tx ->
                    if (tx.accountId == id || tx.toAccountId == id) {
                        repository.deleteById(tx.id)
                    }
                }
            } else if (reassignToAccountId != null) {
                transactions.forEach { tx ->
                    if (tx.accountId == id) {
                        repository.updateTransaction(tx.copy(accountId = reassignToAccountId))
                    }
                    if (tx.toAccountId == id) {
                        repository.updateTransaction(tx.copy(toAccountId = reassignToAccountId))
                    }
                }
            }
            accountRepository.deleteAccountById(id)
        }
    }

    fun archiveAccount(id: String, archived: Boolean) {
        viewModelScope.launch {
            val account = accountRepository.getAccountById(id)
            if (account != null) {
                accountRepository.updateAccount(account.copy(isArchived = archived))
            }
        }
    }

    fun hideAccount(id: String, hidden: Boolean) {
        viewModelScope.launch {
            val account = accountRepository.getAccountById(id)
            if (account != null) {
                accountRepository.updateAccount(account.copy(isHidden = hidden))
            }
        }
    }

    fun updateAccountSortOrder(reorderedList: List<com.example.data.Account>) {
        viewModelScope.launch {
            reorderedList.forEachIndexed { index, account ->
                accountRepository.updateAccount(account.copy(sortOrder = index))
            }
        }
    }

    // Add new category
    fun addCategory(id: String, name: String, icon: String, colorHex: String, type: String) {
        viewModelScope.launch {
            val category = com.example.data.Category(
                id = id,
                name = name.trim(),
                icon = icon.trim(),
                colorHex = colorHex.trim(),
                type = type.uppercase().trim(),
                isSystem = false
            )
            repository.insertCategory(category)
        }
    }

    // Delete transaction
    fun deleteTransaction(id: String) {
        viewModelScope.launch {
            repository.deleteById(id)
        }
    }

    // Archive transaction
    fun archiveTransaction(id: String, archived: Boolean) {
        viewModelScope.launch {
            val tx = repository.getTransactionById(id)
            if (tx != null) {
                repository.updateTransaction(tx.copy(isArchived = archived))
            }
        }
    }

    // Seed accounts
    fun seedAccounts() {
        viewModelScope.launch {
            val accounts = listOf(
                com.example.data.Account(id = "default_cash", name = "Cash", type = "CASH", color = "#6FAF8F", startingBalance = 0.0, icon = "💵"),
                com.example.data.Account(id = "upi_account", name = "PhonePe UPI", type = "UPI", color = "#8B7FD1", startingBalance = 0.0, icon = "📱"),
                com.example.data.Account(id = "bank_account", name = "HDFC Bank", type = "BANK", color = "#5B8DEF", startingBalance = 0.0, icon = "🏦")
            )
            accounts.forEach { accountRepository.insertAccount(it) }
        }
    }

    // Seed sample data
    fun seedSampleData() {
        viewModelScope.launch {
            seedAccounts()
            // Seed a default category first
            val generalCategory = com.example.data.Category(id = "general", name = "General", icon = "more_horiz", colorHex = "#607D8B", type = "BOTH", isSystem = true, order = 0)
            repository.insertCategory(generalCategory)

            repository.insertTransaction(Transaction(title = "Ramesh General Store", amount = 450.00, type = "EXPENSE", categoryId = "general", accountId = "default_cash", description = "Grocery", date = System.currentTimeMillis()))
            repository.insertTransaction(Transaction(title = "Freelance Payment", amount = 8000.00, type = "INCOME", categoryId = "general", accountId = "bank_account", description = "Mobile App Design", date = System.currentTimeMillis()))
            repository.insertTransaction(Transaction(title = "Airtel Postpaid", amount = 1299.00, type = "EXPENSE", categoryId = "general", accountId = "upi_account", description = "Net Utility", date = System.currentTimeMillis()))
        }
    }

} // ← END OF CLASS

// ============================================================================
// VIEWMODEL FACTORY
// ============================================================================

class LedgerViewModelFactory(
    private val repository: TransactionRepository,
    private val accountRepository: com.example.data.AccountRepository,
    private val analyticsService: com.example.data.AnalyticsService
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(LedgerViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return LedgerViewModel(repository, accountRepository, analyticsService) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
