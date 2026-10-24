package com.example.ai.overlay

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.view.Gravity
import android.view.WindowManager
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.ComposeView
import androidx.core.content.ContextCompat
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.example.LedgerApplication
import com.example.MainActivity
import com.example.R
import com.example.ai.model.TransactionSource
import com.example.ai.state.FailureReason
import com.example.ai.stt.DualSttProvider
import com.example.ai.stt.SttCallbacks
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Hosts the floating "Quick Add" pill as a real cross-app overlay (WindowManager +
 * TYPE_APPLICATION_OVERLAY), independent of any Activity. Draws the window, owns STT, and
 * commits through QuickAddOrchestrator. Only promotes to a foreground service (with the
 * microphone type) while actively listening — the window itself isn't foreground-gated.
 */
class QuickAddOverlayService : Service() {

    companion object {
        private const val NOTIFICATION_CHANNEL_ID = "quick_add_mic"
        private const val NOTIFICATION_ID = 4201

        fun start(context: Context) {
            context.startService(Intent(context, QuickAddOverlayService::class.java))
        }
    }

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val tornDown = AtomicBoolean(false)

    private lateinit var windowManager: WindowManager
    private lateinit var layoutParams: WindowManager.LayoutParams
    private lateinit var composeView: ComposeView
    private lateinit var lifecycleOwner: OverlayLifecycleOwner
    private val sttProvider by lazy { DualSttProvider(applicationContext) }

    private var textState by mutableStateOf("")
    private var listeningState by mutableStateOf(false)
    private var statusState by mutableStateOf<QuickAddPillStatus>(QuickAddPillStatus.Idle)

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (!OverlayPermissionHelper.canDrawOverlays(this)) {
            stopSelf()
            return START_NOT_STICKY
        }
        if (!::composeView.isInitialized) {
            showOverlay()
        }
        return START_NOT_STICKY
    }

    private fun showOverlay() {
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        lifecycleOwner = OverlayLifecycleOwner().also { it.start() }

        val overlayType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }

        layoutParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            overlayType,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
            y = resources.displayMetrics.heightPixels / 6
        }

        composeView = ComposeView(this).apply {
            setViewTreeLifecycleOwner(lifecycleOwner)
            setViewTreeViewModelStoreOwner(lifecycleOwner)
            setViewTreeSavedStateRegistryOwner(lifecycleOwner)
            setContent {
                QuickAddOverlayTheme {
                    QuickAddPill(
                        text = textState,
                        onTextChange = { textState = it },
                        isListening = listeningState,
                        micAvailable = hasRecordAudioPermission(),
                        onMicClick = { onMicClick() },
                        onSubmit = { onSubmit() },
                        onDismiss = { teardown() },
                        onAddClick = { openApp() },
                        status = statusState,
                        placeholder = getString(R.string.quick_add_pill_placeholder),
                    )
                }
            }
        }

        windowManager.addView(composeView, layoutParams)
        requestFocusForInput()
        QuickAddHaptics.onOpen(this)
    }

    private fun openApp() {
        val intent = Intent(this, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        startActivity(intent)
        teardown()
    }

    private fun requestFocusForInput() {
        layoutParams.flags = layoutParams.flags and WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE.inv()
        layoutParams.softInputMode = WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE
        windowManager.updateViewLayout(composeView, layoutParams)
    }

    private fun hasRecordAudioPermission(): Boolean =
        ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED

    private fun onMicClick() {
        if (!hasRecordAudioPermission()) return
        if (listeningState) {
            sttProvider.stop()
            return
        }
        promoteToForeground()
        listeningState = true
        sttProvider.start(object : SttCallbacks {
            override fun onListeningStarted() = Unit

            override fun onPartial(text: String) {
                textState = text
            }

            override fun onFinal(text: String) {
                listeningState = false
                demoteFromForeground()
                textState = text
                if (text.isNotBlank()) onSubmit(TransactionSource.VOICE)
            }

            override fun onError(reason: FailureReason) {
                listeningState = false
                demoteFromForeground()
                QuickAddHaptics.onError(this@QuickAddOverlayService)
            }
        })
    }

    private fun onSubmit(source: TransactionSource = TransactionSource.TYPED) {
        val transcript = textState
        if (transcript.isBlank()) return
        val orchestrator = (application as LedgerApplication).quickAddOrchestrator
        serviceScope.launch {
            when (val result = orchestrator.handle(transcript, source)) {
                is QuickAddResult.Committed -> {
                    statusState = QuickAddPillStatus.Committed
                    QuickAddHaptics.onCommit(this@QuickAddOverlayService)
                    delay(500)
                    teardown()
                }
                is QuickAddResult.Failed -> {
                    statusState = QuickAddPillStatus.Error(result.message)
                    QuickAddHaptics.onError(this@QuickAddOverlayService)
                }
            }
        }
    }

    private fun promoteToForeground() {
        ensureNotificationChannel()
        val notification = buildNotification()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun demoteFromForeground() {
        stopForeground(STOP_FOREGROUND_REMOVE)
    }

    private fun ensureNotificationChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = getSystemService(NotificationManager::class.java)
        if (manager.getNotificationChannel(NOTIFICATION_CHANNEL_ID) == null) {
            val channel = NotificationChannel(
                NOTIFICATION_CHANNEL_ID,
                getString(R.string.quick_add_tile_label),
                NotificationManager.IMPORTANCE_MIN
            )
            manager.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(): Notification {
        val builder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder(this, NOTIFICATION_CHANNEL_ID)
        } else {
            @Suppress("DEPRECATION")
            Notification.Builder(this)
        }
        return builder
            .setContentTitle(getString(R.string.quick_add_tile_label))
            .setSmallIcon(R.drawable.ic_quick_add)
            .setOngoing(true)
            .build()
    }

    private fun teardown() {
        if (!tornDown.compareAndSet(false, true)) return
        sttProvider.release()
        if (::windowManager.isInitialized && ::composeView.isInitialized) {
            try {
                windowManager.removeViewImmediate(composeView)
            } catch (_: IllegalArgumentException) {
                // View already detached — ignore.
            }
        }
        if (::lifecycleOwner.isInitialized) {
            lifecycleOwner.destroy()
        }
        demoteFromForeground()
        stopSelf()
    }

    override fun onDestroy() {
        teardown()
        serviceScope.cancel()
        super.onDestroy()
    }
}
