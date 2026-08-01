package com.ledger.query.exec

/**
 * Thin logging facade. Delegates to android.util.Log in production;
 * silently swallows RuntimeException in pure JVM tests where Log is not mocked.
 */
internal object LedgerLog {
    fun d(tag: String, msg: String) = try { android.util.Log.d(tag, msg) } catch (_: RuntimeException) { }
    fun i(tag: String, msg: String) = try { android.util.Log.i(tag, msg) } catch (_: RuntimeException) { }
    fun w(tag: String, msg: String) = try { android.util.Log.w(tag, msg) } catch (_: RuntimeException) { }
    fun e(tag: String, msg: String, t: Throwable? = null) = try {
        if (t != null) android.util.Log.e(tag, msg, t) else android.util.Log.e(tag, msg)
    } catch (_: RuntimeException) { }
}
