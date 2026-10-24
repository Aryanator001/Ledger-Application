package com.example.data

/** Legacy IDs remain valid for their owner. New ledgers get distinct, stable IDs. */
object DefaultAccounts {
    private val defaults = listOf(
        Account(id = "default_cash", name = "Cash", type = "CASH", color = "#6FAF8F", icon = "💵"),
        Account(id = "upi_account", name = "PhonePe UPI", type = "UPI", color = "#8B7FD1", icon = "📱"),
        Account(id = "bank_account", name = "HDFC Bank", type = "BANK", color = "#5B8DEF", icon = "🏦")
    )

    fun scopedId(alias: String, scopeId: String): String = "$alias:$scopeId"

    suspend fun ensure(dao: AccountDao, scopeId: String) {
        require(scopeId.isNotBlank())
        defaults.forEach { account ->
            if (dao.getAccountById(account.id, scopeId) == null) {
                dao.insertAccount(account.copy(id = scopedId(account.id, scopeId), ledgerScopeId = scopeId))
            }
        }
    }

    suspend fun resolve(dao: AccountDao, id: String, scopeId: String): Account? {
        dao.getAccountById(id, scopeId)?.let { return it }
        return if (defaults.any { it.id == id }) dao.getAccountById(scopedId(id, scopeId), scopeId) else null
    }
}
