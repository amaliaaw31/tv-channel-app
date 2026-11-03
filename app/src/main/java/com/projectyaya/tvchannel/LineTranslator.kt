package com.projectyaya.tvchannel

import android.os.SystemClock
import android.util.Log
import com.google.android.gms.tasks.Task
import com.google.mlkit.common.model.DownloadConditions
import com.google.mlkit.nl.languageid.LanguageIdentification
import com.google.mlkit.nl.languageid.LanguageIdentificationOptions
import com.google.mlkit.nl.translate.TranslateLanguage
import com.google.mlkit.nl.translate.Translation
import com.google.mlkit.nl.translate.Translator
import com.google.mlkit.nl.translate.TranslatorOptions
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.suspendCancellableCoroutine

private const val TAG = "Subtitles"
private const val RETRY_AFTER_MS = 30_000L
private const val MIN_CONFIDENCE = 0.6f

private suspend fun <T> Task<T>.await(): T = suspendCancellableCoroutine { c ->
    addOnSuccessListener { c.resume(it) }
    addOnFailureListener { c.resumeWithException(it) }
}

/**
 * ML Kit on-device language identification and translation to Indonesian. One translator per source language,
 * created on first use (its model is downloaded then); [close] drops the translators and the line cache so the
 * models leave memory when the channel changes. Text is never sent anywhere: only the model files are downloaded.
 * [onDownload] is told when a model download starts and ends.
 */
class LineTranslator(private val onDownload: (Boolean) -> Unit) {
    private val identifier = LanguageIdentification.getClient(
        LanguageIdentificationOptions.Builder().setConfidenceThreshold(MIN_CONFIDENCE).build(),
    )
    private val translators = HashMap<String, Translator>()
    private val ready = HashSet<String>()
    private val failedAt = HashMap<String, Long>()
    private val cache = HashMap<String, String>()

    /** The most likely language of [text] with its confidence; language null for "und" (below the threshold) and for errors. */
    suspend fun identify(text: String): Detection {
        val best = runCatching { identifier.identifyPossibleLanguages(text).await() }.getOrNull()?.firstOrNull()
        return Detection(normalizeLang(best?.languageTag), best?.confidence ?: 0f)
    }

    /** [text] in Indonesian, or null when it cannot be translated (unsupported language, model missing, offline). */
    suspend fun translate(text: String, from: String): String? {
        val source = TranslateLanguage.fromLanguageTag(from) ?: return null
        cache["$source|$text"]?.let { return it }
        failedAt[source]?.let { if (SystemClock.elapsedRealtime() - it < RETRY_AFTER_MS) return null }
        val translator = translators.getOrPut(source) {
            Translation.getClient(
                TranslatorOptions.Builder().setSourceLanguage(source).setTargetLanguage(TranslateLanguage.INDONESIAN).build(),
            )
        }
        return try {
            if (ready.add(source)) {
                onDownload(true)
                try {
                    translator.downloadModelIfNeeded(DownloadConditions.Builder().build()).await()
                } catch (e: Exception) {
                    ready.remove(source)
                    throw e
                } finally {
                    onDownload(false)
                }
            }
            translator.translate(text).await().also { cache["$source|$text"] = it }
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "translate from $source failed: ${e.javaClass.simpleName}")
            failedAt[source] = SystemClock.elapsedRealtime()
            null
        }
    }

    /** Channel changed (or the app went to the background): empty the cache and release the translators. */
    fun close() {
        translators.values.forEach { it.close() }
        translators.clear()
        ready.clear()
        failedAt.clear()
        cache.clear()
    }

    fun release() {
        close()
        identifier.close()
    }
}
