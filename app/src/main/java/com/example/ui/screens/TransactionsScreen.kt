package com.example.ui.screens

import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.Transaction
import com.example.ui.theme.appColors
import com.example.ui.theme.AppTypography
import com.example.ui.viewmodel.LedgerViewModel
import com.example.ui.viewmodel.SettingsViewModel
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TransactionsScreen(
    viewModel: LedgerViewModel,
    settingsViewModel: SettingsViewModel
) {
    val allTxs by viewModel.allTransactionsWithCategory.collectAsStateWithLifecycle()
    val currencySymbol by settingsViewModel.currencySymbol.collectAsStateWithLifecycle()
    val accounts by viewModel.allAccounts.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var searchQuery by remember { mutableStateOf("") }
    var isSearchExpanded by remember { mutableStateOf(false) }
    val focusRequester = remember { FocusRequester() }
    
    // Autofocus search when expanded
    LaunchedEffect(isSearchExpanded) {
        if (isSearchExpanded) {
            focusRequester.requestFocus()
        }
    }

    var selectedSegment by remember { mutableStateOf("All") }
    
    var showQuickAdd by remember { mutableStateOf(false) }
    var showAccountFilter by remember { mutableStateOf(false) }
    var showFilterSheet by remember { mutableStateOf(false) }
    var showMonthPicker by remember { mutableStateOf(false) }
    var transactionToView by remember { mutableStateOf<Transaction?>(null) }
    var transactionToEdit by remember { mutableStateOf<Transaction?>(null) }

    var selectedAccounts by remember { mutableStateOf<Set<String>>(emptySet()) }
    
    val cal = Calendar.getInstance()
    var currentYear by remember { mutableStateOf(cal.get(Calendar.YEAR)) }
    var currentMonth by remember { mutableStateOf(cal.get(Calendar.MONTH)) }

    var clickCount by remember { mutableStateOf(0) }
    var showTweaks by remember { mutableStateOf(false) }
    var isArchivedExpanded by remember { mutableStateOf(false) }

    val filteredList by remember(allTxs, searchQuery, selectedSegment, selectedAccounts, currentYear, currentMonth) {
        derivedStateOf {
            allTxs.filter { twc ->
                val tx = twc.transaction
                if (tx.isArchived) return@filter false
                
                val segMatch = when (selectedSegment) {
                    "Income" -> tx.type == "INCOME" || tx.type == "CREDIT"
                    "Expense" -> tx.type == "EXPENSE" || tx.type == "DEBIT"
                    else -> true
                }
                
                val searchMatch = if (searchQuery.isBlank()) true else {
                    tx.title.contains(searchQuery, ignoreCase = true) ||
                    tx.description?.contains(searchQuery, ignoreCase = true) == true ||
                    tx.tags.contains(searchQuery, ignoreCase = true)
                }

                val accMatch = if (selectedAccounts.isEmpty()) true else {
                    selectedAccounts.contains(tx.accountId) ||
                    (tx.toAccountId != null && selectedAccounts.contains(tx.toAccountId))
                }

                val calTx = Calendar.getInstance().apply { timeInMillis = tx.date }
                val monthMatch = calTx.get(Calendar.YEAR) == currentYear && calTx.get(Calendar.MONTH) == currentMonth

                segMatch && searchMatch && accMatch && monthMatch
            }.sortedByDescending { it.transaction.date }
        }
    }

    val filteredArchivedList by remember(allTxs, searchQuery, selectedSegment, selectedAccounts, currentYear, currentMonth) {
        derivedStateOf {
            allTxs.filter { twc ->
                val tx = twc.transaction
                if (!tx.isArchived) return@filter false
                
                val segMatch = when (selectedSegment) {
                    "Income" -> tx.type == "INCOME" || tx.type == "CREDIT"
                    "Expense" -> tx.type == "EXPENSE" || tx.type == "DEBIT"
                    else -> true
                }
                
                val searchMatch = if (searchQuery.isBlank()) true else {
                    tx.title.contains(searchQuery, ignoreCase = true) ||
                    tx.description?.contains(searchQuery, ignoreCase = true) == true ||
                    tx.tags.contains(searchQuery, ignoreCase = true)
                }

                val accMatch = if (selectedAccounts.isEmpty()) true else {
                    selectedAccounts.contains(tx.accountId) ||
                    (tx.toAccountId != null && selectedAccounts.contains(tx.toAccountId))
                }

                val calTx = Calendar.getInstance().apply { timeInMillis = tx.date }
                val monthMatch = calTx.get(Calendar.YEAR) == currentYear && calTx.get(Calendar.MONTH) == currentMonth

                segMatch && searchMatch && accMatch && monthMatch
            }.sortedByDescending { it.transaction.date }
        }
    }

    Scaffold(
        topBar = {
            Column(modifier = Modifier.background(MaterialTheme.appColors.bgBase).padding(top = 16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth().height(48.dp).padding(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val monthLabels = listOf("Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec")
                    val currentMonthStr = "${monthLabels[currentMonth]} $currentYear"
                    
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .clip(RoundedCornerShape(16.dp))
                            .background(MaterialTheme.appColors.primary.copy(alpha = 0.1f))
                            .clickable { showMonthPicker = true }
                            .padding(horizontal = 12.dp, vertical = 6.dp)
                            .pointerInput(Unit) {
                                detectTapGestures(
                                    onTap = { showMonthPicker = true },
                                    onPress = {
                                        clickCount++
                                        if (clickCount >= 5) {
                                            showTweaks = true
                                            clickCount = 0
                                        }
                                        delay(2000)
                                        clickCount = 0
                                    }
                                )
                            }
                    ) {
                        Text(currentMonthStr, style = AppTypography.bodySmall.copy(fontWeight = FontWeight.Bold), color = MaterialTheme.appColors.primary)
                        Icon(Icons.Default.ArrowDropDown, contentDescription = null, tint = MaterialTheme.appColors.primary)
                    }

                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.appColors.bgSurface)
                            .clickable { showFilterSheet = true },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.FilterList, contentDescription = "Filter", tint = MaterialTheme.appColors.textSecondary)
                    }
                }

                AnimatedVisibility(visible = isSearchExpanded) {
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        placeholder = { Text("Search title, note, or tag", style = AppTypography.bodySmall) },
                        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, modifier = Modifier.size(20.dp)) },
                        trailingIcon = {
                            if (searchQuery.isNotEmpty()) {
                                IconButton(onClick = { searchQuery = "" }) {
                                    Icon(Icons.Default.Close, contentDescription = null, modifier = Modifier.size(20.dp))
                                }
                            } else {
                                IconButton(onClick = { isSearchExpanded = false; searchQuery = "" }) {
                                    Icon(Icons.Default.Close, contentDescription = null, modifier = Modifier.size(20.dp))
                                }
                            }
                        },
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp).height(56.dp).focusRequester(focusRequester),
                        shape = RoundedCornerShape(10.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = MaterialTheme.appColors.surfaceSubtle,
                            unfocusedContainerColor = MaterialTheme.appColors.surfaceSubtle,
                            focusedBorderColor = Color.Transparent,
                            unfocusedBorderColor = Color.Transparent
                        )
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    listOf("All", "Income", "Expense").forEach { seg ->
                        val isSel = selectedSegment == seg
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(36.dp)
                                .clip(RoundedCornerShape(18.dp))
                                .background(if (isSel) MaterialTheme.appColors.bgSurface3 else MaterialTheme.appColors.bgSurface)
                                .clickable { selectedSegment = seg },
                            contentAlignment = Alignment.Center
                        ) {
                            Text(seg, style = AppTypography.caption, color = if (isSel) MaterialTheme.appColors.textPrimary else MaterialTheme.appColors.textSecondary)
                        }
                    }
                }

                if (selectedAccounts.isNotEmpty()) {
                     Row(modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
                         val firstAccId = selectedAccounts.first()
                         val firstAccName = accounts.find { it.id == firstAccId }?.name ?: "Account"
                         val label = if (selectedAccounts.size == 1) firstAccName else "$firstAccName + ${selectedAccounts.size - 1} more"
                         
                         Surface(
                             shape = RoundedCornerShape(12.dp),
                             color = MaterialTheme.appColors.surfaceSubtle,
                             onClick = { showAccountFilter = true }
                         ) {
                             Row(modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                                 Text(label, style = AppTypography.caption, color = MaterialTheme.appColors.textPrimary)
                                 Spacer(modifier = Modifier.width(4.dp))
                                 Icon(Icons.Default.Close, contentDescription = null, modifier = Modifier.size(16.dp).clickable { selectedAccounts = emptySet() }, tint = MaterialTheme.appColors.textSecondary)
                             }
                         }
                     }
                }

                Row(
                    modifier = Modifier.fillMaxWidth().height(36.dp).padding(horizontal = 16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Date ↓", style = AppTypography.caption, color = MaterialTheme.appColors.textTertiary, modifier = Modifier.weight(0.5f))
                    Text("Name · Notes", style = AppTypography.caption, color = MaterialTheme.appColors.textTertiary, modifier = Modifier.weight(1f))
                    Text("Amount", style = AppTypography.caption, color = MaterialTheme.appColors.textTertiary, modifier = Modifier.weight(0.6f), textAlign = TextAlign.End)
                }
            }
        },
        floatingActionButton = {
            FabMenu(
                onAddClick = { showQuickAdd = true },
                onAccountClick = { showAccountFilter = true },
                onSearchClick = { isSearchExpanded = true },
                onFilterClick = { showFilterSheet = true }
            )
        },
        containerColor = MaterialTheme.appColors.bgBase
    ) { paddingValues ->
        Box(modifier = Modifier.fillMaxSize().padding(paddingValues)) {
            if (filteredList.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(32.dp)) {
                        if (searchQuery.isNotEmpty()) {
                            Text("No matches for '$searchQuery'", style = AppTypography.bodyRegular, color = MaterialTheme.appColors.textSecondary)
                        } else if (allTxs.isEmpty()) {
                            Text("Add your first transaction", style = AppTypography.bodyRegular, color = MaterialTheme.appColors.textSecondary)
                            Spacer(modifier = Modifier.height(16.dp))
                            Button(onClick = { showQuickAdd = true }, colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.appColors.primary)) {
                                Text("Add Transaction")
                            }
                        } else {
                            Text("No transactions match", style = AppTypography.bodyRegular, color = MaterialTheme.appColors.textSecondary)
                            Spacer(modifier = Modifier.height(8.dp))
                            Text("Clear filters", style = AppTypography.bodySmall, color = MaterialTheme.appColors.primary, modifier = Modifier.clickable {
                                selectedSegment = "All"
                                selectedAccounts = emptySet()
                            }.padding(8.dp))
                        }
                    }
                }
            } else {
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    val grouped = filteredList.groupBy { 
                        val sdf = SimpleDateFormat("dd MMM yyyy", Locale.US)
                        sdf.format(Date(it.transaction.date))
                    }

                    grouped.forEach { (dateStr, groupTxs) ->
                        item {
                            val totalAmount = groupTxs.sumOf { twc ->
                                val t = twc.transaction
                                if (t.type == "INCOME" || t.type == "CREDIT") t.amount else if (t.type == "EXPENSE" || t.type == "DEBIT") -t.amount else 0.0
                            }
                            val color = if (totalAmount > 0) MaterialTheme.appColors.income else if (totalAmount < 0) MaterialTheme.appColors.expense else MaterialTheme.appColors.textSecondary
                            val sign = if (totalAmount > 0) "+" else if (totalAmount < 0) "-" else ""
                            
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(dateStr.uppercase(), style = AppTypography.caption.copy(fontSize = 12.sp), color = MaterialTheme.appColors.textSecondary)
                                if (totalAmount != 0.0) {
                                    Text("$sign$currencySymbol${String.format("%.2f", Math.abs(totalAmount))}", style = AppTypography.caption.copy(fontSize = 12.sp), color = color)
                                }
                            }
                        }

                        items(groupTxs, key = { it.transaction.id }) { twc ->
                            val tx = twc.transaction
                            val cat = twc.category
                            val accName = accounts.find { it.id == tx.accountId }?.name ?: "Acct"
                            
                            val dismissState = rememberSwipeToDismissBoxState(
                                confirmValueChange = { dismissValue ->
                                    if (dismissValue == SwipeToDismissBoxValue.EndToStart) {
                                        scope.launch {
                                            viewModel.deleteTransaction(tx.id)
                                            Toast.makeText(context, "Deleted", Toast.LENGTH_SHORT).show()
                                        }
                                        true
                                    } else if (dismissValue == SwipeToDismissBoxValue.StartToEnd) {
                                        scope.launch {
                                            viewModel.archiveTransaction(tx.id, true)
                                            Toast.makeText(context, "Archived", Toast.LENGTH_SHORT).show()
                                        }
                                        true
                                    } else false
                                },
                                positionalThreshold = { it * 0.3f }
                            )

                            SwipeToDismissBox(
                                state = dismissState,
                                backgroundContent = {
                                    val dir = dismissState.dismissDirection
                                    val bgCol = when(dir) {
                                        SwipeToDismissBoxValue.EndToStart -> MaterialTheme.appColors.swipeDeleteBg
                                        SwipeToDismissBoxValue.StartToEnd -> MaterialTheme.appColors.swipeArchiveBg
                                        else -> Color.Transparent
                                    }
                                    val align = when(dir) {
                                        SwipeToDismissBoxValue.EndToStart -> Alignment.CenterEnd
                                        SwipeToDismissBoxValue.StartToEnd -> Alignment.CenterStart
                                        else -> Alignment.Center
                                    }
                                    val icon = when(dir) {
                                        SwipeToDismissBoxValue.EndToStart -> Icons.Default.Delete
                                        SwipeToDismissBoxValue.StartToEnd -> Icons.Default.Archive
                                        else -> Icons.Default.Check
                                    }
                                    Box(
                                        modifier = Modifier.fillMaxSize().background(bgCol).padding(horizontal = 24.dp),
                                        contentAlignment = align
                                    ) {
                                        Icon(icon, contentDescription = null, tint = Color.White)
                                    }
                                },
                                enableDismissFromStartToEnd = true,
                                enableDismissFromEndToStart = true
                            ) {
                                Column(modifier = Modifier.background(MaterialTheme.appColors.bgBase)) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(64.dp)
                                            .background(MaterialTheme.appColors.bgSurface)
                                            .pointerInput(Unit) {
                                                detectTapGestures(
                                                    onLongPress = { transactionToView = tx },
                                                    onTap = { transactionToView = tx }
                                                )
                                            }
                                            .padding(horizontal = 16.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        val dayDf = SimpleDateFormat("dd/MM", Locale.US)
                                        val dayNameDf = SimpleDateFormat("EEE", Locale.US)
                                        val dateObj = Date(tx.date)
                                        Column(modifier = Modifier.width(48.dp)) {
                                            Text(dayDf.format(dateObj), style = AppTypography.bodySmall, color = MaterialTheme.appColors.textSecondary)
                                            Text(dayNameDf.format(dateObj), style = AppTypography.caption.copy(fontSize = 11.sp), color = MaterialTheme.appColors.textTertiary)
                                        }
                                        
                                        val cCol = Color(android.graphics.Color.parseColor(cat?.colorHex ?: "#999999"))
                                        Box(
                                            modifier = Modifier.size(40.dp).clip(CircleShape).background(cCol.copy(alpha=0.2f)),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            val rawIcon = cat?.icon ?: "💰"
                                            val displayIcon = if (rawIcon.any { it.isSurrogate() || it.code > 127 }) {
                                                rawIcon
                                            } else {
                                                when (rawIcon.lowercase()) {
                                                    "restaurant" -> "🍔"
                                                    "receipt_long" -> "🧾"
                                                    "shopping_bag" -> "🛍️"
                                                    "more_horiz" -> "⚙️"
                                                    else -> rawIcon.take(1)
                                                }
                                            }
                                            Text(displayIcon, fontSize = 20.sp)
                                        }
                                        Spacer(modifier = Modifier.width(12.dp))

                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(tx.title, style = AppTypography.bodyRegular, color = MaterialTheme.appColors.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Box(modifier = Modifier.clip(RoundedCornerShape(8.dp)).background(MaterialTheme.appColors.surfaceSubtle).padding(horizontal = 6.dp, vertical = 2.dp)) {
                                                    Text(accName, fontSize = 11.sp, color = MaterialTheme.appColors.textSecondary)
                                                }
                                                if (!tx.description.isNullOrBlank()) {
                                                    Spacer(modifier = Modifier.width(6.dp))
                                                    Text(tx.description, fontSize = 11.sp, color = MaterialTheme.appColors.textSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                                }
                                            }
                                        }

                                        val isIncome = tx.type == "INCOME" || tx.type == "CREDIT"
                                        val isExpense = tx.type == "EXPENSE" || tx.type == "DEBIT"
                                        val amtStr = if (isIncome) "+$currencySymbol${tx.amount}"
                                                     else if (isExpense) "-$currencySymbol${tx.amount}"
                                                     else "$currencySymbol${tx.amount}"
                                        val amtCol = if (isIncome) MaterialTheme.appColors.income
                                                     else if (isExpense) MaterialTheme.appColors.expense
                                                     else MaterialTheme.appColors.textPrimary

                                        Text(amtStr, style = AppTypography.bodyRegular.copy(fontWeight = FontWeight.Bold), color = amtCol)
                                    }
                                    Box(modifier = Modifier.fillMaxWidth().padding(start = 72.dp).height(1.dp).background(MaterialTheme.appColors.borderLight))
                                }
                            }
                        }
                    }
                    
                    if (filteredArchivedList.isNotEmpty()) {
                        item {
                            Column(modifier = Modifier.fillMaxWidth()) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(1.dp)
                                        .background(MaterialTheme.appColors.borderLight)
                                )
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(48.dp)
                                        .background(MaterialTheme.appColors.surfaceSubtle)
                                        .clickable { isArchivedExpanded = !isArchivedExpanded }
                                        .padding(horizontal = 16.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        "Archived (${filteredArchivedList.size}) ${if (isArchivedExpanded) "↑" else "↓"}",
                                        style = AppTypography.caption.copy(fontSize = 13.sp),
                                        color = MaterialTheme.appColors.textSecondary
                                    )
                                    Icon(
                                        if (isArchivedExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                                        contentDescription = null,
                                        tint = MaterialTheme.appColors.textSecondary,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                        }
                        
                        if (isArchivedExpanded) {
                            val groupedArchived = filteredArchivedList.groupBy { 
                                val sdf = SimpleDateFormat("dd MMM yyyy", Locale.US)
                                sdf.format(Date(it.transaction.date))
                            }

                            groupedArchived.forEach { (dateStr, groupTxs) ->
                                item {
                                    val totalAmount = groupTxs.sumOf { twc ->
                                        val t = twc.transaction
                                        if (t.type == "INCOME" || t.type == "CREDIT") t.amount else if (t.type == "EXPENSE" || t.type == "DEBIT") -t.amount else 0.0
                                    }
                                    val color = if (totalAmount > 0) MaterialTheme.appColors.income else if (totalAmount < 0) MaterialTheme.appColors.expense else MaterialTheme.appColors.textSecondary
                                    val sign = if (totalAmount > 0) "+" else if (totalAmount < 0) "-" else ""
                                    
                                    Row(
                                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(dateStr.uppercase(), style = AppTypography.caption.copy(fontSize = 12.sp), color = MaterialTheme.appColors.textTertiary)
                                        if (totalAmount != 0.0) {
                                            Text("$sign$currencySymbol${String.format("%.2f", Math.abs(totalAmount))}", style = AppTypography.caption.copy(fontSize = 12.sp), color = color.copy(alpha = 0.7f))
                                        }
                                    }
                                }

                                items(groupTxs, key = { "archived_${it.transaction.id}" }) { twc ->
                                    val tx = twc.transaction
                                    val cat = twc.category
                                    val accName = accounts.find { it.id == tx.accountId }?.name ?: "Acct"
                                    
                                    val dismissState = rememberSwipeToDismissBoxState(
                                        confirmValueChange = { dismissValue ->
                                            if (dismissValue == SwipeToDismissBoxValue.StartToEnd) {
                                                scope.launch {
                                                    viewModel.archiveTransaction(tx.id, false)
                                                }
                                                true
                                            } else false
                                        },
                                        positionalThreshold = { it * 0.3f }
                                    )

                                    SwipeToDismissBox(
                                        state = dismissState,
                                        backgroundContent = {
                                            if (dismissState.dismissDirection == SwipeToDismissBoxValue.StartToEnd) {
                                                Box(
                                                    modifier = Modifier.fillMaxSize().background(MaterialTheme.appColors.primary).padding(horizontal = 24.dp),
                                                    contentAlignment = Alignment.CenterStart
                                                ) {
                                                    Icon(Icons.Default.Unarchive, contentDescription = null, tint = Color.White)
                                                }
                                            }
                                        },
                                        enableDismissFromStartToEnd = true,
                                        enableDismissFromEndToStart = false
                                    ) {
                                        Column(modifier = Modifier.background(MaterialTheme.appColors.bgBase).padding(horizontal = 16.dp)) {
                                            Row(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .height(64.dp)
                                                    .background(MaterialTheme.appColors.bgSurface.copy(alpha = 0.7f))
                                                    .pointerInput(Unit) {
                                                        detectTapGestures(
                                                            onLongPress = { transactionToView = tx },
                                                            onTap = { transactionToView = tx }
                                                        )
                                                    }
                                                    .padding(horizontal = 16.dp),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                val dayDf = SimpleDateFormat("dd/MM", Locale.US)
                                                val dayNameDf = SimpleDateFormat("EEE", Locale.US)
                                                val dateObj = Date(tx.date)
                                                Column(modifier = Modifier.width(48.dp)) {
                                                    Text(dayDf.format(dateObj), style = AppTypography.bodySmall, color = MaterialTheme.appColors.textTertiary)
                                                    Text(dayNameDf.format(dateObj), style = AppTypography.caption.copy(fontSize = 11.sp), color = MaterialTheme.appColors.textTertiary)
                                                }
                                                
                                                val cCol = Color(android.graphics.Color.parseColor(cat?.colorHex ?: "#999999"))
                                                Box(
                                                    modifier = Modifier.size(40.dp).clip(CircleShape).background(cCol.copy(alpha=0.14f)),
                                                    contentAlignment = Alignment.Center
                                                ) {
                                                    val rawIcon = cat?.icon ?: "💰"
                                                    val displayIcon = if (rawIcon.any { it.isSurrogate() || it.code > 127 }) {
                                                        rawIcon
                                                    } else {
                                                        when (rawIcon.lowercase()) {
                                                            "restaurant" -> "🍔"
                                                            "receipt_long" -> "🧾"
                                                            "shopping_bag" -> "🛍️"
                                                            "more_horiz" -> "⚙️"
                                                            else -> rawIcon.take(1)
                                                        }
                                                    }
                                                    Text(displayIcon, fontSize = 20.sp, modifier = Modifier.alpha(0.7f))
                                                }
                                                Spacer(modifier = Modifier.width(12.dp))

                                                Column(modifier = Modifier.weight(1f)) {
                                                    Text(tx.title, style = AppTypography.bodyRegular, color = MaterialTheme.appColors.textTertiary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                                        Box(modifier = Modifier.clip(RoundedCornerShape(8.dp)).background(MaterialTheme.appColors.surfaceSubtle).padding(horizontal = 6.dp, vertical = 2.dp)) {
                                                            Text(accName, fontSize = 11.sp, color = MaterialTheme.appColors.textTertiary)
                                                        }
                                                        if (!tx.description.isNullOrBlank()) {
                                                            Spacer(modifier = Modifier.width(6.dp))
                                                            Text(tx.description, fontSize = 11.sp, color = MaterialTheme.appColors.textTertiary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                                        }
                                                    }
                                                }

                                                val isIncome = tx.type == "INCOME" || tx.type == "CREDIT"
                                                val isExpense = tx.type == "EXPENSE" || tx.type == "DEBIT"
                                                val amtStr = if (isIncome) "+$currencySymbol${tx.amount}"
                                                             else if (isExpense) "-$currencySymbol${tx.amount}"
                                                             else "$currencySymbol${tx.amount}"

                                                Text(amtStr, style = AppTypography.bodyRegular.copy(fontWeight = FontWeight.Bold), color = MaterialTheme.appColors.textTertiary)
                                            }
                                            Box(modifier = Modifier.fillMaxWidth().padding(start = 72.dp).height(1.dp).background(MaterialTheme.appColors.borderLight))
                                        }
                                    }
                                }
                            }
                        }
                    }
                    item { Spacer(modifier = Modifier.height(100.dp)) }
                }
            }
        }
    }

    if (showQuickAdd) {
        com.example.ui.components.TransactionDetailSheet(
            viewModel = viewModel,
            existingTransaction = transactionToEdit,
            onDismiss = { showQuickAdd = false; transactionToEdit = null }
        )
    }

    if (transactionToView != null) {
        com.example.ui.components.ReadOnlyTransactionSheet(
            transaction = transactionToView!!,
            viewModel = viewModel,
            onDismiss = { transactionToView = null },
            onEdit = {
                transactionToEdit = transactionToView
                transactionToView = null
                showQuickAdd = true
            },
            onDelete = {
                viewModel.deleteTransaction(transactionToView!!.id)
                transactionToView = null
            }
        )
    }

    if (showAccountFilter) {
        ModalBottomSheet(onDismissRequest = { showAccountFilter = false }, containerColor = MaterialTheme.appColors.bgBase) {
            Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                Text("Filter by Account", style = AppTypography.headline3, color = MaterialTheme.appColors.textPrimary)
                Spacer(modifier = Modifier.height(16.dp))
                LazyColumn {
                    item {
                        Row(modifier = Modifier.fillMaxWidth().clickable { selectedAccounts = emptySet() }.padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(checked = selectedAccounts.isEmpty(), onCheckedChange = { if(it) selectedAccounts = emptySet() })
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("All accounts", color = MaterialTheme.appColors.textPrimary)
                        }
                    }
                    items(accounts) { acc ->
                        if (!acc.isArchived) {
                            Row(modifier = Modifier.fillMaxWidth().clickable { 
                                val m = selectedAccounts.toMutableSet()
                                if (m.contains(acc.id)) m.remove(acc.id) else m.add(acc.id)
                                selectedAccounts = m
                            }.padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                                Checkbox(checked = selectedAccounts.contains(acc.id), onCheckedChange = {
                                    val m = selectedAccounts.toMutableSet()
                                    if (it) m.add(acc.id) else m.remove(acc.id)
                                    selectedAccounts = m
                                })
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(acc.name, color = MaterialTheme.appColors.textPrimary)
                            }
                        }
                    }
                }
                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }

    if (showFilterSheet) {
        ModalBottomSheet(onDismissRequest = { showFilterSheet = false }, containerColor = MaterialTheme.appColors.bgBase) {
            Column(modifier = Modifier.fillMaxWidth().padding(16.dp).weight(1f, false)) {
                 Text("Filters", style = AppTypography.headline3, color = MaterialTheme.appColors.textPrimary)
                 Spacer(modifier = Modifier.height(16.dp))
                 Text("Date Range", style = AppTypography.caption, color = MaterialTheme.appColors.textSecondary)
                 Row(modifier = Modifier.padding(vertical=8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                     listOf("Today", "Week", "Month", "Custom").forEach { c -> Box(modifier = Modifier.clip(RoundedCornerShape(12.dp)).background(MaterialTheme.appColors.surfaceSubtle).padding(horizontal = 12.dp, vertical = 6.dp)){Text(c, style=AppTypography.caption)} }
                 }
                 Spacer(modifier = Modifier.height(16.dp))
                 Text("Type", style = AppTypography.caption, color = MaterialTheme.appColors.textSecondary)
                 Row(modifier = Modifier.padding(vertical=8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                     listOf("Income", "Expense", "Transfer").forEach { c -> Box(modifier = Modifier.clip(RoundedCornerShape(12.dp)).background(MaterialTheme.appColors.surfaceSubtle).padding(horizontal = 12.dp, vertical = 6.dp)){Text(c, style=AppTypography.caption)} }
                 }
                 Spacer(modifier = Modifier.height(32.dp))
                 Button(onClick = { Toast.makeText(context, "Filters coming in next update", Toast.LENGTH_SHORT).show(); showFilterSheet = false }, modifier = Modifier.fillMaxWidth()) {
                     Text("Apply Filters")
                 }
                 Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }

    if (showMonthPicker) {
        ModalBottomSheet(onDismissRequest = { showMonthPicker = false }, containerColor = MaterialTheme.appColors.bgBase) {
            Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                Text("Select Month", style = AppTypography.headline3, color = MaterialTheme.appColors.textPrimary)
                Spacer(modifier = Modifier.height(24.dp))
                val monthLabels = listOf("Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec")
                var tmpYr by remember { mutableStateOf(currentYear) }
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick={ tmpYr-- }, modifier = Modifier.background(MaterialTheme.appColors.surfaceSubtle, CircleShape)) { Icon(Icons.Default.KeyboardArrowLeft, null) }
                    Text("$tmpYr", style=AppTypography.headline3)
                    IconButton(onClick={ tmpYr++ }, modifier = Modifier.background(MaterialTheme.appColors.surfaceSubtle, CircleShape)) { Icon(Icons.Default.KeyboardArrowRight, null) }
                }
                Spacer(modifier = Modifier.height(24.dp))
                Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    androidx.compose.foundation.lazy.grid.LazyVerticalGrid(
                        columns = androidx.compose.foundation.lazy.grid.GridCells.Fixed(4),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        items(monthLabels.size) { idx ->
                            val isSel = idx == currentMonth && tmpYr == currentYear
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(if(isSel) MaterialTheme.appColors.primary else Color.Transparent)
                                    .clickable { currentMonth = idx; currentYear = tmpYr; showMonthPicker = false }
                                    .padding(vertical = 12.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(monthLabels[idx], color = if(isSel) Color.White else MaterialTheme.appColors.textPrimary, style = AppTypography.bodyRegular)
                            }
                        }
                    }
                }
                Spacer(modifier = Modifier.height(32.dp))
            }
        }
    }

    if (showTweaks) {
        ModalBottomSheet(onDismissRequest = { showTweaks = false }) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("DEV TWEAKS", style = AppTypography.headline3)
                Spacer(modifier = Modifier.height(16.dp))
                Text("Visual overrides available here...")
            }
        }
    }
}

@Composable
fun FabMenu(
    onAddClick: () -> Unit,
    onAccountClick: () -> Unit,
    onSearchClick: () -> Unit,
    onFilterClick: () -> Unit
) {
    var isOpen by remember { mutableStateOf(false) }
    val currentAppColors = MaterialTheme.appColors
    
    val width by animateDpAsState(targetValue = if (isOpen) 260.dp else 56.dp, animationSpec = tween(220, easing = LinearOutSlowInEasing))
    val rot by animateFloatAsState(targetValue = if (isOpen) 45f else 0f, animationSpec = tween(220, easing = FastOutSlowInEasing))
    val pillBg = if (isOpen) currentAppColors.surfaceInverse else currentAppColors.primary

    Box(
        modifier = Modifier
            .padding(bottom = 16.dp)
            .width(width)
            .height(56.dp)
            .clip(RoundedCornerShape(28.dp))
            .background(pillBg),
        contentAlignment = Alignment.CenterEnd
    ) {
        if (isOpen) {
            Row(
                modifier = Modifier.fillMaxSize().padding(end = 56.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onAddClick, modifier = Modifier.size(44.dp)) { Icon(Icons.Default.Add, null, tint = Color.White) }
                IconButton(onClick = onAccountClick, modifier = Modifier.size(44.dp)) { Icon(Icons.Default.AccountBalanceWallet, null, tint = Color.White) }
                IconButton(onClick = onSearchClick, modifier = Modifier.size(44.dp)) { Icon(Icons.Default.Search, null, tint = Color.White) }
                IconButton(onClick = onFilterClick, modifier = Modifier.size(44.dp)) { Icon(Icons.Default.FilterList, null, tint = Color.White) }
            }
        }
        
        Box(
            modifier = Modifier
                .size(56.dp)
                .clip(CircleShape)
                .background(Color.Transparent)
                .clickable { isOpen = !isOpen },
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Default.Add, contentDescription = null, tint = Color.White, modifier = Modifier.rotate(rot))
        }
    }
}
