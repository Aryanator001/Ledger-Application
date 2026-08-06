package com.example.ai.stt

import android.content.Context
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Handler
import android.os.Looper
import com.example.BuildConfig
import com.example.ai.state.FailureReason
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import kotlin.math.sqrt

/**
 * SttProvider backed by Sarvam AI's saaras:v3 model.
 *
 * Records raw PCM audio via AudioRecord, wraps it in a WAV container, and POSTs
 * it to the Sarvam speech-to-text REST endpoint. Delivers onPartial heartbeats
 * during recording so the UI shows live activity, then onFinal with the transcript.
 *
 * Falls back gracefully: on any network or API error it fires onError so the
 * DualSttProvider can hand off to AndroidNativeSttProvider.
 *
 * Threading:
 *  - AudioRecord capture runs on a dedicated Thread.
 *  - OkHttp upload runs on Dispatchers.IO via a supervised CoroutineScope.
 *  - All callbacks are delivered on the main thread via Handler.
 *
 * Session safety:
 *  - Every call to start() increments sessionId. Recording threads and upload
 *    coroutines capture mySession at creation time and bail out silently if
 *    sessionId has moved on (meaning start()/cancel() was called again).
 *    This eliminates the isCancelled race that caused the 3-4 transaction freeze.
 */
class SarvamSttProvider(private val context: Context) : SttProvider {

    private val handler = Handler(Looper.getMainLooper())
    private var scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    // Session counter — incremented on every start(). Threads and coroutines
    // capture their session at start; any stale work sees sessionId != mySession
    // and exits without touching callbacks or state.
    private val sessionId = AtomicInteger(0)

    @Volatile private var isRecording = false
    @Volatile private var isCancelled = false
    @Volatile private var callbacks: SttCallbacks? = null
    private var recordingThread: Thread? = null

    @Volatile private var activeCall: okhttp3.Call? = null
    @Volatile private var uploadJob: Job? = null

    // Holds the active AudioRecord so stopInternals() can call stop() from the main thread,
    // forcing any blocking read() to return ERROR_INVALID_OPERATION (n < 0).
    private val activeRecorder = AtomicReference<AudioRecord?>(null)

    private companion object {
        const val SAMPLE_RATE = 16_000
        const val SARVAM_MODEL = "saaras:v3"
        const val LANGUAGE_CODE = "unknown"     // auto-detect Hinglish / Hindi / English
        const val API_URL = "https://api.sarvam.ai/speech-to-text"

        // VAD thresholds
        const val SILENCE_THRESHOLD_RMS  = 600f   // PCM amplitude; raise if too trigger-happy
        const val MIN_SPEECH_MS          = 400L   // ignore blips shorter than this
        const val SILENCE_TO_FINALIZE_MS = 1500L  // silence after speech → auto-stop

        val client: OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .build()
    }

    // -------------------------------------------------------------------------
    // SttProvider interface
    // -------------------------------------------------------------------------

    override fun start(callbacks: SttCallbacks) {
        // Recreate scope if release() was called previously (e.g. during a navigation
        // disposal that incorrectly called release() instead of cancel()).
        if (!scope.isActive) scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

        // Capture the old recording thread BEFORE stopInternals clears the reference,
        // so we can join it and ensure the old AudioRecord is released before we
        // allocate a new one. (Android allows only one active AudioRecord at a time.)
        val previousThread = recordingThread
        stopInternals(discard = true)

        // Wait up to 400 ms for the previous thread to exit and release AudioRecord.
        // Typical exit time is 20–100 ms (one VAD read buffer + recorder.release()).
        previousThread?.join(400L)

        val apiKey = try { BuildConfig.SARVAM_API_KEY } catch (_: Exception) { "" }
        if (apiKey.isBlank() || apiKey == "your_sarvam_key_here") {
            callbacks.onError(FailureReason.Unknown("Sarvam API key not configured"))
            return
        }

        this.callbacks = callbacks
        isCancelled = false
        isRecording = true

        // Mint a session ID for this recording cycle. Every thread and coroutine
        // launched below captures this value and compares it before touching state.
        val mySession = sessionId.incrementAndGet()

        recordingThread = Thread {
            val minBuf = AudioRecord.getMinBufferSize(
                SAMPLE_RATE,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT
            )
            if (minBuf == AudioRecord.ERROR_BAD_VALUE || minBuf == AudioRecord.ERROR) {
                handler.post {
                    if (sessionId.get() == mySession) this.callbacks?.onError(FailureReason.SttFailed)
                }
                return@Thread
            }

            @Suppress("MissingPermission")
            val recorder = try {
                AudioRecord(
                    MediaRecorder.AudioSource.MIC,
                    SAMPLE_RATE,
                    AudioFormat.CHANNEL_IN_MONO,
                    AudioFormat.ENCODING_PCM_16BIT,
                    minBuf * 4
                )
            } catch (e: Exception) {
                handler.post {
                    if (sessionId.get() == mySession) this.callbacks?.onError(FailureReason.SttFailed)
                }
                return@Thread
            }

            if (recorder.state != AudioRecord.STATE_INITIALIZED) {
                recorder.release()
                handler.post {
                    if (sessionId.get() == mySession) this.callbacks?.onError(FailureReason.SttFailed)
                }
                return@Thread
            }

            handler.post {
                if (sessionId.get() == mySession) this.callbacks?.onListeningStarted()
            }

            val readBuf = ByteArray(minBuf)
            val pcmOut = ByteArrayOutputStream()

            // VAD state
            var speechDetected = false
            var speechStartMs  = 0L
            var silenceStartMs = 0L

            val recordStartMs = System.currentTimeMillis()
            recorder.startRecording()
            activeRecorder.set(recorder)   // expose so stopInternals can unblock read()
            while (isRecording) {
                val n = recorder.read(readBuf, 0, readBuf.size)
                if (n < 0) {
                    // AudioRecord returned an error code — the audio HAL has entered a bad
                    // state after repeated create/stop/release cycles. release() is always
                    // safe; stop() would throw IllegalStateException in ERROR state.
                    activeRecorder.compareAndSet(recorder, null)
                    recorder.release()
                    handler.post {
                        if (sessionId.get() == mySession)
                            this.callbacks?.onError(FailureReason.SttFailed)
                    }
                    return@Thread
                }
                if (n > 0) {
                    pcmOut.write(readBuf, 0, n)

                    // Voice Activity Detection
                    val level = rms(readBuf, n)
                    val now   = System.currentTimeMillis()

                    // Safety timeout: if no speech is detected within 8 seconds, the HAL
                    // is likely returning zero-amplitude buffers. Fire SttFailed so
                    // DualSttProvider can fall back to Android native STT transparently.
                    if (!speechDetected && (now - recordStartMs) > 8_000L) {
                        isRecording = false
                        break
                    }

                    if (level > SILENCE_THRESHOLD_RMS) {
                        if (!speechDetected) { speechDetected = true; speechStartMs = now }
                        silenceStartMs = 0L
                    } else if (speechDetected) {
                        if (silenceStartMs == 0L) silenceStartMs = now
                        if ((now - speechStartMs)  >= MIN_SPEECH_MS &&
                            (now - silenceStartMs) >= SILENCE_TO_FINALIZE_MS) {
                            isRecording = false   // auto-stop after silence
                        }
                    }
                }
            }
            // CAS: if stopInternals already called stop() from main thread, skip it here
            // to avoid IllegalStateException on double-stop.
            if (activeRecorder.compareAndSet(recorder, null)) {
                try { recorder.stop() } catch (_: Exception) { }
            }
            recorder.release()

            // If we exited via the 8-second no-speech timeout (HAL returning silent
            // buffers), fire SttFailed to trigger the Android native STT fallback
            // instead of uploading an empty WAV to Sarvam.
            if (!speechDetected && !isCancelled && sessionId.get() == mySession) {
                handler.post {
                    if (sessionId.get() == mySession)
                        this.callbacks?.onError(FailureReason.SttFailed)
                }
                return@Thread
            }

            // Guard: if this session was superseded (start() called again) or cancelled,
            // do not upload. Using sessionId instead of isCancelled alone prevents the
            // race where isCancelled is reset to false by a new start() before this check.
            if (isCancelled || sessionId.get() != mySession) return@Thread

            val pcm = pcmOut.toByteArray()
            val wav = buildWav(pcm)

            uploadJob = scope.launch { uploadToSarvam(wav, apiKey, mySession) }
        }.also { it.start() }
    }

    override fun stop() {
        isRecording = false
        // Recording thread exits on the next read() return, then uploads.
    }

    override fun cancel() {
        stopInternals(discard = true)
        scope.cancel()
    }

    override fun release() {
        stopInternals(discard = true)
        scope.cancel()
    }

    // -------------------------------------------------------------------------
    // Internal helpers
    // -------------------------------------------------------------------------

    private fun stopActiveRecorder() {
        val rec = activeRecorder.getAndSet(null)
        try { rec?.stop() } catch (_: Exception) { }
    }

    private fun stopInternals(discard: Boolean) {
        if (discard) {
            isCancelled = true
            stopActiveRecorder()   // forces any blocking read() to return immediately
            activeCall?.cancel()
            activeCall = null
            uploadJob?.cancel()
            uploadJob = null
        }
        isRecording = false
        callbacks = null
        recordingThread = null
    }

    // -------------------------------------------------------------------------
    // Voice Activity Detection
    // -------------------------------------------------------------------------

    private fun rms(buf: ByteArray, n: Int): Float {
        var sum = 0.0
        var i = 0
        while (i < n - 1) {
            val s = ((buf[i + 1].toInt() shl 8) or (buf[i].toInt() and 0xFF)).toShort()
            sum += s.toDouble() * s.toDouble()
            i += 2
        }
        return sqrt(sum / (n / 2)).toFloat()
    }

    private fun uploadToSarvam(wav: ByteArray, apiKey: String, mySession: Int) {
        // Bail out immediately if this session has been superseded or cancelled.
        if (sessionId.get() != mySession || isCancelled) return
        val cb = callbacks ?: return

        try {
            val body = MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart("file", "audio.wav", wav.toRequestBody("audio/wav".toMediaType()))
                .addFormDataPart("model", SARVAM_MODEL)
                .addFormDataPart("language_code", LANGUAGE_CODE)
                .build()

            val request = Request.Builder()
                .url(API_URL)
                .header("api-subscription-key", apiKey)
                .post(body)
                .build()

            val call = client.newCall(request)
            activeCall = call
            val response = call.execute()
            activeCall = null

            val responseBody = response.body?.string() ?: ""

            if (!response.isSuccessful) {
                val reason = if (response.code == 401 || response.code == 403)
                    FailureReason.Unknown("Sarvam auth error (${response.code})")
                else
                    FailureReason.NetworkError("Sarvam API error: ${response.code}")
                handler.post {
                    if (sessionId.get() == mySession && !isCancelled) cb.onError(reason)
                }
                return
            }

            val transcript = try {
                JSONObject(responseBody).optString("transcript", "").trim()
            } catch (_: Exception) { "" }

            handler.post {
                if (sessionId.get() != mySession || isCancelled) return@post
                if (transcript.isNotBlank()) cb.onFinal(transcript)
                else cb.onError(FailureReason.EmptyTranscript)
            }
        } catch (e: Exception) {
            activeCall = null
            handler.post {
                if (sessionId.get() == mySession && !isCancelled)
                    cb.onError(FailureReason.NetworkError(e.message ?: "Network error"))
            }
        }
    }

    // -------------------------------------------------------------------------
    // WAV packaging
    // -------------------------------------------------------------------------

    private fun buildWav(pcm: ByteArray): ByteArray {
        val dataLen = pcm.size
        val byteRate = SAMPLE_RATE * 2   // 16-bit mono: 1 channel × 2 bytes/sample
        val buf = ByteBuffer.allocate(44 + dataLen).order(ByteOrder.LITTLE_ENDIAN)

        buf.put("RIFF".toByteArray())
        buf.putInt(36 + dataLen)          // ChunkSize
        buf.put("WAVE".toByteArray())
        buf.put("fmt ".toByteArray())
        buf.putInt(16)                    // Subchunk1Size (PCM)
        buf.putShort(1)                   // AudioFormat = PCM
        buf.putShort(1)                   // NumChannels = mono
        buf.putInt(SAMPLE_RATE)
        buf.putInt(byteRate)
        buf.putShort(2)                   // BlockAlign
        buf.putShort(16)                  // BitsPerSample
        buf.put("data".toByteArray())
        buf.putInt(dataLen)
        buf.put(pcm)

        return buf.array()
    }
}
