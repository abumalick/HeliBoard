// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.latin.dictation

import android.media.MediaRecorder
import android.os.Build
import helium314.keyboard.latin.LatinIME
import helium314.keyboard.latin.RichInputMethodManager
import helium314.keyboard.latin.settings.Defaults
import helium314.keyboard.latin.settings.Settings
import helium314.keyboard.latin.utils.Log
import helium314.keyboard.latin.utils.prefs
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class DictationManager(private val latinIME: LatinIME) {

    private val state = DictationState()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var recorder: MediaRecorder? = null
    private var transcribeJob: Job? = null
    private var autoDetectLanguage = false
    private val audioFile: File get() = File(latinIME.cacheDir, "dictation.m4a")

    var onStateChanged: ((status: DictationStatus, reason: String?, canRetry: Boolean) -> Unit)? = null

    val isIdle get() = state.status == DictationStatus.IDLE

    fun onKeyPress(autoDetectLanguage: Boolean) {
        when (state.status) {
            DictationStatus.IDLE -> {
                this.autoDetectLanguage = autoDetectLanguage
                startRecording()
            }
            DictationStatus.RECORDING -> stopRecordingAndTranscribe()
            DictationStatus.TRANSCRIBING, DictationStatus.ERROR -> cancel()
        }
    }

    fun retry() {
        if (state.retry()) {
            notifyState()
            launchTranscription()
        }
    }

    fun cancel() = abort()

    fun abort() {
        transcribeJob?.cancel()
        transcribeJob = null
        releaseRecorder()
        audioFile.delete()
        state.abort()
        notifyState()
    }

    private fun startRecording() {
        val token = latinIME.prefs().getString(Settings.PREF_ELEVENLABS_API_KEY, Defaults.PREF_ELEVENLABS_API_KEY)
        if (token.isNullOrBlank()) {
            fail("No API token")
            return
        }
        audioFile.delete()
        val newRecorder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) MediaRecorder(latinIME)
            else @Suppress("DEPRECATION") MediaRecorder()
        try {
            newRecorder.apply {
                setAudioSource(MediaRecorder.AudioSource.VOICE_RECOGNITION)
                setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                setAudioChannels(1)
                setAudioSamplingRate(SAMPLE_RATE_HZ)
                setAudioEncodingBitRate(BIT_RATE)
                setMaxDuration(MAX_DURATION_MS)
                setOutputFile(audioFile.absolutePath)
                setOnInfoListener { _, what, _ ->
                    if (what == MediaRecorder.MEDIA_RECORDER_INFO_MAX_DURATION_REACHED) stopRecordingAndTranscribe()
                }
                prepare()
                start()
            }
        } catch (e: Exception) {
            // most often SecurityException: the RECORD_AUDIO permission was never granted
            newRecorder.release()
            Log.e(TAG, "could not start recording", e)
            fail(if (e is SecurityException) "Microphone permission not granted" else e.javaClass.simpleName)
            return
        }
        recorder = newRecorder
        recordingStartedAt = System.currentTimeMillis()
        state.startRecording()
        notifyState()
    }

    private fun stopRecordingAndTranscribe() {
        if (state.status != DictationStatus.RECORDING) return
        val duration = System.currentTimeMillis() - recordingStartedAt
        releaseRecorder()
        if (duration < MIN_DURATION_MS || !audioFile.exists() || audioFile.length() == 0L) {
            audioFile.delete()
            state.abort()
            fail("Recording too short") // single render: no notifyState() first, or the strip flickers IDLE then ERROR
            return
        }
        state.stopRecording()
        notifyState()
        launchTranscription()
    }

    private fun launchTranscription() {
        val token = latinIME.prefs().getString(Settings.PREF_ELEVENLABS_API_KEY, Defaults.PREF_ELEVENLABS_API_KEY).orEmpty()
        val language = if (autoDetectLanguage) null else RichInputMethodManager.getInstance().currentSubtypeLocale.language
        transcribeJob = scope.launch {
            val result = withContext(Dispatchers.IO) { ElevenLabsClient.transcribe(token, audioFile, language) }
            if (!isActive) return@launch
            when (result) {
                is TranscriptionResult.Success -> {
                    audioFile.delete()
                    state.onSuccess()
                    notifyState()
                    if (result.text.isNotBlank()) latinIME.onTextInput(result.text)
                }
                is TranscriptionResult.Failure -> {
                    state.onFailure(result.reason)
                    notifyState()
                }
            }
        }
    }

    private fun releaseRecorder() {
        val current = recorder ?: return
        recorder = null
        runCatching { current.stop() }
        runCatching { current.release() }
    }

    /**
     * A failure with no audio to resend. The state machine stays IDLE; this only paints a
     * transient message, and canRetry is false so no dead Retry button is offered.
     */
    private fun fail(reason: String) = onStateChanged?.invoke(DictationStatus.ERROR, reason, false)

    private fun notifyState() =
        onStateChanged?.invoke(state.status, state.errorReason, state.status == DictationStatus.ERROR)

    fun destroy() {
        abort()
        scope.cancel()
    }

    private var recordingStartedAt = 0L

    companion object {
        private val TAG = DictationManager::class.simpleName
        private const val SAMPLE_RATE_HZ = 16_000
        private const val BIT_RATE = 32_000
        private const val MAX_DURATION_MS = 60_000
        private const val MIN_DURATION_MS = 300L
    }
}
