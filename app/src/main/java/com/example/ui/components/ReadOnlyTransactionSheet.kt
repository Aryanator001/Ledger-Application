package com.example.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import com.example.R
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.Transaction
import com.example.ui.theme.appColors
import com.example.ui.viewmodel.LedgerViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReadOnlyTransactionSheet(
    transaction: Transaction,
    viewModel: LedgerViewModel,
    onDismiss: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val accounts by viewModel.allAccounts.collectAsStateWithLifecycle()
    val categories by viewModel.allCategories.collectAsStateWithLifecycle()

    val account = accounts.find { it.id == transaction.accountId }
    val category = categories.find { it.id == transaction.categoryId }

    val sdfDate = SimpleDateFormat("dd MMM yyyy", Locale.US)
    val sdfTime = SimpleDateFormat("hh:mm a", Locale.US)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.appColors.bgSurface,
        shape = androidx.compose.foundation.shape.RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(bottom = 24.dp)
        ) {
            Text(
                text = category?.name?.uppercase() ?: transaction.type,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.appColors.textPrimary
            )
            Divider(modifier = Modifier.padding(vertical = 12.dp), color = MaterialTheme.appColors.borderLight)

            DetailRow(stringResource(R.string.readonly_sheet_amount), "₹${transaction.amount}")
            DetailRow(stringResource(R.string.readonly_sheet_type), transaction.type.lowercase().replaceFirstChar { it.uppercase() })
            DetailRow(stringResource(R.string.readonly_sheet_account), account?.name ?: stringResource(R.string.readonly_sheet_unknown))
            DetailRow(stringResource(R.string.readonly_sheet_date), sdfDate.format(Date(transaction.date)))
            DetailRow(stringResource(R.string.readonly_sheet_time), sdfTime.format(Date(transaction.date)))

            if (transaction.description != null) {
                DetailRow(stringResource(R.string.readonly_sheet_note), transaction.description)
            }
            if (transaction.tags.isNotEmpty()) {
                DetailRow(stringResource(R.string.readonly_sheet_tags), transaction.tags.split(",").joinToString(" ") { "[$it]" })
            }

            Divider(modifier = Modifier.padding(vertical = 12.dp), color = MaterialTheme.appColors.borderLight)

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                OutlinedButton(
                    onClick = onEdit,
                    modifier = Modifier.weight(1f).padding(end = 8.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.appColors.primary)
                ) {
                    Text(stringResource(R.string.readonly_sheet_edit))
                }
                Button(
                    onClick = onDelete,
                    modifier = Modifier.weight(1f).padding(start = 8.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.appColors.expense)
                ) {
                    Text(stringResource(R.string.readonly_sheet_delete), color = Color.White)
                }
            }
        }
    }
}

@Composable
fun DetailRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(text = label, color = MaterialTheme.appColors.textSecondary)
        Text(text = value, color = MaterialTheme.appColors.textPrimary)
    }
}
