package com.vinsvova.alicescreensaver

import kotlin.math.max
import kotlin.math.min
import kotlin.random.Random

/**
 * Задачи на формулы:
 *  — площадь и периметр квадрата и прямоугольника;
 *  — цена, количество, стоимость.
 * У каждого задания есть hint — формула, которая показывается после ошибки.
 * В подсказке только общая формула, без готового ответа.
 */
object FormulaTasks {

    // ---------- Формулы ----------

    private const val SQ_AREA = "Площадь квадрата:\nS = a × a"
    private const val SQ_PERIM = "Периметр квадрата:\nP = a × 4"
    private const val RECT_AREA = "Площадь прямоугольника:\nS = a × b  (длина × ширина)"
    private const val RECT_PERIM = "Периметр прямоугольника:\nP = (a + b) × 2"
    private const val RECT_SIDE_FROM_AREA = "Сторона прямоугольника:\nb = S : a  (площадь : длина)"
    private const val SQ_SIDE_FROM_PERIM = "Сторона квадрата:\na = P : 4"
    private const val SQ_SIDE_FROM_AREA = "Площадь квадрата: S = a × a.\nКакое число, умноженное само на себя, даёт S?"
    private const val RECT_SIDE_FROM_PERIM = "Сторона прямоугольника:\nb = P : 2 − a  (половина периметра минус длина)"
    private const val RECT_PERIM_FROM_AREA = "1) Длина: a = S : b\n2) Периметр: P = (a + b) × 2"
    private const val RECT_AREA_FROM_PERIM = "1) Длина: a = P : 2 − b\n2) Площадь: S = a × b"

    private const val COST = "Стоимость = цена × количество"
    private const val PRICE = "Цена = стоимость : количество"
    private const val COUNT = "Количество = стоимость : цена"
    private const val TWO_PURCHASES = "Стоимость = цена × количество.\nНайди стоимость каждой покупки и сложи."
    private const val CHANGE = "Стоимость = цена × количество.\nОсталось = было − стоимость"
    private const val PRICE_THEN_COST = "1) Цена = стоимость : количество\n2) Стоимость = цена × количество"
    private const val TIMES_PRICE = "1) Цена дороже в k раз: цена × k\n2) Стоимость = цена × количество"

    // ---------- Площадь и периметр ----------

    fun geometry(level: Int): Task {
        val u = listOf("см", "дм", "м").random()
        val sq = "кв. $u"
        return when (level) {
            TaskGenerator.EASY -> when (r(1, 4)) {
                1 -> squareArea(r(2, 9), u, sq)
                2 -> squarePerimeter(r(2, 9), u)
                3 -> { val a = r(3, 9); rectArea(a, r(2, a - 1), u, sq) }
                else -> { val a = r(3, 9); rectPerimeter(a, r(2, a - 1), u) }
            }
            TaskGenerator.MEDIUM -> when (r(1, 6)) {
                1 -> squareArea(r(2, 9), u, sq)
                2 -> squarePerimeter(r(3, 25), u)
                3 -> { val a = r(3, 9); rectArea(a, r(2, 9).let { if (it == a) a - 1 else it }, u, sq) }
                4 -> { val a = r(6, 35); rectPerimeter(a, r(2, min(a - 1, 50 - a)), u) }
                5 -> {
                    // ширина по площади и длине
                    val a = r(3, 9)
                    val b = r(2, 9).let { if (it == a) a - 1 else it }
                    shape(
                        "Площадь прямоугольника ${a * b} $sq, длина $a $u. Найди ширину.",
                        b, RECT_SIDE_FROM_AREA, "rect", "$a $u", "? $u", "S = ${a * b} $sq"
                    )
                }
                else -> {
                    // сторона квадрата по периметру
                    val a = r(2, 25)
                    shape(
                        "Периметр квадрата ${a * 4} $u. Найди длину его стороны.",
                        a, SQ_SIDE_FROM_PERIM, "square", "? $u", "? $u", "P = ${a * 4} $u"
                    )
                }
            }
            else -> when (r(1, 6)) {
                1 -> {
                    val a = r(5, 35)
                    val b = r(2, min(a - 1, 50 - a))
                    shape(
                        "Периметр прямоугольника ${2 * (a + b)} $u, длина $a $u. Найди ширину.",
                        b, RECT_SIDE_FROM_PERIM, "rect", "$a $u", "? $u", "P = ${2 * (a + b)} $u"
                    )
                }
                2 -> {
                    val a = r(2, 9)
                    shape(
                        "Площадь квадрата ${a * a} $sq. Найди длину его стороны.",
                        a, SQ_SIDE_FROM_AREA, "square", "? $u", "? $u", "S = ${a * a} $sq"
                    )
                }
                3 -> {
                    val b = r(2, 8)
                    val a = r(b + 1, 9)
                    shape(
                        "Площадь прямоугольника ${a * b} $sq, ширина $b $u. Найди его периметр.",
                        2 * (a + b), RECT_PERIM_FROM_AREA, "rect", "? $u", "$b $u", "S = ${a * b} $sq"
                    )
                }
                4 -> {
                    val b = r(2, 8)
                    val a = r(b + 1, 9)
                    shape(
                        "Периметр прямоугольника ${2 * (a + b)} $u, ширина $b $u. Найди его площадь.",
                        a * b, RECT_AREA_FROM_PERIM, "rect", "? $u", "$b $u", "P = ${2 * (a + b)} $u"
                    )
                }
                5 -> {
                    val b = r(2, 5)
                    val k = r(2, 4)
                    val a = b * k
                    shape(
                        "Ширина прямоугольника $b $u, а длина в $k ${raz(k)} больше. Найди площадь.",
                        a * b, "1) Длина: a = b × $k\n2) Площадь: S = a × b", "rect", "? $u", "$b $u", "S = ?"
                    )
                }
                else -> {
                    val b = r(2, 9)
                    val k = r(2, 4)
                    val a = b * k
                    shape(
                        "Ширина прямоугольника $b $u, а длина в $k ${raz(k)} больше. Найди периметр.",
                        2 * (a + b), "1) Длина: a = b × $k\n2) Периметр: P = (a + b) × 2", "rect", "? $u", "$b $u", "P = ?"
                    )
                }
            }
        }
    }

    private fun squareArea(a: Int, u: String, sq: String) = shape(
        "Найди площадь квадрата со стороной $a $u.", a * a, SQ_AREA, "square", "$a $u", "$a $u", "S = ? $sq"
    )

    private fun squarePerimeter(a: Int, u: String) = shape(
        "Найди периметр квадрата со стороной $a $u.", a * 4, SQ_PERIM, "square", "$a $u", "$a $u", "P = ? $u"
    )

    private fun rectArea(a: Int, b: Int, u: String, sq: String) = shape(
        "Длина прямоугольника $a $u, ширина $b $u. Найди его площадь.", a * b, RECT_AREA, "rect", "$a $u", "$b $u", "S = ? $sq"
    )

    private fun rectPerimeter(a: Int, b: Int, u: String) = shape(
        "Длина прямоугольника $a $u, ширина $b $u. Найди его периметр.", 2 * (a + b), RECT_PERIM, "rect", "$a $u", "$b $u", "P = ? $u"
    )

    private fun shape(text: String, answer: Int, hint: String, shape: String, top: String, side: String, inside: String) =
        Task(text, answer.toString(), hint = hint, shape = shape, figTop = top, figSide = side, figIn = inside)

    // ---------- Цена, количество, стоимость ----------

    private data class Item(val one: String, val few: String, val many: String, val oneWord: String) {
        fun forms(n: Int) = TaskGenerator.f(n, one, few, many)
        val title: String get() = one.replaceFirstChar { it.uppercase() }
    }

    private val ITEMS = listOf(
        Item("тетрадь", "тетради", "тетрадей", "одна"),
        Item("ручка", "ручки", "ручек", "одна"),
        Item("карандаш", "карандаша", "карандашей", "один"),
        Item("альбом", "альбома", "альбомов", "один"),
        Item("блокнот", "блокнота", "блокнотов", "один"),
        Item("ластик", "ластика", "ластиков", "один"),
        Item("мяч", "мяча", "мячей", "один"),
        Item("книга", "книги", "книг", "одна"),
        Item("булочка", "булочки", "булочек", "одна"),
        Item("яблоко", "яблока", "яблок", "одно")
    )

    /** 4 рубля, 5 рублей, 21 рубль. */
    private fun rub(n: Int) = "$n ${TaskGenerator.f(n, "рубль", "рубля", "рублей")}"

    /** «по 8 рублей», «по 21 рублю». */
    private fun poRub(n: Int) = "по $n ${TaskGenerator.f(n, "рублю", "рубля", "рублей")}"

    fun money(level: Int): Task {
        val item = ITEMS.random()
        return when (level) {
            TaskGenerator.EASY -> {
                val p = r(2, 5)
                val n = r(2, 5)
                simpleMoney(item, p, n, r(1, 3))
            }
            TaskGenerator.MEDIUM -> when (r(1, 5)) {
                in 1..3 -> simpleMoney(item, r(2, 9), r(2, 9), r(1, 3))
                4 -> {
                    // дороже: цена от 10 до 30 рублей, 2–3 штуки
                    val p = r(10, 30)
                    val n = r(2, 3)
                    simpleMoney(item, p, n, r(1, 3))
                }
                else -> {
                    // формулировка как в учебнике: «Цена … количество … найди стоимость»
                    val p = r(2, 9)
                    val n = r(2, 9)
                    when (r(1, 3)) {
                        1 -> Task("Цена ${rub(p)}, количество $n шт. Найди стоимость.", "${p * n}", hint = COST)
                        2 -> Task("Стоимость ${rub(p * n)}, количество $n шт. Найди цену.", "$p", hint = PRICE)
                        else -> Task("Стоимость ${rub(p * n)}, цена ${rub(p)}. Найди количество.", "$n", hint = COUNT)
                    }
                }
            }
            else -> when (r(1, 4)) {
                1 -> {
                    val other = ITEMS.filter { it != item }.random()
                    val n1 = r(2, 5)
                    val p1 = r(2, 9)
                    val n2 = r(2, 4)
                    val p2 = r(10, max(10, min(20, (100 - n1 * p1) / n2)))
                    Task(
                        "Купили $n1 ${item.forms(n1)} ${poRub(p1)} и $n2 ${other.forms(n2)} ${poRub(p2)}. " +
                            "Сколько рублей заплатили за всю покупку?",
                        "${n1 * p1 + n2 * p2}", hint = TWO_PURCHASES
                    )
                }
                2 -> {
                    val n = r(2, 6)
                    val p = r(5, 15)
                    val cost = n * p
                    val had = if (cost < 50) 50 else 100
                    Task(
                        "У Маши было ${rub(had)}. Она купила $n ${item.forms(n)} ${poRub(p)}. Сколько рублей у неё осталось?",
                        "${had - cost}", hint = CHANGE
                    )
                }
                3 -> {
                    val p = r(2, 12)
                    val n1 = r(2, 8)
                    val n2 = r(2, 8).let { if (it == n1) n1 + 1 else it }
                    Task(
                        "За $n1 ${item.forms(n1)} заплатили ${rub(p * n1)}. Сколько рублей стоят $n2 таких ${item.forms(n2)}?",
                        "${p * n2}", hint = PRICE_THEN_COST
                    )
                }
                else -> {
                    val pen = ITEMS[1]
                    val p = r(2, 5)
                    val k = r(2, 3)
                    val n = r(2, 5)
                    Task(
                        "Тетрадь стоит ${rub(p)}, а ручка в $k ${raz(k)} дороже. Сколько рублей стоят $n ${pen.forms(n)}?",
                        "${p * k * n}", hint = TIMES_PRICE
                    )
                }
            }
        }
    }

    private fun simpleMoney(item: Item, p: Int, n: Int, kind: Int): Task = when (kind) {
        1 -> Task(
            "${item.title} стоит ${rub(p)}. Сколько рублей стоят $n ${item.forms(n)}?",
            "${p * n}", hint = COST
        )
        2 -> Task(
            "За $n ${item.forms(n)} заплатили ${rub(p * n)}. Сколько стоит ${item.oneWord} ${item.one}?",
            "$p", hint = PRICE
        )
        else -> Task(
            "${item.title} стоит ${rub(p)}. Сколько ${item.many} можно купить на ${rub(p * n)}?",
            "$n", hint = COUNT
        )
    }

    // ---------- Вспомогательное ----------

    private fun r(from: Int, to: Int): Int = Random.nextInt(from, max(from, to) + 1)

    private fun raz(n: Int) = TaskGenerator.f(n, "раз", "раза", "раз")
}
