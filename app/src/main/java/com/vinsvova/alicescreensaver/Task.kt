package com.vinsvova.alicescreensaver

import org.json.JSONArray
import org.json.JSONObject

/**
 * Одно задание.
 * answer — правильный ответ: число («56») или знак сравнения («>», «<», «=»).
 * plain — короткий пример вида «7 × 8 =», показывается крупно, поле ответа справа.
 */
data class Task(
    val text: String,
    val answer: String,
    val sign: Boolean = false,
    val plain: Boolean = false
) {
    fun toJson(): JSONObject = JSONObject()
        .put("t", text)
        .put("a", answer)
        .put("s", sign)
        .put("p", plain)

    companion object {
        fun fromJson(o: JSONObject): Task? {
            val text = o.optString("t")
            val answer = o.optString("a")
            if (text.isEmpty() || answer.isEmpty()) return null
            return Task(text, answer, o.optBoolean("s"), o.optBoolean("p"))
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
