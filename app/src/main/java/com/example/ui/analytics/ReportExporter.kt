package com.example.ui.analytics

import android.content.Context
import android.content.Intent
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import androidx.core.content.FileProvider
import com.example.data.CategorySpend
import com.example.data.Transaction
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class ReportExporter(private val context: Context) {

    suspend fun buildCsvShareIntent(
        transactions: List<Transaction>,
        categories: Map<String, String>,
        accounts: Map<String, String>,
        periodLabel: String
    ): Intent = withContext(Dispatchers.IO) {
        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        val sb = StringBuilder()
        sb.appendLine("Date,Type,Category,Amount,Account,Note")
        transactions.forEach { tx ->
            val note = (tx.description ?: "").replace("\n", " ")
            sb.appendLine(listOf(
                sdf.format(Date(tx.date)),
                tx.type,
                categories[tx.categoryId] ?: tx.categoryId,
                tx.amount.toString(),
                accounts[tx.accountId] ?: tx.accountId,
                note
            ).joinToString(",") { csvField(it) })
        }

        val file = exportFile("ledger_report", "csv")
        file.writeText(sb.toString())
        shareIntent(file, "text/csv", "Ledger Report – $periodLabel")
    }

    suspend fun buildPdfShareIntent(
        totalIncomePaise: Long,
        totalExpensePaise: Long,
        netPaise: Long,
        categoryBreakdown: List<CategorySpend>,
        transactions: List<Transaction>,
        categories: Map<String, String>,
        periodLabel: String
    ): Intent = withContext(Dispatchers.IO) {
        val doc = PdfDocument()
        val file: File
        try {
            val pageInfo = PdfDocument.PageInfo.Builder(595, 842, 1).create()
            val page = doc.startPage(pageInfo)
            val canvas = page.canvas

            val titlePaint = Paint().apply { textSize = 22f; isFakeBoldText = true }
            val headPaint = Paint().apply { textSize = 13f; isFakeBoldText = true }
            val bodyPaint = Paint().apply { textSize = 11f }
            val smallPaint = Paint().apply { textSize = 9f }
            val linePaint = Paint().apply { strokeWidth = 0.5f }

            var y = 55f

            // Header
            canvas.drawText("Ledger Financial Report", 40f, y, titlePaint)
            y += 22f
            canvas.drawText("Period: $periodLabel", 40f, y, bodyPaint)
            y += 6f
            canvas.drawLine(40f, y, 555f, y, linePaint)
            y += 18f

            // Summary
            canvas.drawText("SUMMARY", 40f, y, headPaint)
            y += 16f
            canvas.drawText("Total Income:   ₹${totalIncomePaise / 100}", 40f, y, bodyPaint)
            y += 14f
            canvas.drawText("Total Expense:  ₹${totalExpensePaise / 100}", 40f, y, bodyPaint)
            y += 14f
            val netLabel = if (netPaise >= 0) "Net Profit: ₹${netPaise / 100}" else "Net Loss:   ₹${-netPaise / 100}"
            canvas.drawText(netLabel, 40f, y, bodyPaint)
            y += 18f
            canvas.drawLine(40f, y, 555f, y, linePaint)
            y += 18f

            // Category breakdown
            if (categoryBreakdown.isNotEmpty()) {
                canvas.drawText("EXPENSE BREAKDOWN", 40f, y, headPaint)
                y += 14f
                categoryBreakdown.take(8).forEach { cat ->
                    canvas.drawText(
                        "${cat.categoryName}: ₹${cat.totalAmount / 100}  (${String.format("%.1f", cat.percentage)}%)",
                        48f, y, smallPaint
                    )
                    y += 12f
                }
                y += 4f
                canvas.drawLine(40f, y, 555f, y, linePaint)
                y += 18f
            }

            // Transaction table
            canvas.drawText("TRANSACTIONS", 40f, y, headPaint)
            y += 14f
            val headerPaint = Paint().apply { textSize = 9f; isFakeBoldText = true }
            canvas.drawText("Date", 40f, y, headerPaint)
            canvas.drawText("Type", 115f, y, headerPaint)
            canvas.drawText("Category", 185f, y, headerPaint)
            canvas.drawText("Amount", 390f, y, headerPaint)
            canvas.drawText("Note", 460f, y, headerPaint)
            y += 4f
            canvas.drawLine(40f, y, 555f, y, linePaint)
            y += 10f

            val rowPaint = Paint().apply { textSize = 9f }
            val dateFmt = SimpleDateFormat("dd/MM/yy", Locale.getDefault())
            for (tx in transactions) {
                if (y > 810f) break
                canvas.drawText(dateFmt.format(Date(tx.date)), 40f, y, rowPaint)
                canvas.drawText(tx.type, 115f, y, rowPaint)
                canvas.drawText((categories[tx.categoryId] ?: tx.categoryId).take(18), 185f, y, rowPaint)
                canvas.drawText("₹${tx.amount}", 390f, y, rowPaint)
                canvas.drawText((tx.description ?: "").take(14), 460f, y, rowPaint)
                y += 12f
            }

            // Footer
            val footerPaint = Paint().apply { textSize = 8f }
            canvas.drawText(
                "Generated by Ledger App  •  ${SimpleDateFormat("dd MMM yyyy", Locale.getDefault()).format(Date())}",
                40f, 830f, footerPaint
            )

            doc.finishPage(page)
            file = exportFile("ledger_report", "pdf")
            FileOutputStream(file).use { doc.writeTo(it) }
        } finally {
            doc.close()
        }
        shareIntent(file, "application/pdf", "Ledger Report – $periodLabel")
    }

    private fun csvField(value: String): String =
        if (value.contains(',') || value.contains('"') || value.contains('\n'))
            "\"${value.replace("\"", "\"\"")}\"" else value

    private fun exportFile(name: String, ext: String): File {
        val dir = File(context.getExternalFilesDir(null), "exports")
        dir.mkdirs()
        return File(dir, "${name}_${System.currentTimeMillis()}.$ext")
    }

    private fun shareIntent(file: File, mimeType: String, subject: String): Intent {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.provider", file)
        return Intent(Intent.ACTION_SEND).apply {
            type = mimeType
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, subject)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }
}
