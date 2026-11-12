package com.vinsvova.alicescreensaver

import org.json.JSONArray
import org.json.JSONObject

/**
 * Одно задание.
 * answer — правильный ответ: число («56»), знак сравнения («>», «<», «=») или текст варианта.
 * plain — короткий пример вида «7 × 8 =», показывается крупно, поле ответа справа.
 * cat, level — вид и сложность, чтобы при защите от угадывания заменить задание похожим.
 * Для нот: clef — "treble" или "bass", pos — положение ноты на стане, options — четыре варианта ответа.
 */
data class Task(
    val text: String,
    val answer: String,
    val sign: Boolean = false,
    val plain: Boolean = false,
    val cat: String = "",
    val level: Int = TaskGenerator.MEDIUM,
    val clef: String = "",
    val pos: Int = 0,
    val options: List<String> = emptyList()
) {
    val isNote: Boolean get() = clef.isNotEmpty() && options.isNotEmpty()

    /** Сколько ошибок допускается, прежде чем задание сменится (чтобы перебором не угадать). */
    val maxWrong: Int get() = when {
        isNote -> options.size - 1
        sign -> 2
        else -> 3
    }

    fun toJson(): JSONObject = JSONObject()
        .put("t", text)
        .put("a", answer)
        .put("s", sign)
        .put("p", plain)
        .put("c", cat)
        .put("lv", level)
        .put("cl", clef)
        .put("ps", pos)
        .put("op", JSONArray(options))

    companion object {
        fun fromJson(o: JSONObject): Task? {
            val text = o.optString("t")
            val answer = o.optString("a")
            if (text.isEmpty() || answer.isEmpty()) return null
            val ops = o.optJSONArray("op")
            val options = if (ops == null) emptyList() else (0 until ops.length()).map { ops.optString(it) }
            return Task(
                text, answer, o.optBoolean("s"), o.optBoolean("p"),
                o.optString("c"), o.optInt("lv", TaskGenerator.MEDIUM),
                o.optString("cl"), o.optInt("ps"), options
            )
        }

        fun encodeList(list: List<Task>): String =
            JSONArray().apply { list.forEach { put(it.toJson()) } }.toString()

        fun decodeList(s: String): List<Task> {
            if (s.isBlank()) return emptyList()
            return try {
                val arr = JSONArray(s)
                (0 until arr.length()).mapNotNull { i -> arr.optJSONObject(i)?.let { fromJson(it) } }
            } catch (e: Exception) {
                emptyList()
            }
        }
    }
}
