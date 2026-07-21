// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.latin

import helium314.keyboard.latin.dictation.DictationState
import helium314.keyboard.latin.dictation.DictationStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DictationStateTest {

    @Test fun happyPathReachesIdle() {
        val state = DictationState()
        assertEquals(DictationStatus.IDLE, state.status)
        assertTrue(state.startRecording())
        assertEquals(DictationStatus.RECORDING, state.status)
        assertTrue(state.stopRecording())
        assertEquals(DictationStatus.TRANSCRIBING, state.status)
        state.onSuccess()
        assertEquals(DictationStatus.IDLE, state.status)
        assertFalse(state.hasAudio)
    }

    @Test fun failureEntersErrorAndKeepsAudio() {
        val state = DictationState()
        state.startRecording()
        state.stopRecording()
        state.onFailure("HTTP 503")
        assertEquals(DictationStatus.ERROR, state.status)
        assertEquals("HTTP 503", state.errorReason)
        assertTrue(state.hasAudio)
    }

    @Test fun retryFromErrorReusesAudio() {
        val state = DictationState()
        state.startRecording()
        state.stopRecording()
        state.onFailure("timeout")
        assertTrue(state.retry())
        assertEquals(DictationStatus.TRANSCRIBING, state.status)
        assertTrue(state.hasAudio)
        assertNull(state.errorReason)
    }

    @Test fun abortFromErrorReleasesAudio() {
        val state = DictationState()
        state.startRecording()
        state.stopRecording()
        state.onFailure("timeout")
        state.abort()
        assertEquals(DictationStatus.IDLE, state.status)
        assertFalse(state.hasAudio)
        assertNull(state.errorReason)
    }

    @Test fun abortFromEveryStateReachesIdle() {
        for (status in DictationStatus.entries) {
            val state = stateIn(status)
            assertEquals(status, state.status)
            state.abort()
            assertEquals("abort from $status must reach IDLE", DictationStatus.IDLE, state.status)
            assertFalse("abort from $status must release audio", state.hasAudio)
        }
    }

    @Test fun everyStateHasAnExitToIdle() {
        // IDLE is trivially IDLE; the other three must each have a non-abort path home.
        val recording = stateIn(DictationStatus.RECORDING)
        recording.stopRecording()
        recording.onSuccess()
        assertEquals(DictationStatus.IDLE, recording.status)

        val transcribing = stateIn(DictationStatus.TRANSCRIBING)
        transcribing.onSuccess()
        assertEquals(DictationStatus.IDLE, transcribing.status)

        val error = stateIn(DictationStatus.ERROR)
        error.retry()
        error.onSuccess()
        assertEquals(DictationStatus.IDLE, error.status)
    }

    @Test fun illegalTransitionsAreRejected() {
        val state = DictationState()
        assertFalse("cannot stop a recording that never started", state.stopRecording())
        assertFalse("cannot retry from IDLE", state.retry())
        state.startRecording()
        assertFalse("cannot start twice", state.startRecording())
    }

    private fun stateIn(status: DictationStatus): DictationState {
        val state = DictationState()
        when (status) {
            DictationStatus.IDLE -> {}
            DictationStatus.RECORDING -> state.startRecording()
            DictationStatus.TRANSCRIBING -> { state.startRecording(); state.stopRecording() }
            DictationStatus.ERROR -> { state.startRecording(); state.stopRecording(); state.onFailure("x") }
        }
        return state
    }
}
