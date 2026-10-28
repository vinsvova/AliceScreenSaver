package com.vinsvova.alicescreensaver

import kotlin.random.Random

/**
 * Пример из таблицы умножения или деления.
 * Множители, делитель и частное — целые числа от 2 до 9.
 */
data class Example(val a: Int, val b: Int, val op: Char) {

    val answer: Int get() = if (op == MUL) a * b else a / b

    /** Как пример выглядит на экране: «7 × 8 =» или «56 : 7 =». */
    val text: String get() = if (op == MUL) "$a × $b =" else "$a : $b ="

    fun encode(): String = "$a$op$b"

    companion object {
        const val MUL = '*'
        const val DIV = ':'

        fun decode(s: String): Example? {
            val op = when {
                MUL in s -> MUL
                DIV in s -> DIV
                else -> return null
            }
            val parts = s.split(op)
            val a = parts.getOrNull(0)?.trim()?.toIntOrNull() ?: return null
            val b = parts.getOrNull(1)?.trim()?.toIntOrNull() ?: return null
            if (b == 0) return null
            return Example(a, b, op)
        }

        fun encodeList(list: List<Example>): String = list.joinToString(",") { it.encode() }

        fun decodeList(s: String): List<Example> =
            if (s.isBlank()) emptyList() else s.split(",").mapNotNull { decode(it) }

        /** Один пример на умножение и один на деление, в случайном порядке. */
        fun generatePair(): List<Example> {
            val mul = Example(Random.nextInt(2, 10), Random.nextInt(2, 10), MUL)
            val divisor = Random.nextInt(2, 10)
            val quotient = Random.nextInt(2, 10)
            val div = Example(divisor * quotient, divisor, DIV)
            return listOf(mul, div).shuffled()
        }
    }
}
