package com.example.ui.screens

import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.ui.theme.appColors
import com.example.ui.theme.AppTypography
import com.example.ui.theme.AppShapes
import com.example.ui.viewmodel.SettingsViewModel
import kotlinx.coroutines.launch
import org.json.JSONArray

data class CurrencyData(
    val code: String,
    val name: String,
    val symbol: String,
    val country: String,
    val flag: String
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CurrencyPickerScreen(
    viewModel: SettingsViewModel,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val currentCurrencyCode by viewModel.currencyCode.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    val allCurrencies = remember { loadCurrencies(context) }
    var searchQuery by remember { mutableStateOf("") }

    val mostUsedCodes = listOf("INR", "USD", "EUR", "GBP", "AED", "SGD", "AUD", "CAD")
    val mostUsed = remember(allCurrencies) {
        mostUsedCodes.mapNotNull { code -> allCurrencies.find { it.code == code } }
    }

    val filteredCurrencies = remember(searchQuery, allCurrencies) {
        if (searchQuery.isBlank()) {
            allCurrencies
        } else {
            val q = searchQuery.lowercase()
            allCurrencies.filter {
                it.code.lowercase().contains(q) ||
                it.name.lowercase().contains(q) ||
                it.country.lowercase().contains(q)
            }
        }
    }

    fun selectCurrency(currency: CurrencyData) {
        viewModel.setCurrency(currency.code, currency.symbol)
        scope.launch {
            snackbarHostState.showSnackbar("Currency updated to ${currency.code}")
        }
    }

    Scaffold(
        topBar = {
            Column {
                CenterAlignedTopAppBar(
                    title = { Text("Change Currency", style = AppTypography.headline3) },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                        }
                    },
                    colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                        containerColor = MaterialTheme.appColors.bgBase,
                        titleContentColor = MaterialTheme.appColors.textPrimary,
                        navigationIconContentColor = MaterialTheme.appColors.textPrimary
                    )
                )
                // Search Bar
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                        .clip(AppShapes.full)
                        .background(MaterialTheme.appColors.bgSurface2)
                        .padding(horizontal = 16.dp, vertical = 12.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Default.Search,
                            contentDescription = "Search",
                            tint = MaterialTheme.appColors.textTertiary,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Box(modifier = Modifier.weight(1f)) {
                            if (searchQuery.isEmpty()) {
                                Text(
                                    "Search currency or country...",
                                    style = AppTypography.bodyRegular,
                                    color = MaterialTheme.appColors.textTertiary
                                )
                            }
                            BasicTextField(
                                value = searchQuery,
                                onValueChange = { searchQuery = it },
                                textStyle = AppTypography.bodyRegular.copy(color = MaterialTheme.appColors.textPrimary),
                                cursorBrush = SolidColor(MaterialTheme.appColors.primary),
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true
                            )
                        }
                        if (searchQuery.isNotEmpty()) {
                            Icon(
                                Icons.Default.Close,
                                contentDescription = "Clear",
                                tint = MaterialTheme.appColors.textSecondary,
                                modifier = Modifier
                                    .size(20.dp)
                                    .clickable { searchQuery = "" }
                            )
                        }
                    }
                }
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = MaterialTheme.appColors.bgBase
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            if (searchQuery.isBlank() && mostUsed.isNotEmpty()) {
                item {
                    Column(modifier = Modifier.padding(vertical = 16.dp)) {
                        Text(
                            text = "MOST USED",
                            style = AppTypography.caption,
                            color = MaterialTheme.appColors.textTertiary,
                            modifier = Modifier.padding(horizontal = 16.dp).padding(bottom = 12.dp)
                        )
                        LazyRow(
                            contentPadding = PaddingValues(horizontal = 16.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            items(mostUsed) { currency ->
                                val isSelected = currency.code == currentCurrencyCode
                                val bgColor = if (isSelected) MaterialTheme.appColors.primary.copy(alpha = 0.2f) else MaterialTheme.appColors.bgSurface2
                                val contentColor = if (isSelected) MaterialTheme.appColors.primary else MaterialTheme.appColors.textPrimary

                                Row(
                                    modifier = Modifier
                                        .clip(CircleShape)
                                        .background(bgColor)
                                        .clickable { selectCurrency(currency) }
                                        .padding(horizontal = 16.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(currency.flag, fontSize = 16.sp)
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        currency.code,
                                        style = AppTypography.bodySmall.copy(fontWeight = FontWeight.Bold),
                                        color = contentColor
                                    )
                                }
                            }
                        }
                    }
                }
            }

            items(filteredCurrencies) { currency ->
                val isSelected = currency.code == currentCurrencyCode
                val rowBg = if (isSelected) MaterialTheme.appColors.primary.copy(alpha = 0.1f) else Color.Transparent

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp)
                        .background(rowBg)
                        .clickable { selectCurrency(currency) }
                        .padding(horizontal = 16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        modifier = Modifier.width(64.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(currency.flag, fontSize = 20.sp)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = currency.code,
                            style = AppTypography.bodyRegular.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.appColors.textPrimary,
                            fontSize = 15.sp
                        )
                    }

                    Spacer(modifier = Modifier.width(16.dp))

                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text(
                            text = currency.name,
                            style = AppTypography.bodySmall,
                            color = MaterialTheme.appColors.textSecondary,
                            fontSize = 14.sp,
                            maxLines = 1
                        )
                        Text(
                            text = currency.country,
                            style = AppTypography.caption,
                            color = MaterialTheme.appColors.textTertiary,
                            fontSize = 12.sp,
                            maxLines = 1
                        )
                    }

                    if (isSelected) {
                        Icon(
                            Icons.Default.Check,
                            contentDescription = "Selected",
                            tint = MaterialTheme.appColors.primary,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }
            }
        }
    }
}

private fun loadCurrencies(context: Context): List<CurrencyData> {
    try {
        val inputStream = context.assets.open("currencies.json")
        val size = inputStream.available()
        val buffer = ByteArray(size)
        inputStream.read(buffer)
        inputStream.close()
        val jsonString = String(buffer, Charsets.UTF_8)
        
        val jsonArray = JSONArray(jsonString)
        val list = mutableListOf<CurrencyData>()
        for (i in 0 until jsonArray.length()) {
            val obj = jsonArray.getJSONObject(i)
            list.add(
                CurrencyData(
                    code = obj.getString("code"),
                    name = obj.getString("name"),
                    symbol = obj.getString("symbol"),
                    country = obj.getString("country"),
                    flag = obj.getString("flag")
                )
            )
        }
        return list
    } catch (e: Exception) {
        e.printStackTrace()
        return emptyList()
    }
}
