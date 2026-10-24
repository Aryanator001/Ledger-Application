package com.example.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelStore

/** Retain across rotation, but clear ledger data and jobs whenever the identity changes. */
class LedgerViewModelStores : ViewModel() {
    private var key: Pair<String?, String?>? = null
    private var store = ViewModelStore()

    fun forLedger(userId: String?, scopeId: String?): ViewModelStore {
        val next = userId to scopeId
        if (key != next) {
            store.clear()
            store = ViewModelStore()
            key = next
        }
        return store
    }

    override fun onCleared() { store.clear() }
}
