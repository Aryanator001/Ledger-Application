package com.example.ui.viewmodel

import androidx.lifecycle.ViewModel
import org.junit.Assert.*
import org.junit.Test

class LedgerViewModelStoresTest {
    private class ClearedModel : ViewModel() {
        var cleared = false
        override fun onCleared() { cleared = true }
    }

    @Test fun sameSessionRetainsModelsWhileUserAndScopeChangesClearThem() {
        val stores = LedgerViewModelStores()
        val alice = stores.forLedger("alice", "a")
        val model = ClearedModel()
        alice.put("ledger", model)
        assertSame(alice, stores.forLedger("alice", "a"))
        assertFalse(model.cleared)
        val bob = stores.forLedger("bob", "b")
        assertNotSame(alice, bob)
        assertTrue(model.cleared)
        assertNull(bob.get("ledger"))
        val otherModel = ClearedModel()
        bob.put("ledger", otherModel)
        assertNotSame(bob, stores.forLedger("bob", "another-scope"))
        assertTrue(otherModel.cleared)
    }

    @Test fun signOutClearsRetainedLedgerModels() {
        val stores = LedgerViewModelStores()
        val model = ClearedModel()
        stores.forLedger("alice", "a").put("ledger", model)
        val signedOut = stores.forLedger(null, null)
        assertTrue(model.cleared)
        assertNull(signedOut.get("ledger"))
    }
}
