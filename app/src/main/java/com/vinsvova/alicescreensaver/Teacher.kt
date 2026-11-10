package com.vinsvova.alicescreensaver

import android.content.Context
import android.media.AudioAttributes
import android.speech.tts.TextToSpeech
import android.speech.tts.Voice
import java.util.Locale

/**
 * Голос строгой учительницы: синтез речи Android на русском языке.
 * Говорит чуть ниже и медленнее обычного, чтобы звучать строже.
 */
object Teacher {

    val ERRORS = listOf(
        "Подумай лучше!",
        "Старайся!",
        "Будь внимательной!",
        "Не спеши!",
        "Снова ошибка!",
        "Неверно!",
        "Поднажми!"
    )

    val PRAISE = listOf(
        "Молодец, умница!",
        "Так держать!",
        "Похвально!",
        "Ставлю тебе пять!"
    )

    private var tts: TextToSpeech? = null
    private var ready = false
    private var pendingVoice: String? = null
    private var lastError = -1
    private var lastPraise = -1

    /** Синтезатор запустился и умеет говорить по-русски. */
    var russianOk = false
        private set

    /** Синтезатор не запустился или русского языка нет. */
    var failed = false
        private set

    fun init(context: Context, voiceName: String = "") {
        pendingVoice = voiceName
        if (tts != null) return
        tts = TextToSpeech(context.applicationContext) { status ->
            val engine = tts
            if (status != TextToSpeech.SUCCESS || engine == null) {
                failed = true
                return@TextToSpeech
            }
            val res = engine.setLanguage(Locale("ru", "RU"))
            russianOk = res != TextToSpeech.LANG_MISSING_DATA && res != TextToSpeech.LANG_NOT_SUPPORTED
            failed = !russianOk
            engine.setPitch(0.85f)
            engine.setSpeechRate(0.9f)
            engine.setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_GAME)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build()
            )
            ready = true
            pendingVoice?.let { setVoice(it) }
        }
    }

    fun nextError(): String {
        lastError = pick(ERRORS.size, lastError)
        return ERRORS[lastError]
    }

    fun nextPraise(): String {
        lastPraise = pick(PRAISE.size, lastPraise)
        return PRAISE[lastPraise]
    }

    fun say(text: String) {
        if (!ready || !russianOk) return
        try {
            tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "teacher")
        } catch (e: Exception) {
            // синтезатор недоступен
        }
    }

    fun stop() {
        try {
            tts?.stop()
        } catch (e: Exception) {
        }
    }

    /** Русские голоса, которые работают без интернета. */
    fun russianVoices(): List<Voice> {
        if (!ready) return emptyList()
        return try {
            tts?.voices.orEmpty()
                .filter { it.locale.language == "ru" && !it.isNetworkConnectionRequired }
                .sortedBy { it.name }
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun currentVoiceName(): String = try {
        tts?.voice?.name.orEmpty()
    } catch (e: Exception) {
        ""
    }

    fun setVoice(name: String) {
        pendingVoice = name
        if (!ready || name.isEmpty()) return
        val voice = russianVoices().firstOrNull { it.name == name } ?: return
        try {
            tts?.voice = voice
            tts?.setPitch(0.85f)
            tts?.setSpeechRate(0.9f)
        } catch (e: Exception) {
        }
    }

    private fun pick(size: Int, last: Int): Int {
        if (size <= 1) return 0
        var i: Int
        do {
            i = (0 until size).random()
        } while (i == last)
        return i
    }
}
