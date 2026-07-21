// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.latin.dictation

enum class DictationStatus { IDLE, RECORDING, TRANSCRIBING, ERROR }

/**
 * Transition rules for a dictation attempt. Deliberately free of Android types so the
 * "every state can reach IDLE" invariant is unit-testable.
 *
 * The audio file outlives a failed transcription, which is what makes retry free.
 * Only reaching IDLE releases it.
 */
class DictationState {

    var status: DictationStatus = DictationStatus.IDLE
        private set

    var hasAudio: Boolean = false
        private set

    var errorReason: String? = null
        private set

    fun startRecording(): Boolean {
        if (status != DictationStatus.IDLE) return false
        status = DictationStatus.RECORDING
        hasAudio = false
        errorReason = null
        return true
    }

    fun stopRecording(): Boolean {
        if (status != DictationStatus.RECORDING) return false
        status = DictationStatus.TRANSCRIBING
        hasAudio = true
        return true
    }

    fun onSuccess() {
        status = DictationStatus.IDLE
        hasAudio = false
        errorReason = null
    }

    fun onFailure(reason: String) {
        if (status != DictationStatus.TRANSCRIBING) return
        status = DictationStatus.ERROR
        errorReason = reason
    }

    fun retry(): Boolean {
        if (status != DictationStatus.ERROR || !hasAudio) return false
        status = DictationStatus.TRANSCRIBING
        errorReason = null
        return true
    }

    fun abort() {
        status = DictationStatus.IDLE
        hasAudio = false
        errorReason = null
    }
}
