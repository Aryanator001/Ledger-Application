package com.example

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.enableEdgeToEdge
import androidx.core.content.ContextCompat
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.data.Transaction
import com.example.ui.theme.MyApplicationTheme
import com.example.ui.viewmodel.LedgerViewModel
import com.example.ui.viewmodel.LedgerViewModelFactory
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject

data class ParsedTransaction(
    val partyName: String,
    val amount: Double,
    val transactionType: String,
    val rawTranscript: String
)

suspend fun parseTransactionIntent(input: String): ParsedTransaction? = withContext(Dispatchers.IO) {
    val apiKey = BuildConfig.GEMINI_API_KEY
    if (apiKey.isEmpty() || apiKey == "MY_GEMINI_API_KEY") {
        Log.e("GeminiParser", "API Key is missing!")
        return@withContext null
    }

    val url = "https://generativelanguage.googleapis.com/v1beta/models/gemini-3.5-flash:generateContent?key=$apiKey"

    val systemInstruction = JSONObject().apply {
        put("parts", JSONArray().apply {
            put(JSONObject().apply {
                put("text", "You are a financial parser for Indian Hinglish. 'diya', 'kharcha', 'diye' means DEBIT. 'mila', 'aaye', 'received' means CREDIT. Extract the monetary value as a Double. Output the exact original sentence as `rawTranscript`. Return ONLY a raw JSON object. Do not use markdown formatting. Do not include ```json blocks. Return output STRICTLY as a JSON object matching this schema: {\"partyName\": \"string\", \"amount\": number, \"transactionType\": \"DEBIT\"|\"CREDIT\", \"rawTranscript\": \"string\"}")
            })
        })
    }

    val requestBodyJson = JSONObject().apply {
        put("systemInstruction", systemInstruction)
        put("contents", JSONArray().apply {
            put(JSONObject().apply {
                put("parts", JSONArray().apply {
                    put(JSONObject().apply {
                        put("text", input)
                    })
                })
            })
        })
        put("generationConfig", JSONObject().apply {
            put("responseMimeType", "application/json")
        })
    }

    val requestBody = requestBodyJson.toString().toRequestBody("application/json".toMediaType())

    val request = Request.Builder()
        .url(url)
        .post(requestBody)
        .build()

    val client = OkHttpClient()
    var schemaOutputRaw = ""
    try {
        val response = client.newCall(request).execute()
        val responseText = response.body?.string() ?: return@withContext null

        val jsonObj = JSONObject(responseText)
        schemaOutputRaw = jsonObj.getJSONArray("candidates")
            .getJSONObject(0)
            .getJSONObject("content")
            .getJSONArray("parts")
            .getJSONObject(0)
            .getString("text")

        val sanitizedOutput = schemaOutputRaw
            .replace("```json", "", ignoreCase = true)
            .replace("```", "")
            .trim()

        val parsedJson = JSONObject(sanitizedOutput)
        val parsed = ParsedTransaction(
            partyName = parsedJson.optString("partyName", "Unknown"),
            amount = parsedJson.optDouble("amount", 0.0),
            transactionType = parsedJson.optString("transactionType", "DEBIT"),
            rawTranscript = parsedJson.optString("rawTranscript", input)
        )
        return@withContext parsed
    } catch (e: Exception) {
        Log.e("GeminiParser", "Raw AI Output was: $schemaOutputRaw", e)
        return@withContext null
    }
}

// --- DESIGN TOKENS ---
private val ColorBackground = Color(0xFFF8F9FA)
private val ColorSurface = Color(0xFFFFFFFF)
private val ColorTextPrimary = Color(0xFF1A1D20)
private val ColorTextSecondary = Color(0xFF6C757D)
private val ColorPrimaryAccent = Color(0xFF2F54EB) // Cobalt Blue
private val ColorIncomeAccent = Color(0xFF27A770)  // Muted Emerald
private val ColorExpenseAccent = Color(0xFFEA4E3D) // Muted Crimson
private val ColorDockBackground = Color(0xFF1A1D20)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MyApplicationTheme {
                val app = application as LedgerApplication
                val factory = LedgerViewModelFactory(app.repository)
                val viewModel: LedgerViewModel = viewModel(factory = factory)
                
                MainScreen(viewModel = viewModel)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(viewModel: LedgerViewModel) {
    val aiChatResponse by viewModel.aiChatResponse.collectAsStateWithLifecycle()
    val transactions by viewModel.allTransactions.collectAsStateWithLifecycle()
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    var inputText by remember { mutableStateOf("") }
    var isRecording by remember { mutableStateOf(false) }
    var isParsing by remember { mutableStateOf(false) }
    var parsedTransactionForConfirmation by remember { mutableStateOf<ParsedTransaction?>(null) }
    var showSuccessOverlay by remember { mutableStateOf(false) }
    val haptic = androidx.compose.ui.platform.LocalHapticFeedback.current
    val context = LocalContext.current

    LaunchedEffect(showSuccessOverlay) {
        if (showSuccessOverlay) {
            haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress)
            kotlinx.coroutines.delay(2000L)
            showSuccessOverlay = false
        }
    }

    val speechRecognizer = remember {
        if (SpeechRecognizer.isRecognitionAvailable(context)) {
            SpeechRecognizer.createSpeechRecognizer(context).apply {
                setRecognitionListener(object : RecognitionListener {
                    override fun onReadyForSpeech(params: Bundle?) {}
                    override fun onBeginningOfSpeech() {}
                    override fun onRmsChanged(rmsdB: Float) {}
                    override fun onBufferReceived(buffer: ByteArray?) {}
                    override fun onEndOfSpeech() {
                        isRecording = false
                    }
                    override fun onError(error: Int) {
                        isRecording = false
                    }
                    override fun onResults(results: Bundle?) {
                        isRecording = false
                        val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                        if (!matches.isNullOrEmpty()) {
                            val text = matches[0]
                            inputText = text
                        }
                    }
                    override fun onPartialResults(partialResults: Bundle?) {
                        val matches = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                        if (!matches.isNullOrEmpty()) {
                            val text = matches[0]
                            inputText = text
                        }
                    }
                    override fun onEvent(eventType: Int, params: Bundle?) {}
                })
            }
        } else {
            null
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            speechRecognizer?.destroy()
        }
    }

    val requestPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        if (isGranted) {
            isRecording = true
            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            }
            speechRecognizer?.startListening(intent)
        } else {
            Toast.makeText(context, "Microphone permission required", Toast.LENGTH_SHORT).show()
        }
    }

    fun toggleRecording() {
        if (speechRecognizer == null) {
            Toast.makeText(context, "Speech Recognition not available on this device", Toast.LENGTH_SHORT).show()
            return
        }
        if (isRecording) {
            isRecording = false
            speechRecognizer.stopListening()
        } else {
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
                isRecording = true
                val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                    putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                }
                speechRecognizer.startListening(intent)
            } else {
                requestPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
            }
        }
    }

    fun handleSend() {
        if (inputText.isBlank() || isParsing) return
        val currentInput = inputText
        inputText = ""
        isParsing = true
        scope.launch {
            if (currentInput.contains("?") || currentInput.contains("kitna", ignoreCase=true) || currentInput.contains("balance", ignoreCase=true)) {
                viewModel.executeUserQuery(currentInput)
                isParsing = false
            } else {
                val parsedResult = parseTransactionIntent(currentInput)
                isParsing = false
                if (parsedResult != null) {
                    Log.d("GeminiParser", "Parsed JSON: $parsedResult")
                    parsedTransactionForConfirmation = parsedResult
                } else {
                    viewModel.executeUserQuery(currentInput)
                }
            }
        }
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet(
                drawerContainerColor = ColorSurface,
                modifier = Modifier.width(300.dp)
            ) {
                DrawerContent()
            }
        }
    ) {
        Scaffold(
            containerColor = ColorBackground,
            topBar = {
                CenterAlignedTopAppBar(
                    colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                        containerColor = ColorBackground,
                        titleContentColor = ColorTextPrimary,
                        actionIconContentColor = ColorTextPrimary,
                        navigationIconContentColor = ColorTextPrimary
                    ),
                    title = {
                        Text(
                            text = "Transactions",
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 18.sp
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = { scope.launch { drawerState.open() } }) {
                            Icon(Icons.Default.Menu, contentDescription = "Menu")
                        }
                    },
                    actions = {
                        Box(
                            modifier = Modifier
                                .padding(end = 16.dp)
                                .size(32.dp)
                                .clip(CircleShape)
                                .background(ColorPrimaryAccent.copy(alpha = 0.1f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("JD", color = ColorPrimaryAccent, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        }
                    }
                )
            }
        ) { innerPadding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                if (transactions.isEmpty()) {
                    EmptyStateView(
                        modifier = Modifier.align(Alignment.Center),
                        onSeed = { viewModel.seedSampleData() }
                    )
                } else {
                    GroupedTransactionList(
                        transactions = transactions,
                        onDelete = { viewModel.deleteTransaction(it) }
                    )
                }
                
                // Floating AI-first chat input bar
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 12.dp)
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        androidx.compose.animation.AnimatedVisibility(
                            visible = aiChatResponse != null,
                            enter = androidx.compose.animation.fadeIn() + androidx.compose.animation.slideInVertically { it / 2 },
                            exit = androidx.compose.animation.fadeOut() + androidx.compose.animation.slideOutVertically { it / 2 }
                        ) {
                            Box(
                                modifier = Modifier
                                    .padding(horizontal = 16.dp, vertical = 8.dp)
                                    .background(Color(0xFF202123), RoundedCornerShape(16.dp))
                                    .padding(16.dp)
                            ) {
                                Column {
                                    Row(
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Text(
                                            text = "✨ AI Ledger Assistant",
                                            color = Color.White,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 14.sp
                                        )
                                        IconButton(
                                            onClick = { viewModel.dismissChatBubble() },
                                            modifier = Modifier.size(24.dp)
                                        ) {
                                            Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.LightGray)
                                        }
                                    }
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Text(
                                        text = aiChatResponse ?: "",
                                        color = Color.White.copy(alpha = 0.9f),
                                        fontSize = 15.sp,
                                        lineHeight = 22.sp
                                    )
                                }
                            }
                        }

                        ChatInputBar(
                        inputText = inputText,
                        onInputTextChanged = { inputText = it },
                        isRecording = isRecording,
                        onPlusClicked = { 
                            parsedTransactionForConfirmation = ParsedTransaction("", 0.0, "DEBIT", "")
                        },
                        onInputAreaClicked = { Toast.makeText(context, "Chat Menu / Keyboard Mode Mock", Toast.LENGTH_SHORT).show() },
                        onMicClicked = { toggleRecording() },
                        onSendClicked = { handleSend() },
                        isParsing = isParsing
                    )
                    }

                    androidx.compose.animation.AnimatedVisibility(
                        visible = showSuccessOverlay,
                        enter = androidx.compose.animation.fadeIn() + androidx.compose.animation.scaleIn(),
                        exit = androidx.compose.animation.fadeOut() + androidx.compose.animation.scaleOut(),
                        modifier = Modifier.align(Alignment.Center)
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(0.9f)
                                .height(56.dp)
                                .background(Color(0xFF10A37F), RoundedCornerShape(28.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.CheckCircle,
                                    contentDescription = "Success",
                                    tint = Color.White
                                )
                                Text(
                                    text = "Transaction Saved",
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 16.sp
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    if (parsedTransactionForConfirmation != null) {
        TransactionConfirmationSheet(
            parsedTransaction = parsedTransactionForConfirmation!!,
            onDismiss = { parsedTransactionForConfirmation = null },
            onCheckDuplicate = { partyName, amount, type ->
                viewModel.checkDuplicate(partyName, amount, type)
            },
            onConfirm = { partyName, amount, type, particular ->
                viewModel.addTransaction(
                    partyName = partyName,
                    amount = amount,
                    type = type,
                    particular = particular,
                    rawTranscript = parsedTransactionForConfirmation!!.rawTranscript
                )
                parsedTransactionForConfirmation = null
                inputText = ""
                showSuccessOverlay = true
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TransactionConfirmationSheet(
    parsedTransaction: ParsedTransaction,
    onDismiss: () -> Unit,
    onCheckDuplicate: suspend (partyName: String, amount: Double, transactionType: String) -> Boolean,
    onConfirm: (partyName: String, amount: Double, transactionType: String, particular: String) -> Unit
) {
    var duplicateWarning by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val haptic = androidx.compose.ui.platform.LocalHapticFeedback.current
    var showExitConfirmation by remember { mutableStateOf(false) }
    var canDismiss by remember { mutableStateOf(false) }

    val sheetState = rememberModalBottomSheetState(
        skipPartiallyExpanded = true,
        confirmValueChange = {
            if (it == SheetValue.Hidden && !canDismiss) {
                showExitConfirmation = true
                false
            } else {
                true
            }
        }
    )

    if (showExitConfirmation) {
        AlertDialog(
            onDismissRequest = { showExitConfirmation = false },
            title = { Text("Discard entered data?", color = ColorTextPrimary, fontWeight = FontWeight.Bold) },
            text = { Text("Are you sure you want to discard this transaction? Your changes will be lost.", color = ColorTextSecondary) },
            confirmButton = {
                TextButton(
                    onClick = {
                        showExitConfirmation = false
                        canDismiss = true
                        onDismiss()
                    }
                ) {
                    Text("Discard", color = ColorExpenseAccent, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showExitConfirmation = false }
                ) {
                    Text("Cancel", color = ColorTextSecondary)
                }
            },
            containerColor = ColorSurface,
            shape = RoundedCornerShape(16.dp)
        )
    }

    ModalBottomSheet(
        sheetState = sheetState,
        onDismissRequest = {
            // This is called when dismissal is requested
            showExitConfirmation = true
        },
        containerColor = ColorSurface,
        scrimColor = Color.Black.copy(alpha = 0.5f),
        dragHandle = { BottomSheetDefaults.DragHandle() }
    ) {
        var partyNameState by remember(parsedTransaction) { mutableStateOf(parsedTransaction.partyName) }
        var amountState by remember(parsedTransaction) { mutableStateOf(if (parsedTransaction.amount > 0) parsedTransaction.amount.toString() else "") }
        var typeState by remember(parsedTransaction) { mutableStateOf(parsedTransaction.transactionType) }
        var particularState by remember(parsedTransaction) { mutableStateOf("") }

        // Determine dynamic colored style based on typeState
        val isCredit = typeState.equals("CREDIT", ignoreCase = true)
        val amountColor = if (isCredit) ColorIncomeAccent else ColorExpenseAccent

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 24.dp, vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = "Confirm AI Transcript",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = ColorTextPrimary
            )
            
            if (parsedTransaction.rawTranscript.isNotEmpty()) {
                Text(
                    text = "\"${parsedTransaction.rawTranscript}\"",
                    style = MaterialTheme.typography.bodyMedium,
                    color = ColorTextSecondary,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(ColorBackground, shape = RoundedCornerShape(12.dp))
                        .padding(12.dp)
                )
            }

            // Editable Party Name
            Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = "PARTY NAME",
                    style = MaterialTheme.typography.labelSmall,
                    color = ColorTextSecondary,
                    fontWeight = FontWeight.Bold
                )
                OutlinedTextField(
                    value = partyNameState,
                    onValueChange = { partyNameState = it },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = ColorTextPrimary,
                        unfocusedTextColor = ColorTextPrimary,
                        focusedBorderColor = ColorPrimaryAccent,
                        unfocusedBorderColor = ColorTextSecondary.copy(alpha = 0.3f),
                        focusedContainerColor = ColorBackground,
                        unfocusedContainerColor = ColorBackground
                    ),
                    singleLine = true
                )
            }

            // Editable Amount
            Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = "AMOUNT (₹)",
                    style = MaterialTheme.typography.labelSmall,
                    color = ColorTextSecondary,
                    fontWeight = FontWeight.Bold
                )
                OutlinedTextField(
                    value = amountState,
                    onValueChange = { amountState = it },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = androidx.compose.ui.text.input.ImeAction.Next),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = amountColor,
                        unfocusedTextColor = amountColor,
                        focusedBorderColor = ColorPrimaryAccent,
                        unfocusedBorderColor = ColorTextSecondary.copy(alpha = 0.3f),
                        focusedContainerColor = ColorBackground,
                        unfocusedContainerColor = ColorBackground
                    ),
                    textStyle = MaterialTheme.typography.bodyLarge.copy(
                        fontWeight = FontWeight.Bold,
                        fontSize = 20.sp
                    ),
                    singleLine = true
                )
            }

            // Editable Particular
            Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = "PARTICULAR (OPTIONAL)",
                    style = MaterialTheme.typography.labelSmall,
                    color = ColorTextSecondary,
                    fontWeight = FontWeight.Bold
                )
                OutlinedTextField(
                    value = particularState,
                    onValueChange = { particularState = it },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    keyboardOptions = KeyboardOptions(imeAction = androidx.compose.ui.text.input.ImeAction.Done),
                    keyboardActions = androidx.compose.foundation.text.KeyboardActions(
                        onDone = {
                            val finalAmount = amountState.toDoubleOrNull() ?: 0.0
                            if (duplicateWarning) {
                                onConfirm(partyNameState, finalAmount, typeState, particularState)
                            } else {
                                scope.launch {
                                    val isDuplicate = onCheckDuplicate(partyNameState, finalAmount, typeState)
                                    if (isDuplicate) {
                                        duplicateWarning = true
                                        haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress)
                                    } else {
                                        onConfirm(partyNameState, finalAmount, typeState, particularState)
                                    }
                                }
                            }
                        }
                    ),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = ColorTextPrimary,
                        unfocusedTextColor = ColorTextPrimary,
                        focusedBorderColor = ColorPrimaryAccent,
                        unfocusedBorderColor = ColorTextSecondary.copy(alpha = 0.3f),
                        focusedContainerColor = ColorBackground,
                        unfocusedContainerColor = ColorBackground
                    ),
                    singleLine = true
                )
            }

            // Editable Type Segment
            Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = "TRANSACTION TYPE",
                    style = MaterialTheme.typography.labelSmall,
                    color = ColorTextSecondary,
                    fontWeight = FontWeight.Bold
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // DEBIT Button
                    Button(
                        onClick = { typeState = "DEBIT" },
                        modifier = Modifier.weight(1.5f),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (!isCredit) ColorExpenseAccent else ColorBackground,
                            contentColor = if (!isCredit) Color.White else ColorTextSecondary
                        ),
                        elevation = ButtonDefaults.buttonElevation(defaultElevation = if (!isCredit) 2.dp else 0.dp)
                    ) {
                        Text(
                            text = "DEBIT (EXPENSE)",
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 13.sp
                        )
                    }

                    // CREDIT Button
                    Button(
                        onClick = { typeState = "CREDIT" },
                        modifier = Modifier.weight(1.5f),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (isCredit) ColorIncomeAccent else ColorBackground,
                            contentColor = if (isCredit) Color.White else ColorTextSecondary
                        ),
                        elevation = ButtonDefaults.buttonElevation(defaultElevation = if (isCredit) 2.dp else 0.dp)
                    ) {
                        Text(
                            text = "CREDIT (INCOME)",
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 13.sp
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            if (duplicateWarning) {
                Text(
                    text = "You just recorded this exact transaction moments ago. Is this a duplicate?",
                    color = ColorExpenseAccent,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp)
                )
                Spacer(modifier = Modifier.height(8.dp))
            }

            // Action row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(
                    onClick = { showExitConfirmation = true },
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        text = if (duplicateWarning) "Yes, Discard" else "Discard",
                        color = ColorExpenseAccent,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 16.sp
                    )
                }

                Button(
                    onClick = {
                        val finalAmount = amountState.toDoubleOrNull() ?: 0.0
                        if (duplicateWarning) {
                            canDismiss = true
                            onConfirm(partyNameState, finalAmount, typeState, particularState)
                        } else {
                            scope.launch {
                                val isDuplicate = onCheckDuplicate(partyNameState, finalAmount, typeState)
                                if (isDuplicate) {
                                    duplicateWarning = true
                                    haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress)
                                } else {
                                    canDismiss = true
                                    onConfirm(partyNameState, finalAmount, typeState, particularState)
                                }
                            }
                        }
                    },
                    modifier = Modifier
                        .weight(1.5f)
                        .height(48.dp),
                    shape = RoundedCornerShape(24.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (duplicateWarning) ColorTextSecondary else ColorPrimaryAccent,
                        contentColor = Color.White
                    )
                ) {
                    Text(
                        text = if (duplicateWarning) "No, Log Anyway" else "Confirm & Log",
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                }
            }
        }
    }
}

@Composable
fun DrawerContent() {
    Column(modifier = Modifier.fillMaxSize()) {
        // Profile Header
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(ColorBackground)
                .padding(24.dp)
        ) {
            Column {
                Box(
                    modifier = Modifier
                        .size(56.dp)
                        .clip(CircleShape)
                        .background(ColorPrimaryAccent.copy(alpha = 0.1f)),
                    contentAlignment = Alignment.Center
                ) {
                    Text("JD", color = ColorPrimaryAccent, fontWeight = FontWeight.Bold, fontSize = 20.sp)
                }
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "John Doe",
                    color = ColorTextPrimary,
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp
                )
                Text(
                    text = "john.doe@example.com",
                    color = ColorTextSecondary,
                    fontSize = 14.sp
                )
            }
        }
        
        HorizontalDivider(color = ColorBackground, thickness = 1.dp)

        val menuItems = listOf("Dashboard", "Transaction History", "Analytics", "Budgets", "Settings")
        Spacer(modifier = Modifier.height(16.dp))
        menuItems.forEach { item ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { /* No-op for mock navigation */ }
                    .padding(horizontal = 24.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = item,
                    color = ColorTextPrimary,
                    fontWeight = FontWeight.Medium,
                    fontSize = 16.sp
                )
            }
        }
    }
}

@Composable
fun GroupedTransactionList(
    transactions: List<Transaction>,
    onDelete: (Int) -> Unit
) {
    // Group transactions by simple date string
    val grouped = transactions.groupBy { tx ->
        val sdf = java.text.SimpleDateFormat("MMM dd, yyyy", java.util.Locale.getDefault())
        val dateStr = sdf.format(java.util.Date(tx.timestamp))
        
        when {
            isSameDay(tx.timestamp, System.currentTimeMillis()) -> "Today"
            isSameDay(tx.timestamp, System.currentTimeMillis() - 86400000) -> "Yesterday"
            else -> dateStr
        }
    }

    LazyColumn(
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 100.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        modifier = Modifier.fillMaxSize()
    ) {
        grouped.forEach { (header, items) ->
            item {
                Text(
                    text = header,
                    color = ColorTextSecondary,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.padding(start = 4.dp, bottom = 4.dp, top = 8.dp)
                )
            }
            items(items, key = { it.id }) { item ->
                TransactionCard(transaction = item, onDelete = { onDelete(item.id) })
            }
        }
    }
}

@Composable
fun TransactionCard(transaction: Transaction, onDelete: () -> Unit) {
    val isCredit = transaction.transactionType.equals("CREDIT", ignoreCase = true)
    val amountColor = if (isCredit) ColorIncomeAccent else ColorTextPrimary
    val prefix = if (isCredit) "+" else "-"

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(elevation = 2.dp, shape = RoundedCornerShape(20.dp), spotColor = ColorTextSecondary.copy(alpha = 0.5f))
            .background(ColorSurface, shape = RoundedCornerShape(20.dp))
            .clickable { onDelete() }
            .padding(horizontal = 16.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.weight(1f)
        ) {
            // Icon
            Box(
                modifier = Modifier
                    .size(46.dp)
                    .clip(CircleShape)
                    .background(ColorBackground),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = if (isCredit) "+" else "-",
                    color = if (isCredit) ColorIncomeAccent else ColorExpenseAccent,
                    fontWeight = FontWeight.Bold,
                    fontSize = 24.sp
                )
            }
            
            Column {
                Text(
                    text = transaction.partyName,
                    color = ColorTextPrimary,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 16.sp
                )
                Spacer(modifier = Modifier.height(2.dp))
                if (!transaction.particular.isNullOrBlank()) {
                    Text(
                        text = transaction.particular,
                        color = ColorTextSecondary,
                        fontSize = 13.sp
                    )
                }
            }
        }
        
        Text(
            text = "$prefix \u20B9${String.format("%.2f", transaction.amount)}",
            color = amountColor,
            fontWeight = FontWeight.Bold,
            fontSize = 16.sp
        )
    }
}

@Composable
fun ChatInputBar(
    modifier: Modifier = Modifier,
    inputText: String,
    onInputTextChanged: (String) -> Unit,
    isRecording: Boolean,
    onPlusClicked: () -> Unit,
    onInputAreaClicked: () -> Unit,
    onMicClicked: () -> Unit,
    onSendClicked: () -> Unit,
    isParsing: Boolean
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Component A: The "+" Button
        Box(
            modifier = Modifier
                .size(46.dp)
                .clip(CircleShape)
                .background(Color(0xFF202123))
                .clickable { onPlusClicked() },
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.Add,
                contentDescription = "Manual Entry",
                tint = Color.White,
                modifier = Modifier.size(24.dp)
            )
        }

        // Component B: The Input Pill
        Row(
            modifier = Modifier
                .weight(1f)
                .height(48.dp)
                .clip(RoundedCornerShape(26.dp))
                .background(Color(0xFF2F3136))
                .padding(start = 16.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Text Area
            Box(modifier = Modifier
                .weight(1f)
                .clickable { onInputAreaClicked() }) {
                if (inputText.isEmpty()) {
                    Text(
                        text = "Add transaction...",
                        color = Color(0xFF8E8EA0),
                        fontSize = 16.sp,
                        maxLines = 1,
                        modifier = Modifier.align(Alignment.CenterStart)
                    )
                }
                androidx.compose.foundation.text.BasicTextField(
                    value = inputText,
                    onValueChange = onInputTextChanged,
                    textStyle = androidx.compose.ui.text.TextStyle(
                        color = Color.White,
                        fontSize = 16.sp
                    ),
                    modifier = Modifier.fillMaxWidth().align(Alignment.CenterStart),
                    singleLine = true,
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                        imeAction = androidx.compose.ui.text.input.ImeAction.Send
                    ),
                    keyboardActions = androidx.compose.foundation.text.KeyboardActions(
                        onSend = { onSendClicked() }
                    )
                )
            }

            // Mic Icon
            if (!isParsing && inputText.isEmpty()) {
                IconButton(
                    onClick = onMicClicked,
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        imageVector = MicIcon,
                        contentDescription = "Mic",
                        tint = if (isRecording) Color.Red else Color(0xFF8E8EA0),
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            if (isParsing) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .padding(8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(
                        color = Color(0xFF10A37F),
                        strokeWidth = 2.dp,
                        modifier = Modifier.size(24.dp)
                    )
                }
            } else if (inputText.isNotEmpty()) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(Color(0xFF10A37F)) // Vivid green
                        .clickable { onSendClicked() },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Send,
                        contentDescription = "Send",
                        tint = Color.White,
                        modifier = Modifier.size(20.dp)
                    )
                }
            } else {
                // Voice/Wave Button
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(Color(0xFF0F84F8)) // Vivid blue
                        .clickable { /* TBD Wave feature */ },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = WaveformIcon,
                        contentDescription = "Voice Mode",
                        tint = Color.White,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }
    }
}

val MicIcon: androidx.compose.ui.graphics.vector.ImageVector
    get() = androidx.compose.ui.graphics.vector.ImageVector.Builder(
        name = "Mic",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f
    ).apply {
        path(fill = SolidColor(Color.White)) {
            moveTo(12f, 14f)
            curveTo(13.66f, 14f, 15f, 12.66f, 15f, 11f)
            lineTo(15f, 5f)
            curveTo(15f, 3.34f, 13.66f, 2f, 12f, 2f)
            curveTo(10.34f, 2f, 9f, 3.34f, 9f, 5f)
            lineTo(9f, 11f)
            curveTo(9f, 12.66f, 10.34f, 14f, 12f, 14f)
            close()
            moveTo(17f, 11f)
            curveTo(17f, 13.76f, 14.76f, 16f, 12f, 16f)
            curveTo(9.24f, 16f, 7f, 13.76f, 7f, 11f)
            lineTo(5f, 11f)
            curveTo(5f, 14.53f, 7.61f, 17.43f, 11f, 17.92f)
            lineTo(11f, 21f)
            lineTo(13f, 21f)
            lineTo(13f, 17.92f)
            curveTo(16.39f, 17.43f, 19f, 14.53f, 19f, 11f)
            lineTo(17f, 11f)
            close()
        }
    }.build()

val WaveformIcon: androidx.compose.ui.graphics.vector.ImageVector
    get() = androidx.compose.ui.graphics.vector.ImageVector.Builder(
        name = "Waveform",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f
    ).apply {
        path(fill = SolidColor(Color.White)) {
            moveTo(6f, 10f)
            lineTo(8f, 10f)
            lineTo(8f, 14f)
            lineTo(6f, 14f)
            close()
            moveTo(10f, 6f)
            lineTo(12f, 6f)
            lineTo(12f, 18f)
            lineTo(10f, 18f)
            close()
            moveTo(14f, 8f)
            lineTo(16f, 8f)
            lineTo(16f, 16f)
            lineTo(14f, 16f)
            close()
            moveTo(18f, 11f)
            lineTo(20f, 11f)
            lineTo(20f, 13f)
            lineTo(18f, 13f)
            close()
        }
    }.build()

@Composable
fun EmptyStateView(modifier: Modifier = Modifier, onSeed: () -> Unit) {
    Column(
        modifier = modifier.padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            imageVector = Icons.Default.Info,
            contentDescription = null,
            tint = ColorTextSecondary.copy(alpha = 0.3f),
            modifier = Modifier.size(64.dp)
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "No Transactions Found",
            color = ColorTextPrimary,
            fontWeight = FontWeight.SemiBold,
            fontSize = 18.sp
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "Start by adding a new transaction or seed test data to see how it looks.",
            color = ColorTextSecondary,
            fontSize = 14.sp,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )
        Spacer(modifier = Modifier.height(24.dp))
        Button(
            onClick = onSeed,
            colors = ButtonDefaults.buttonColors(containerColor = ColorBackground, contentColor = ColorTextPrimary),
            elevation = ButtonDefaults.buttonElevation(defaultElevation = 2.dp)
        ) {
            Text("Seed Test Data")
        }
    }
}

// Helpers
fun isSameDay(time1: Long, time2: Long): Boolean {
    val cal1 = Calendar.getInstance().apply { timeInMillis = time1 }
    val cal2 = Calendar.getInstance().apply { timeInMillis = time2 }
    return cal1.get(Calendar.YEAR) == cal2.get(Calendar.YEAR) &&
           cal1.get(Calendar.DAY_OF_YEAR) == cal2.get(Calendar.DAY_OF_YEAR)
}
