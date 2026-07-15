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
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
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
 */
class SarvamSttProvider(private val context: Context) : SttProvider {

    private val handler = Handler(Looper.getMainLooper())
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    @Volatile private var isRecording = false
    @Volatile private var isCancelled = false
    private var callbacks: SttCallbacks? = null
    private var recordingThread: Thread? = null

    @Volatile private var activeCall: okhttp3.Call? = null

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
        stopInternals(discard = true)

        val apiKey = try { BuildConfig.SARVAM_API_KEY } catch (_: Exception) { "" }
        if (apiKey.isBlank() || apiKey == "your_sarvam_key_here") {
            callbacks.onError(FailureReason.Unknown("Sarvam API key not configured"))
            return
        }

        this.callbacks = callbacks
        isCancelled = false
        isRecording = true

        recordingThread = Thread {
            val minBuf = AudioRecord.getMinBufferSize(
                SAMPLE_RATE,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT
            )
            if (minBuf == AudioRecord.ERROR_BAD_VALUE || minBuf == AudioRecord.ERROR) {
                handler.post { this.callbacks?.onError(FailureReason.SttFailed) }
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
                handler.post { this.callbacks?.onError(FailureReason.SttFailed) }
                return@Thread
            }

            if (recorder.state != AudioRecord.STATE_INITIALIZED) {
                recorder.release()
                handler.post { this.callbacks?.onError(FailureReason.SttFailed) }
                return@Thread
            }

            handler.post { this.callbacks?.onListeningStarted() }

            val readBuf = ByteArray(minBuf)
            val pcmOut = ByteArrayOutputStream()

            // VAD state
            var speechDetected = false
            var speechStartMs  = 0L
            var silenceStartMs = 0L

            recorder.startRecording()
            while (isRecording) {
                val n = recorder.read(readBuf, 0, readBuf.size)
                if (n > 0) {
                    pcmOut.write(readBuf, 0, n)

                    // Voice Activity Detection
                    val level = rms(readBuf, n)
                    val now   = System.currentTimeMillis()
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
            recorder.stop()
            recorder.release()

            if (isCancelled) return@Thread

            val pcm = pcmOut.toByteArray()
            val wav = buildWav(pcm)

            scope.launch { uploadToSarvam(wav, apiKey) }
        }.also { it.start() }
    }

    override fun stop() {
        isRecording = false
        // Recording thread exits on the next read() return, then uploads.
    }

    override fun cancel() {
        stopInternals(discard = true)
    }

    override fun release() {
        stopInternals(discard = true)
        scope.cancel()
    }

    // -------------------------------------------------------------------------
    // Internal helpers
    // -------------------------------------------------------------------------

    private fun stopInternals(discard: Boolean) {
        if (discard) {
            isCancelled = true
            activeCall?.cancel()
            activeCall = null
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

    private fun uploadToSarvam(wav: ByteArray, apiKey: String) {
        val cb = callbacks ?: return   // caller cancelled; discard result

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
                handler.post { if (!isCancelled) cb.onError(reason) }
                return
            }

            val transcript = try {
                JSONObject(responseBody).optString("transcript", "").trim()
            } catch (_: Exception) { "" }

            handler.post {
                if (isCancelled) return@post
                if (transcript.isNotBlank()) cb.onFinal(transcript)
                else cb.onError(FailureReason.EmptyTranscript)
            }
        } catch (e: Exception) {
            activeCall = null
            handler.post {
                if (!isCancelled) cb.onError(FailureReason.NetworkError(e.message ?: "Network error"))
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
