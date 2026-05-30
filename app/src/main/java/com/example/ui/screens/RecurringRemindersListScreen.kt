package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.appColors
import com.example.ui.theme.AppShapes
import com.example.ui.theme.AppTypography

data class ReminderItem(
    val id: String,
    val label: String,
    val date: String,
    val isActive: Boolean
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecurringRemindersListScreen(
    onBack: () -> Unit
) {
    var showCreateSheet by remember { mutableStateOf(false) }

    // Mock data for the list
    var items by remember {
        mutableStateOf(
            listOf(
                ReminderItem("1", "Electricity bill", "Monthly, 1st - 9:00 AM", true),
                ReminderItem("2", "Internet bill", "Monthly, 15th - 10:00 AM", true),
                ReminderItem("3", "Credit card payment", "Monthly, 28th - 8:00 AM", false)
            )
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Recurring Reminders", style = AppTypography.display.copy(fontSize = 20.sp)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(imageVector = Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { showCreateSheet = true }) {
                        Icon(imageVector = Icons.Default.Add, contentDescription = "Add reminder")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.appColors.bgBase,
                    titleContentColor = MaterialTheme.appColors.textPrimary,
                    navigationIconContentColor = MaterialTheme.appColors.textPrimary,
                    actionIconContentColor = MaterialTheme.appColors.primary
                )
            )
        },
        containerColor = MaterialTheme.appColors.bgBase
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(top = 16.dp, bottom = 32.dp),
            contentPadding = PaddingValues(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            items(
                items = items,
                key = { it.id }
            ) { item ->
                ReminderListItem(
                    item = item,
                    onToggle = { isChecked ->
                        items = items.map { if (it.id == item.id) it.copy(isActive = isChecked) else it }
                    },
                    onDelete = {
                        items = items.filter { it.id != item.id }
                    }
                )
            }
            
            if (items.isEmpty()) {
                item {
                    Box(modifier = Modifier.fillParentMaxSize(), contentAlignment = Alignment.Center) {
                        Text(
                            text = "No active reminders",
                            style = AppTypography.bodyRegular,
                            color = MaterialTheme.appColors.textTertiary
                        )
                    }
                }
            }
        }
    }

    if (showCreateSheet) {
        CreateReminderSheet(onDismiss = { showCreateSheet = false })
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReminderListItem(
    item: ReminderItem,
    onToggle: (Boolean) -> Unit,
    onDelete: () -> Unit
) {
    val dismissState = rememberSwipeToDismissBoxState(
        confirmValueChange = {
            if (it == SwipeToDismissBoxValue.EndToStart) {
                onDelete()
                true
            } else {
                false
            }
        }
    )

    SwipeToDismissBox(
        state = dismissState,
        backgroundContent = {
            val color = if (dismissState.targetValue == SwipeToDismissBoxValue.EndToStart) {
                MaterialTheme.appColors.expense
            } else {
                Color.Transparent
            }
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(AppShapes.xl)
                    .background(color)
                    .padding(end = 24.dp),
                contentAlignment = Alignment.CenterEnd
            ) {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = "Delete",
                    tint = Color.White
                )
            }
        },
        enableDismissFromStartToEnd = false
    ) {
        Card(
            shape = AppShapes.xl,
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.appColors.bgSurface),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(MaterialTheme.appColors.primary.copy(alpha = 0.1f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.NotificationsActive,
                        contentDescription = null,
                        tint = MaterialTheme.appColors.primary,
                        modifier = Modifier.size(24.dp)
                    )
                }

                Spacer(modifier = Modifier.width(16.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = item.label,
                        color = MaterialTheme.appColors.textPrimary,
                        style = AppTypography.bodyLarge,
                        fontWeight = FontWeight.Medium
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = item.date,
                        color = MaterialTheme.appColors.textSecondary,
                        style = AppTypography.bodySmall
                    )
                }

                Switch(
                    checked = item.isActive,
                    onCheckedChange = onToggle,
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Color.White,
                        checkedTrackColor = MaterialTheme.appColors.primary,
                        uncheckedThumbColor = MaterialTheme.appColors.textTertiary,
                        uncheckedTrackColor = MaterialTheme.appColors.bgSurface,
                        uncheckedBorderColor = MaterialTheme.appColors.borderLight
                    )
                )
            }
        }
    }
}
