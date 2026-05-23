package com.example.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.data.Transaction
import com.example.data.TransactionRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import android.util.Log

class LedgerViewModel(private val repository: TransactionRepository) : ViewModel() {

    private val _aiChatResponse = MutableStateFlow<String?>(null)
    val aiChatResponse: StateFlow<String?> = _aiChatResponse

    fun dismissChatBubble() {
        _aiChatResponse.value = null
    }

    fun executeUserQuery(userPrompt: String) {
        viewModelScope.launch(Dispatchers.IO) {
            _aiChatResponse.value = "Thinking..."
            val apiKey = com.example.BuildConfig.GEMINI_API_KEY
            if (apiKey.isEmpty() || apiKey == "MY_GEMINI_API_KEY") {
                _aiChatResponse.value = "Error: API Key missing"
                return@launch
            }

            try {
                val client = OkHttpClient()
                val functionDeclaration = JSONObject().apply {
                    put("name", "getPartyBalance")
                    put("description", "Get the total balance for a given party name. Positive means CREDIT (they gave us), negative means DEBIT (we gave them).")
                    put("parameters", JSONObject().apply {
                        put("type", "OBJECT")
                        put("properties", JSONObject().apply {
                            put("partyName", JSONObject().apply {
                                put("type", "STRING")
                                put("description", "Name of the party or person")
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

                val userPart = part(userPrompt)
                var currentParts = JSONArray().apply { put(userPart) }
                var chatHistory = JSONArray().apply {
                    put(content("user", currentParts))
                }

                val requestJson = JSONObject().apply {
                    put("contents", chatHistory)
                    put("tools", tools)
                    put("systemInstruction", content("user", JSONArray().apply { put(part("You are a helpful assistant for a ledger app. You speak Indian Hinglish/Hindi.")) }))
                }

                val url = "https://generativelanguage.googleapis.com/v1beta/models/gemini-3.5-flash:generateContent?key=$apiKey"
                val requestBody1 = requestJson.toString().toRequestBody("application/json".toMediaType())
                val request1 = Request.Builder().url(url).post(requestBody1).build()

                val response1 = client.newCall(request1).execute()
                val responseText1 = response1.body?.string() ?: return@launch
                val jsonResponse1 = JSONObject(responseText1)
                
                Log.d("GeminiQuery", "Response 1: $responseText1")

                val candidate1 = jsonResponse1.optJSONArray("candidates")?.optJSONObject(0)
                val content1 = candidate1?.optJSONObject("content")
                val parts1 = content1?.optJSONArray("parts")
                val part1 = parts1?.optJSONObject(0)

                if (part1 != null && part1.has("functionCall")) {
                    val functionCall = part1.getJSONObject("functionCall")
                    val name = functionCall.getString("name")
                    val args = functionCall.getJSONObject("args")

                    if (name == "getPartyBalance") {
                        val partyName = args.getString("partyName")
                        val balance = repository.getPartyBalance(partyName)
                        
                        val functionResponsePart = JSONObject().apply {
                            put("functionResponse", JSONObject().apply {
                                put("name", "getPartyBalance")
                                put("response", JSONObject().apply {
                                    put("balance", balance)
                                })
                            })
                        }
                        
                        chatHistory.put(content1) // Append model's function call
                        chatHistory.put(content("user", JSONArray().apply { put(functionResponsePart) }))
                        
                        val requestJson2 = JSONObject().apply {
                            put("contents", chatHistory)
                            put("systemInstruction", content("user", JSONArray().apply { put(part("You have received the balance $balance from the database. Now, formulate a friendly, conversational response in Hinglish/Hindi for the user. Do not use markdown blocks.")) }))
                        }
                        
                        val requestBody2 = requestJson2.toString().toRequestBody("application/json".toMediaType())
                        val request2 = Request.Builder().url(url).post(requestBody2).build()
                        val response2 = client.newCall(request2).execute()
                        val responseText2 = response2.body?.string() ?: return@launch
                        
                        Log.d("GeminiQuery", "Response 2: $responseText2")
                        val jsonResponse2 = JSONObject(responseText2)
                        val textAnswer = jsonResponse2.optJSONArray("candidates")?.optJSONObject(0)?.optJSONObject("content")?.optJSONArray("parts")?.optJSONObject(0)?.optString("text")
                        
                        _aiChatResponse.value = textAnswer?.trim() ?: "Could not formulate response."
                    }
                } else if (part1 != null && part1.has("text")) {
                    _aiChatResponse.value = part1.getString("text").trim()
                } else {
                     _aiChatResponse.value = "Sorry, I couldn't understand that."
                }

            } catch (e: Exception) {
                Log.e("GeminiQuery", "Error executing query", e)
                _aiChatResponse.value = "Error: ${e.message}"
            }
        }
    }

    private fun part(text: String): JSONObject {
        return JSONObject().apply { put("text", text) }
    }
    
    private fun content(role: String, parts: JSONArray): JSONObject {
        return JSONObject().apply { 
            put("role", role)
            put("parts", parts) 
        }
    }


    // Retrieve all transactions flowing from Room
    val allTransactions: StateFlow<List<Transaction>> = repository.allTransactions
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    // Calculate dynamic total balance (CREDIT is added, DEBIT is subtracted)
    val totalBalance: StateFlow<Double> = allTransactions.map { transactions ->
        transactions.fold(0.0) { sum, tx ->
            if (tx.transactionType.equals("CREDIT", ignoreCase = true)) {
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

    // Calculate monthly/recent total variation (e.g. net flow of current items)
    val netFlow: StateFlow<Double> = allTransactions.map { transactions ->
        transactions.fold(0.0) { sum, tx ->
            val factor = if (tx.transactionType.equals("CREDIT", ignoreCase = true)) 1.0 else -1.0
            sum + (tx.amount * factor)
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = 0.0
    )

    // Check if the transaction is a duplicate
    suspend fun checkDuplicate(partyName: String, amount: Double, type: String): Boolean {
        // 5 minutes window
        val window = System.currentTimeMillis() - 5 * 60 * 1000
        return repository.checkDuplicate(partyName.trim(), amount, type.uppercase().trim(), window)
    }

    // Insert a new transaction
    fun addTransaction(partyName: String, amount: Double, type: String, particular: String? = null, rawTranscript: String? = null) {
        viewModelScope.launch {
            val transaction = Transaction(
                partyName = partyName.trim(),
                amount = amount,
                transactionType = type.uppercase().trim(),
                particular = particular?.trim(),
                rawTranscript = rawTranscript,
                timestamp = System.currentTimeMillis()
            )
            repository.insert(transaction)
        }
    }

    // Delete a transaction from Room
    fun deleteTransaction(id: Int) {
        viewModelScope.launch {
            repository.deleteById(id)
        }
    }

    // Quick seed tool to add basic records if empty
    fun seedSampleData() {
        viewModelScope.launch {
            repository.insert(Transaction(partyName = "Ramesh General Store", amount = 450.00, transactionType = "DEBIT", particular = "Grocery"))
            repository.insert(Transaction(partyName = "Freelance Payment", amount = 8000.00, transactionType = "CREDIT", particular = "Mobile App Design"))
            repository.insert(Transaction(partyName = "Airtel Postpaid", amount = 1299.00, transactionType = "DEBIT", particular = "Net Utility"))
            repository.insert(Transaction(partyName = "Pending Settlement", amount = 0.0, transactionType = "PENDING", particular = "Refund tracking"))
        }
    }
}

class LedgerViewModelFactory(private val repository: TransactionRepository) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(LedgerViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return LedgerViewModel(repository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
