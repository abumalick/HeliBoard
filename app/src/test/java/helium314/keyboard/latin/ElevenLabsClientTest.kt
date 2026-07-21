// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.latin

import helium314.keyboard.latin.dictation.ElevenLabsClient
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ElevenLabsClientTest {

    @Test fun bodyContainsModelIdAndFilePart() {
        val body = ElevenLabsClient.buildMultipartBody(byteArrayOf(1, 2, 3), null).decodeToString(throwOnInvalidSequence = false)
        assertTrue(body.contains("name=\"model_id\""))
        assertTrue(body.contains("scribe_v1"))
        assertTrue(body.contains("name=\"file\""))
        assertTrue(body.contains("filename=\"dictation.m4a\""))
        assertTrue(body.contains("Content-Type: audio/mp4"))
    }

    @Test fun languageCodeOmittedWhenNull() {
        val body = ElevenLabsClient.buildMultipartBody(byteArrayOf(1), null).decodeToString(throwOnInvalidSequence = false)
        assertFalse(body.contains("name=\"language_code\""))
    }

    @Test fun languageCodeIncludedWhenGiven() {
        val body = ElevenLabsClient.buildMultipartBody(byteArrayOf(1), "ar").decodeToString(throwOnInvalidSequence = false)
        assertTrue(body.contains("name=\"language_code\""))
        assertTrue(body.contains("\r\n\r\nar\r\n"))
    }

    @Test fun bodyIsTerminatedByClosingBoundary() {
        val body = ElevenLabsClient.buildMultipartBody(byteArrayOf(1), "en").decodeToString(throwOnInvalidSequence = false)
        assertTrue(body.trimEnd().endsWith("--"))
    }

    @Test fun audioBytesSurviveEncodingUnchanged() {
        val audio = byteArrayOf(0x00, 0x7F, -0x80, -0x01)
        val body = ElevenLabsClient.buildMultipartBody(audio, null)
        assertTrue(indexOfSubarray(body, audio) >= 0)
    }

    private fun indexOfSubarray(haystack: ByteArray, needle: ByteArray): Int {
        outer@ for (i in 0..haystack.size - needle.size) {
            for (j in needle.indices) if (haystack[i + j] != needle[j]) continue@outer
            return i
        }
        return -1
    }
}
