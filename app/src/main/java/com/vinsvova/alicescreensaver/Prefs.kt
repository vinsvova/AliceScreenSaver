package com.vinsvova.alicescreensaver

import android.content.Context
import android.content.SharedPreferences
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Calendar

/**
 * Настройки и текущее состояние таймера.
 * PIN хранится только в виде солёного SHA-256, после 5 неверных попыток ввод блокируется.
 */
class Prefs(context: Context) {

    private val sp: SharedPreferences =
        context.applicationContext.getSharedPreferences("rest_timer", Context.MODE_PRIVATE)

    var playMinutes: Int
        get() = sp.getInt(KEY_PLAY, 30)
        set(value) { sp.edit().putInt(KEY_PLAY, value).apply() }

    var restMinutes: Int
        get() = sp.getInt(KEY_REST, 10)
        set(value) { sp.edit().putInt(KEY_REST, value).apply() }

    var message: String
        get() = sp.getString(KEY_MESSAGE, null) ?: DEFAULT_MESSAGE
        set(value) { sp.edit().putString(KEY_MESSAGE, value).apply() }

    var enabled: Boolean
        get() = sp.getBoolean(KEY_ENABLED, false)
        set(value) { sp.edit().putBoolean(KEY_ENABLED, value).apply() }

    /** Сколько секунд планшетом уже пользовались с конца прошлого перерыва. */
    var playedSeconds: Long
        get() = sp.getLong(KEY_PLAYED, 0L)
        set(value) { sp.edit().putLong(KEY_PLAYED, value).apply() }

    /** Время окончания перерыва (System.currentTimeMillis), 0 — перерыва нет. */
    var restEndsAt: Long
        get() = sp.getLong(KEY_REST_END, 0L)
        set(value) { sp.edit().putLong(KEY_REST_END, value).apply() }

    /** Когда погас экран, 0 — экран включён. */
    var screenOffAt: Long
        get() = sp.getLong(KEY_SCREEN_OFF, 0L)
        set(value) { sp.edit().putLong(KEY_SCREEN_OFF, value).apply() }

    /** Блокировка по расписанию включена. */
    var blockEnabled: Boolean
        get() = sp.getBoolean(KEY_BLOCK_ENABLED, false)
        set(value) { sp.edit().putBoolean(KEY_BLOCK_ENABLED, value).apply() }

    /** Начало блокировки, минуты от полуночи (21:00 = 1260). */
    var blockStart: Int
        get() = sp.getInt(KEY_BLOCK_START, 21 * 60)
        set(value) { sp.edit().putInt(KEY_BLOCK_START, value).apply() }

    /** Конец блокировки, минуты от полуночи (08:00 = 480). */
    var blockEnd: Int
        get() = sp.getInt(KEY_BLOCK_END, 8 * 60)
        set(value) { sp.edit().putInt(KEY_BLOCK_END, value).apply() }

    /** До какого момента родитель временно снял блокировку по расписанию. */
    var blockPauseUntil: Long
        get() = sp.getLong(KEY_BLOCK_PAUSE, 0L)
        set(value) { sp.edit().putLong(KEY_BLOCK_PAUSE, value).apply() }

    /** Попадает ли текущий момент в интервал блокировки (учитывая переход через полночь). */
    fun isBlockedNow(now: Long = System.currentTimeMillis()): Boolean {
        if (!blockEnabled) return false
        val start = blockStart
        val end = blockEnd
        if (start == end) return false
        if (now < blockPauseUntil) return false
        val m = minutesOfDay(now)
        return if (start < end) m in start until end else m >= start || m < end
    }

    // ---------- Примеры по математике ----------

    var mathEnabled: Boolean
        get() = sp.getBoolean(KEY_MATH_ENABLED, true)
        set(value) { sp.edit().putBoolean(KEY_MATH_ENABLED, value).apply() }

    /** Через сколько минут пользования планшетом показывать примеры. */
    var mathIntervalMinutes: Int
        get() = sp.getInt(KEY_MATH_INTERVAL, 15)
        set(value) { sp.edit().putInt(KEY_MATH_INTERVAL, value).apply() }

    /** Сколько секунд даётся на ответ. */
    var mathAnswerSeconds: Int
        get() = sp.getInt(KEY_MATH_SECONDS, 60)
        set(value) { sp.edit().putInt(KEY_MATH_SECONDS, value).apply() }

    /** Сколько секунд активности прошло с последних примеров. */
    var mathPlayedSeconds: Long
        get() = sp.getLong(KEY_MATH_PLAYED, 0L)
        set(value) { sp.edit().putLong(KEY_MATH_PLAYED, value).apply() }

    /** Примеры, которые сейчас на экране (пусто — экрана с примерами нет). */
    var activeQuiz: String
        get() = sp.getString(KEY_QUIZ_ACTIVE, "") ?: ""
        set(value) { sp.edit().putString(KEY_QUIZ_ACTIVE, value).apply() }

    /** Текущий экран — повтор ошибок. */
    var activeQuizIsRetry: Boolean
        get() = sp.getBoolean(KEY_QUIZ_ACTIVE_RETRY, false)
        set(value) { sp.edit().putBoolean(KEY_QUIZ_ACTIVE_RETRY, value).apply() }

    /** Примеры с ошибками, которые нужно повторить. */
    var quizRetry: String
        get() = sp.getString(KEY_QUIZ_RETRY, "") ?: ""
        set(value) { sp.edit().putString(KEY_QUIZ_RETRY, value).apply() }

    /** Когда показать повтор ошибок (System.currentTimeMillis), 0 — не нужно. */
    var quizRetryAt: Long
        get() = sp.getLong(KEY_QUIZ_RETRY_AT, 0L)
        set(value) { sp.edit().putLong(KEY_QUIZ_RETRY_AT, value).apply() }

    fun hasPin(): Boolean = sp.contains(KEY_PIN_HASH)

    fun setPin(pin: String) {
        val salt = ByteArray(16).also { SecureRandom().nextBytes(it) }.toHex()
        sp.edit()
            .putString(KEY_PIN_SALT, salt)
            .putString(KEY_PIN_HASH, hash(salt, pin))
            .putInt(KEY_PIN_FAILS, 0)
            .putLong(KEY_PIN_LOCK_UNTIL, 0L)
            .apply()
    }

    /** Сколько секунд ещё нельзя вводить PIN после серии ошибок. */
    fun lockoutSecondsLeft(): Long {
        val left = sp.getLong(KEY_PIN_LOCK_UNTIL, 0L) - System.currentTimeMillis()
        return if (left > 0) (left + 999) / 1000 else 0
    }

    fun checkPin(pin: String): Boolean {
        if (lockoutSecondsLeft() > 0) return false
        val salt = sp.getString(KEY_PIN_SALT, null) ?: return false
        val ok = hash(salt, pin) == sp.getString(KEY_PIN_HASH, null)
        if (ok) {
            sp.edit().putInt(KEY_PIN_FAILS, 0).apply()
        } else {
            val fails = sp.getInt(KEY_PIN_FAILS, 0) + 1
            val editor = sp.edit().putInt(KEY_PIN_FAILS, fails)
            if (fails % 5 == 0) {
                // 1 минута после 5 ошибок, 2 минуты после 10 и т.д.
                editor.putLong(KEY_PIN_LOCK_UNTIL, System.currentTimeMillis() + 60_000L * (fails / 5))
            }
            editor.apply()
        }
        return ok
    }

    private fun hash(salt: String, pin: String): String =
        MessageDigest.getInstance("SHA-256").digest("$salt:$pin".toByteArray()).toHex()

    companion object {
        const val DEFAULT_MESSAGE = "Отдохни"

        private const val KEY_PLAY = "play_minutes"
        private const val KEY_REST = "rest_minutes"
        private const val KEY_MESSAGE = "message"
        private const val KEY_ENABLED = "enabled"
        private const val KEY_PLAYED = "played_seconds"
        private const val KEY_REST_END = "rest_ends_at"
        private const val KEY_SCREEN_OFF = "screen_off_at"
        private const val KEY_BLOCK_ENABLED = "block_enabled"
        private const val KEY_BLOCK_START = "block_start"
        private const val KEY_BLOCK_END = "block_end"
        private const val KEY_BLOCK_PAUSE = "block_pause_until"
        private const val KEY_MATH_ENABLED = "math_enabled"
        private const val KEY_MATH_INTERVAL = "math_interval"
        private const val KEY_MATH_SECONDS = "math_seconds"
        private const val KEY_MATH_PLAYED = "math_played_seconds"
        private const val KEY_QUIZ_ACTIVE = "quiz_active"
        private const val KEY_QUIZ_ACTIVE_RETRY = "quiz_active_retry"
        private const val KEY_QUIZ_RETRY = "quiz_retry"
        private const val KEY_QUIZ_RETRY_AT = "quiz_retry_at"
        private const val KEY_PIN_SALT = "pin_salt"
        private const val KEY_PIN_HASH = "pin_hash"
        private const val KEY_PIN_FAILS = "pin_fails"
        private const val KEY_PIN_LOCK_UNTIL = "pin_lock_until"
    }
}

private fun ByteArray.toHex(): String = joinToString("") { "%02x".format(it) }

/** 125000 мс → "2:05" */
fun formatDuration(ms: Long): String {
    val total = (ms + 999) / 1000
    return "%d:%02d".format(total / 60, total % 60)
}

fun minutesOfDay(now: Long): Int {
    val c = Calendar.getInstance().apply { timeInMillis = now }
    return c.get(Calendar.HOUR_OF_DAY) * 60 + c.get(Calendar.MINUTE)
}

/** 1260 → "21:00" */
fun formatClock(minutesOfDay: Int): String = "%02d:%02d".format(minutesOfDay / 60, minutesOfDay % 60)

/** 125000 мс → "3 мин" (округление вверх) */
fun formatMinutes(ms: Long): String = "${(ms + 59_999) / 60_000} мин"
