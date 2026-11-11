// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.latin.dictation

import helium314.keyboard.latin.BuildConfig
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

sealed interface TranscriptionResult {
    data class Success(val text: String) : TranscriptionResult
    data class Failure(val reason: String) : TranscriptionResult
}

object ElevenLabsClient {

    private const val ENDPOINT = "https://api.elevenlabs.io/v1/speech-to-text"
    private const val MODEL_ID = "scribe_v3"
    private const val BOUNDARY = "----HeliBoardDictation7f3a9c"
    private const val FILE_NAME = "dictation.m4a"
    private const val CONNECT_TIMEOUT_MS = 10_000
    // the API answers only once it has transcribed the whole file, so this has to cover the
    // longest recording the recorder allows, not a typical one
    private const val READ_TIMEOUT_MS = 5 * 60_000

    fun buildMultipartBody(
        audio: ByteArray,
        languageCode: String?,
        keyterms: List<String> = BuildConfig.STT_KEYTERMS.toList(),
        secondaryLanguageCodes: List<String> = emptyList(),
    ): ByteArray {
        val out = ByteArrayOutputStream()
        fun writeField(name: String, value: String) {
            out.write("--$BOUNDARY\r\n".toByteArray())
            out.write("Content-Disposition: form-data; name=\"$name\"\r\n\r\n".toByteArray())
            out.write("$value\r\n".toByteArray())
        }

        writeField("model_id", MODEL_ID)
        // drops filler words, false starts and repeated words at the source; scribe_v2 and later
        writeField("no_verbatim", "true")
        // separate flag, defaults to true: annotates background noise as "(baby crying)" etc.
        writeField("tag_audio_events", "false")
        // no numbers_format: v3 already writes digits by default, and sending it (even "digits")
        // makes the API ignore no_verbatim
        if (languageCode != null) writeField("language_code", languageCode)
        // the API rejects secondaries without a language_code (HTTP 500)
        if (languageCode != null) for (code in secondaryLanguageCodes) writeField("secondary_language_codes", code)
        // jargon and proper nouns the model would otherwise mishear; one repeated part per term is
        // how the API takes a vocabulary. Validated and capped when baked into BuildConfig.
        for (term in keyterms) writeField("keyterms", term)

        out.write("--$BOUNDARY\r\n".toByteArray())
        out.write("Content-Disposition: form-data; name=\"file\"; filename=\"$FILE_NAME\"\r\n".toByteArray())
        out.write("Content-Type: audio/mp4\r\n\r\n".toByteArray())
        out.write(audio)
        out.write("\r\n--$BOUNDARY--\r\n".toByteArray())
        return out.toByteArray()
    }

    /** Blocking. Call from a background dispatcher. */
    fun transcribe(apiKey: String, audioFile: File, languageCode: String?, secondaryLanguageCodes: List<String>): TranscriptionResult {
        var connection: HttpURLConnection? = null
        return try {
            val body = buildMultipartBody(audioFile.readBytes(), languageCode, secondaryLanguageCodes = secondaryLanguageCodes)
            connection = (URL(ENDPOINT).openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                doOutput = true
                connectTimeout = CONNECT_TIMEOUT_MS
                readTimeout = READ_TIMEOUT_MS
                setRequestProperty("xi-api-key", apiKey)
                setRequestProperty("Content-Type", "multipart/form-data; boundary=$BOUNDARY")
                setFixedLengthStreamingMode(body.size)
            }
            connection.outputStream.use { it.write(body) }

            val code = connection.responseCode
            if (code != HttpURLConnection.HTTP_OK) {
                val detail = connection.errorStream?.bufferedReader()?.use { it.readText() }.orEmpty().take(120)
                return TranscriptionResult.Failure(if (detail.isBlank()) "HTTP $code" else "HTTP $code: $detail")
            }
            val text = JSONObject(connection.inputStream.bufferedReader().use { it.readText() }).optString("text")
            TranscriptionResult.Success(text.trim())
        } catch (e: Exception) {
            TranscriptionResult.Failure(e.javaClass.simpleName)
        } finally {
            connection?.disconnect()
        }
    }
}
