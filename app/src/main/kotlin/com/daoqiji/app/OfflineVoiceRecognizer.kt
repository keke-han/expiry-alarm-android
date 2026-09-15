package com.daoqiji.app

import android.annotation.SuppressLint
import android.content.Context
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import com.k2fsa.sherpa.onnx.OnlineModelConfig
import com.k2fsa.sherpa.onnx.OnlineRecognizer
import com.k2fsa.sherpa.onnx.OnlineRecognizerConfig
import com.k2fsa.sherpa.onnx.OnlineZipformer2CtcModelConfig
import java.util.concurrent.Executors

enum class VoiceInputState { Idle, Loading, Listening }

class OfflineVoiceRecognizer(
    context: Context,
    private val onText: (String) -> Unit,
    private val onStateChange: (VoiceInputState) -> Unit,
    private val onError: (String) -> Unit
) {
    private val assets = context.applicationContext.assets
    private val main = Handler(Looper.getMainLooper())
    private val worker = Executors.newSingleThreadExecutor()
    private var recognizer: OnlineRecognizer? = null
    private var state = VoiceInputState.Idle
    @Volatile private var stopRequested = false
    @Volatile private var disposed = false

    fun toggle() {
        when (state) {
            VoiceInputState.Idle -> {
                stopRequested = false
                updateState(VoiceInputState.Loading)
                worker.execute { record() }
            }
            VoiceInputState.Listening -> {
                stopRequested = true
                updateState(VoiceInputState.Loading)
            }
            VoiceInputState.Loading -> Unit
        }
    }

    fun cancel() { stopRequested = true }

    fun dispose() {
        disposed = true
        stopRequested = true
        worker.execute { recognizer?.release(); recognizer = null }
        worker.shutdown()
    }

    @SuppressLint("MissingPermission")
    private fun record() {
        var microphone: AudioRecord? = null
        var result: String? = null
        var failure: Throwable? = null
        try {
            val engine = recognizer ?: OnlineRecognizer(
                assetManager = assets,
                config = OnlineRecognizerConfig(
                    modelConfig = OnlineModelConfig(
                        zipformer2Ctc = OnlineZipformer2CtcModelConfig("$MODEL/model.int8.onnx"),
                        tokens = "$MODEL/tokens.txt"
                    ),
                    ruleFsts = "itn_zh_number.fst",
                    enableEndpoint = true
                )
            ).also { recognizer = it }
            if (disposed || stopRequested) return
            val minimum = AudioRecord.getMinBufferSize(RATE, AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT)
            check(minimum > 0)
            microphone = AudioRecord(MediaRecorder.AudioSource.MIC, RATE,
                AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT,
                maxOf(minimum * 2, RATE / 5 * 2))
            check(microphone.state == AudioRecord.STATE_INITIALIZED)
            val stream = engine.createStream()
            try {
                microphone.startRecording()
                check(microphone.recordingState == AudioRecord.RECORDSTATE_RECORDING)
                main.post { if (!disposed && !stopRequested) updateState(VoiceInputState.Listening) }
                val segments = StringBuilder()
                val buffer = ShortArray(RATE / 10)
                val deadline = SystemClock.elapsedRealtime() + 30_000
                while (!stopRequested && SystemClock.elapsedRealtime() < deadline) {
                    val count = microphone.read(buffer, 0, buffer.size)
                    check(count > 0) { "AudioRecord read failed: $count" }
                    stream.acceptWaveform(FloatArray(count) { buffer[it] / 32768f }, RATE)
                    while (engine.isReady(stream)) engine.decode(stream)
                    // Endpoint marks a phrase boundary; keep listening through natural pauses.
                    if (engine.isEndpoint(stream)) {
                        segments.append(engine.getResult(stream).text)
                        engine.reset(stream)
                    }
                }
                microphone.stop()
                stream.acceptWaveform(FloatArray(RATE), RATE)
                stream.inputFinished()
                while (engine.isReady(stream)) engine.decode(stream)
                segments.append(engine.getResult(stream).text)
                result = segments.toString().trim()
            } finally { stream.release() }
        } catch (error: Exception) {
            failure = error
            Log.e("OfflineVoice", "Recognition failed", error)
        } finally {
            microphone?.release()
            main.post {
                if (!disposed) {
                    updateState(VoiceInputState.Idle)
                    when {
                        failure != null -> onError("语音识别失败，请检查麦克风权限后重试")
                        result == null -> Unit
                        result.isNullOrBlank() -> onError("没有听清，请再试一次")
                        usableSpeechResult(result) == null ->
                            onError("部分内容未识别，请改用中文重试或键盘输入")
                        else -> onText(result)
                    }
                }
            }
        }
    }

    private fun updateState(value: VoiceInputState) {
        state = value
        onStateChange(value)
    }

    private companion object {
        const val RATE = 16_000
        const val MODEL = "sherpa-onnx-streaming-zipformer-small-ctc-zh-int8-2025-04-01"
    }
}
