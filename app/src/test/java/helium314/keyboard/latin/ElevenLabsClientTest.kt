// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.latin

import helium314.keyboard.latin.dictation.ElevenLabsClient
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ElevenLabsClientTest {

    @Test fun bodyContainsModelIdAndFilePart() {
        val body = ElevenLabsClient.buildMultipartBody(byteArrayOf(1, 2, 3), null, emptyList()).decodeToString(throwOnInvalidSequence = false)
        assertTrue(body.contains("name=\"model_id\""))
        assertTrue(body.contains("scribe_v3"))
        assertFalse(body.contains("name=\"numbers_format\""))
        // no_verbatim is what keeps fillers out of the transcript
        assertTrue(body.contains("name=\"no_verbatim\""))
        assertTrue(body.contains("\r\n\r\ntrue\r\n"))
        // tag_audio_events defaults to true server-side and annotates background noise
        assertTrue(body.contains("name=\"tag_audio_events\""))
        assertTrue(body.contains("\r\n\r\nfalse\r\n"))
        assertTrue(body.contains("name=\"file\""))
        assertTrue(body.contains("filename=\"dictation.m4a\""))
        assertTrue(body.contains("Content-Type: audio/mp4"))
    }

    @Test fun languageCodeOmittedWhenNull() {
        val body = ElevenLabsClient.buildMultipartBody(byteArrayOf(1), null, emptyList()).decodeToString(throwOnInvalidSequence = false)
        assertFalse(body.contains("name=\"language_code\""))
    }

    // The API takes a vocabulary as one repeated part per term, not a joined string.
    @Test fun keytermsAreSentAsOnePartEach() {
        val body = ElevenLabsClient
            .buildMultipartBody(byteArrayOf(1), null, listOf("Perch", "Claude Code"))
            .decodeToString(throwOnInvalidSequence = false)
        assertEquals(2, Regex("name=\"keyterms\"").findAll(body).count())
        assertTrue(body.contains("\r\n\r\nPerch\r\n"))
        assertTrue(body.contains("\r\n\r\nClaude Code\r\n"))
    }

    @Test fun keytermsOmittedWhenVocabularyIsEmpty() {
        val body = ElevenLabsClient.buildMultipartBody(byteArrayOf(1), null, emptyList()).decodeToString(throwOnInvalidSequence = false)
        assertFalse(body.contains("name=\"keyterms\""))
    }

    @Test fun languageCodeIncludedWhenGiven() {
        val body = ElevenLabsClient.buildMultipartBody(byteArrayOf(1), "ar", emptyList()).decodeToString(throwOnInvalidSequence = false)
        assertTrue(body.contains("name=\"language_code\""))
        assertTrue(body.contains("\r\n\r\nar\r\n"))
    }

    @Test fun secondaryLanguagesAreSentAsOnePartEach() {
        val body = ElevenLabsClient
            .buildMultipartBody(byteArrayOf(1), "en", emptyList(), listOf("ar", "fr"))
            .decodeToString(throwOnInvalidSequence = false)
        assertEquals(2, Regex("name=\"secondary_language_codes\"").findAll(body).count())
        assertTrue(body.contains("name=\"secondary_language_codes\"\r\n\r\nar\r\n"))
        assertTrue(body.contains("name=\"secondary_language_codes\"\r\n\r\nfr\r\n"))
    }

    @Test fun secondaryLanguagesOmittedWithoutLanguageCode() {
        val body = ElevenLabsClient
            .buildMultipartBody(byteArrayOf(1), null, emptyList(), listOf("ar", "fr"))
            .decodeToString(throwOnInvalidSequence = false)
        assertFalse(body.contains("name=\"secondary_language_codes\""))
    }

    @Test fun bodyIsTerminatedByClosingBoundary() {
        val body = ElevenLabsClient.buildMultipartBody(byteArrayOf(1), "en", emptyList()).decodeToString(throwOnInvalidSequence = false)
        assertTrue(body.trimEnd().endsWith("--"))
    }

    @Test fun audioBytesSurviveEncodingUnchanged() {
        val audio = byteArrayOf(0x00, 0x7F, -0x80, -0x01)
        val body = ElevenLabsClient.buildMultipartBody(audio, null, emptyList())
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
