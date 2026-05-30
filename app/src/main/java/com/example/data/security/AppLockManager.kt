package com.example.data.security

import android.app.Application
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

object AppLockManager : DefaultLifecycleObserver {
    private val _isLocked = MutableStateFlow(false)
    val isLocked: StateFlow<Boolean> = _isLocked.asStateFlow()

    private var backgroundedAt: Long = 0L
    private lateinit var repo: SecurityRepository
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    fun init(application: Application, repository: SecurityRepository) {
        repo = repository
        ProcessLifecycleOwner.get().lifecycle.addObserver(this)
    }

    override fun onStart(owner: LifecycleOwner) {
        // App foregrounded
        scope.launch {
            val enabled = repo.appLockEnabled.first()
            if (!enabled) return@launch
            
            val lockAfterDeferred = async { repo.lockAfterMs.first() }
            val requireOnLaunchDeferred = async { repo.requireUnlockOnLaunch.first() }
            
            val lockAfter = lockAfterDeferred.await()
            val requireOnLaunch = requireOnLaunchDeferred.await()
            val elapsed = System.currentTimeMillis() - backgroundedAt
            
            val shouldLock = when {
                backgroundedAt == 0L && requireOnLaunch -> true
                lockAfter == -1L -> false                   // Never
                lockAfter == 0L -> true                     // Immediately
                elapsed >= lockAfter -> true
                else -> false
            }
            if (shouldLock) _isLocked.value = true
        }
    }

    override fun onStop(owner: LifecycleOwner) {
        backgroundedAt = System.currentTimeMillis()
    }

    fun unlock() { _isLocked.value = false }
    fun lock() { _isLocked.value = true }
}
