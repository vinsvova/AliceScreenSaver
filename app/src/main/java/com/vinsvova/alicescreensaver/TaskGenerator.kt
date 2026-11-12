package com.vinsvova.alicescreensaver

import kotlin.math.max
import kotlin.math.min
import kotlin.random.Random

/**
 * Генератор заданий в духе математических диктантов для начальной школы.
 * Все числа случайные, ответы всегда целые и положительные.
 * У каждого вида три уровня сложности: лёгкий, средний, сложный.
 */
object TaskGenerator {

    const val CAT_PLAIN = "plain"       // 7 × 8 =, 56 : 7 =
    const val CAT_WORDS = "words"       // таблица умножения словами
    const val CAT_EXTRA = "extra"       // внетабличное умножение и деление
    const val CAT_ADDSUB = "addsub"     // сложение и вычитание в пределах 100
    const val CAT_STEPS = "steps"       // действия в два шага
    const val CAT_COMPARE = "compare"   // сравнение величин м, дм, см
    const val CAT_PROBLEMS = "problems" // текстовые задачи
    const val CAT_NOTES = "notes"       // ноты в скрипичном и басовом ключе

    val ALL = listOf(CAT_PLAIN, CAT_WORDS, CAT_EXTRA, CAT_ADDSUB, CAT_STEPS, CAT_COMPARE, CAT_PROBLEMS, CAT_NOTES)

    const val EASY = 1
    const val MEDIUM = 2
    const val HARD = 3

    private const val MINUS = "−"

    fun generate(count: Int, categories: Set<String>, level: Int = MEDIUM): List<Task> {
        val enabled = ALL.filter { it in categories }.ifEmpty { listOf(CAT_PLAIN) }
        val result = mutableListOf<Task>()
        var order = enabled.shuffled()
        var i = 0
        var guard = 0
        while (result.size < count && guard < 500) {
            guard++
            if (i >= order.size) {
                order = enabled.shuffled()
                i = 0
            }
            val task = one(order[i], level)
            if (result.none { it.text == task.text }) {
                result.add(task)
                i++
            }
        }
        return result
    }

    fun one(category: String, level: Int = MEDIUM): Task {
        val lv = level.coerceIn(EASY, HARD)
        val cat = if (category in ALL) category else CAT_PLAIN
        val task = when (cat) {
            CAT_WORDS -> words(lv)
            CAT_EXTRA -> extra(lv)
            CAT_ADDSUB -> addSub(lv)
            CAT_STEPS -> steps(lv)
            CAT_COMPARE -> compare(lv)
            CAT_PROBLEMS -> problems(lv)
            CAT_NOTES -> notes(lv)
            else -> plain(lv)
        }
        return task.copy(cat = cat, level = lv)
    }

    /** Другое задание того же вида и сложности — для защиты от угадывания перебором. */
    fun replacement(old: Task): Task {
        val cat = old.cat.ifEmpty { CAT_PLAIN }
        repeat(30) {
            val t = one(cat, old.level)
            if (t.text != old.text || t.answer != old.answer || t.pos != old.pos || t.clef != old.clef) return t
        }
        return one(cat, old.level)
    }

    // ---------- Ноты ----------

    private val NOTE_NAMES = listOf("до", "ре", "ми", "фа", "соль", "ля", "си")
    private val OCTAVES = mapOf(2 to "большой октавы", 3 to "малой октавы", 4 to "первой октавы", 5 to "второй октавы")

    /** Нижняя линейка: ми первой октавы в скрипичном ключе, соль большой — в басовом. */
    private const val TREBLE_BOTTOM = 4 * 7 + 2
    private const val BASS_BOTTOM = 2 * 7 + 4
    private const val LOWEST = 2 * 7      // до большой октавы
    private const val HIGHEST = 5 * 7 + 6 // си второй октавы

    /** d — номер ступени: октава × 7 + нота (до = 0 … си = 6). */
    fun noteLabel(d: Int, withOctave: Boolean): String =
        NOTE_NAMES[d % 7] + if (withOctave) "\n" + OCTAVES[d / 7] else ""

    private fun notes(level: Int): Task {
        val treble = Random.nextBoolean()
        val (lo, hi) = when (level) {
            EASY -> 0 to 8                       // только на линейках и между ними
            MEDIUM -> -2 to 10                   // плюс одна добавочная линейка
            else -> if (treble) -2 to 11 else -4 to 10 // до двух добавочных линеек
        }
        val pos = r(lo, hi)
        val d = (if (treble) TREBLE_BOTTOM else BASS_BOTTOM) + pos
        val withOctave = level != EASY
        val correct = noteLabel(d, withOctave)

        val options = mutableListOf(correct)
        if (!withOctave) {
            NOTE_NAMES.filter { it != correct }.shuffled().take(3).forEach { options.add(it) }
        } else {
            val candidates = mutableListOf<Int>()
            if (level == HARD) {
                // та же нота в другой октаве — нужно смотреть на ключ и линейки
                listOf(d - 7, d + 7).filter { it in LOWEST..HIGHEST }.shuffled().firstOrNull()?.let { candidates.add(it) }
                listOf(d - 1, d + 1, d - 2, d + 2).shuffled().forEach { candidates.add(it) }
            } else {
                listOf(d - 1, d + 1, d - 2, d + 2, d - 3, d + 3).shuffled().forEach { candidates.add(it) }
            }
            for (c in candidates) {
                if (options.size == 4) break
                if (c !in LOWEST..HIGHEST) continue
                val label = noteLabel(c, true)
                if (label !in options) options.add(label)
            }
            var extraStep = 3
            while (options.size < 4) {
                extraStep++
                for (c in listOf(d + extraStep, d - extraStep)) {
                    if (options.size < 4 && c in LOWEST..HIGHEST) {
                        val label = noteLabel(c, true)
                        if (label !in options) options.add(label)
                    }
                }
            }
        }
        return Task(
            text = "Какая это нота?",
            answer = correct,
            clef = if (treble) "treble" else "bass",
            pos = pos,
            options = options.shuffled()
        )
    }

    /** Пара множителей из таблицы по уровню: лёгкий 2–5, средний 2–9, сложный — хотя бы один от 6 до 9. */
    private fun tablePair(level: Int): Pair<Int, Int> = when (level) {
        EASY -> r(2, 5) to r(2, 5)
        MEDIUM -> r(2, 9) to r(2, 9)
        else -> if (Random.nextBoolean()) r(6, 9) to r(2, 9) else r(2, 9) to r(6, 9)
    }

    // ---------- Табличные примеры ----------

    private fun plain(level: Int): Task {
        val (a, b) = tablePair(level)
        val p = a * b
        if (level == HARD && Random.nextInt(3) == 0) {
            return when (r(1, 3)) {
                1 -> Task("□ × $b = $p", "$a", plain = true)
                2 -> Task("$p : □ = $a", "$b", plain = true)
                else -> Task("□ : $b = $a", "$p", plain = true)
            }
        }
        return if (Random.nextBoolean()) {
            Task("$a × $b =", "$p", plain = true)
        } else {
            Task("$p : $b =", "$a", plain = true)
        }
    }

    // ---------- Таблица умножения словами ----------

    private fun words(level: Int): Task {
        val (a, b) = tablePair(level)
        val p = a * b
        val simple: List<() -> Task> = listOf(
            { t("Увеличь $a в $b ${raz(b)}.", p) },
            { t("Уменьши $p в $b ${raz(b)}.", a) },
            { t("Найди произведение чисел $a и $b.", p) },
            { t("Найди частное чисел $p и $b.", a) },
            { t("Чему равно произведение чисел $a и $b?", p) },
            { t("Чему равно частное чисел $p и $b?", a) }
        )
        val harder: List<() -> Task> = listOf(
            { t("Какое число нужно умножить на $b, чтобы получить $p?", a) },
            { t("На какое число нужно разделить $p, чтобы получить $a?", b) },
            { t("Какое число нужно разделить на $b, чтобы получилось $a?", p) },
            { t("Во сколько раз $p больше $b?", a) },
            { t("Во сколько раз $b меньше $p?", a) },
            { t("Какое число в $b ${raz(b)} больше $a?", p) },
            { t("Какое число в $b ${raz(b)} меньше $p?", a) },
            { t("Первый множитель $a, второй множитель $b. Найди произведение.", p) },
            { t("Делимое $p, делитель $b. Найди частное.", a) },
            { t("Произведение равно $p, один множитель $b. Найди другой множитель.", a) }
        )
        val pool = when (level) {
            EASY -> simple
            MEDIUM -> simple + harder
            else -> harder
        }
        return pool.random()()
    }

    // ---------- Внетабличное умножение и деление ----------

    private fun extra(level: Int): Task {
        return when (level) {
            EASY -> {
                val b = r(2, 4)
                val x = r(11, min(20, 80 / b))
                when (r(1, 4)) {
                    1 -> t("Найди произведение чисел $x и $b.", x * b)
                    2 -> t("Найди частное чисел ${x * b} и $b.", x)
                    3 -> Task("$x × $b =", "${x * b}", plain = true)
                    else -> Task("${x * b} : $b =", "$x", plain = true)
                }
            }
            MEDIUM -> {
                val b = r(2, 9)
                val x = r(11, max(11, 99 / b))
                when (r(1, 7)) {
                    1 -> t("Найди произведение чисел $x и $b.", x * b)
                    2 -> t("Найди частное чисел ${x * b} и $b.", x)
                    3 -> {
                        val s = x * b
                        val y = r(max(10, s / 4), s - max(10, s / 4))
                        t("Сумму чисел $y и ${s - y} раздели на $b.", x)
                    }
                    4 -> t("Увеличь $x в $b ${raz(b)}.", x * b)
                    5 -> t("Уменьши ${x * b} в $b ${raz(b)}.", x)
                    6 -> Task("$x × $b =", "${x * b}", plain = true)
                    else -> Task("${x * b} : $b =", "$x", plain = true)
                }
            }
            else -> when (r(1, 6)) {
                1 -> {
                    val b = r(3, 8)
                    val x = r(11, max(11, 99 / b))
                    val p = x * b
                    val c = r(5, p - 5)
                    t("Произведение чисел $x и $b уменьши на $c.", p - c)
                }
                2 -> {
                    val b = r(2, 6)
                    val q = r(11, min(33, 99 / b))
                    val k = r(2, max(2, 99 / q))
                    t("Частное чисел ${q * b} и $b увеличь в $k ${raz(k)}.", q * k)
                }
                3 -> {
                    val c = r(3, 8)
                    val q = r(11, max(11, 94 / c))
                    val s = c * q
                    val x = r(s + 5, 99)
                    t("Разность чисел $x и ${x - s} раздели на $c.", q)
                }
                4 -> {
                    val c = r(6, 9)
                    val q = r(11, max(11, 99 / c))
                    val s = c * q
                    val y = r(max(10, s / 4), s - max(10, s / 4))
                    t("Сумму чисел $y и ${s - y} раздели на $c.", q)
                }
                5 -> {
                    val b = r(2, 4)
                    val x = r(11, 19)
                    val c = r(2, 4)
                    val q = r(11, 20)
                    t("Сложи произведение чисел $x и $b и частное чисел ${c * q} и $c.", x * b + q)
                }
                else -> {
                    val b = r(3, 8)
                    val x = r(11, max(11, 99 / b))
                    if (Random.nextBoolean()) Task("□ × $b = ${x * b}", "$x", plain = true)
                    else Task("${x * b} : □ = $x", "$b", plain = true)
                }
            }
        }
    }

    // ---------- Сложение и вычитание в пределах 100 ----------

    private fun addSub(level: Int): Task {
        return when (level) {
            EASY -> when (r(1, 5)) {
                1 -> {
                    val a = r(1, 8)
                    val b = r(1, 9 - a)
                    Task("${a * 10} + ${b * 10} =", "${(a + b) * 10}", plain = true)
                }
                2 -> {
                    val a = r(3, 9)
                    val b = r(1, a - 1)
                    Task("${a * 10} $MINUS ${b * 10} =", "${(a - b) * 10}", plain = true)
                }
                3 -> {
                    val x = r(1, 8) * 10 + r(0, 8)
                    val d = r(1, 9 - x % 10)
                    Task("$x + $d =", "${x + d}", plain = true)
                }
                4 -> {
                    val x = r(1, 9) * 10 + r(2, 9)
                    val d = r(1, x % 10)
                    Task("$x $MINUS $d =", "${x - d}", plain = true)
                }
                else -> {
                    val x = r(11, 69)
                    val tens = r(1, (99 - x) / 10) * 10
                    if (Random.nextBoolean()) Task("$x + $tens =", "${x + tens}", plain = true)
                    else Task("${x + tens} $MINUS $tens =", "$x", plain = true)
                }
            }
            MEDIUM -> when (r(1, 6)) {
                1 -> {
                    // с переходом через десяток: 38 + 7
                    val x = r(1, 8) * 10 + r(2, 9)
                    val d = r(10 - x % 10, 9)
                    Task("$x + $d =", "${x + d}", plain = true)
                }
                2 -> {
                    // с переходом через десяток: 52 − 6
                    val x = r(2, 9) * 10 + r(0, 7)
                    val d = r(x % 10 + 1, 9)
                    Task("$x $MINUS $d =", "${x - d}", plain = true)
                }
                3 -> {
                    // без перехода: 34 + 25
                    val t1 = r(1, 7)
                    val t2 = r(1, 8 - t1)
                    val u1 = r(0, 8)
                    val u2 = r(1, 9 - u1)
                    Task("${t1 * 10 + u1} + ${t2 * 10 + u2} =", "${(t1 + t2) * 10 + u1 + u2}", plain = true)
                }
                4 -> {
                    // без перехода: 87 − 43
                    val t1 = r(3, 9)
                    val t2 = r(1, t1 - 1)
                    val u1 = r(1, 9)
                    val u2 = r(0, u1)
                    val x = t1 * 10 + u1
                    val y = t2 * 10 + u2
                    Task("$x $MINUS $y =", "${x - y}", plain = true)
                }
                5 -> {
                    val x = r(21, 89)
                    val d = r(3, 9)
                    if (x + d <= 100 && Random.nextBoolean()) t("Увеличь $x на $d.", x + d)
                    else t("Уменьши $x на $d.", x - d)
                }
                else -> {
                    val x = r(11, 60)
                    val y = r(11, 99 - x)
                    if (Random.nextBoolean()) t("Найди сумму чисел $x и $y.", x + y)
                    else t("Найди разность чисел ${x + y} и $y.", x)
                }
            }
            else -> when (r(1, 6)) {
                1 -> {
                    // с переходом: 47 + 38
                    val u1 = r(2, 9)
                    val u2 = r(10 - u1, 9)
                    val t1 = r(1, 7)
                    val t2 = r(1, max(1, 8 - t1))
                    val x = t1 * 10 + u1
                    val y = t2 * 10 + u2
                    Task("$x + $y =", "${x + y}", plain = true)
                }
                2 -> {
                    // с переходом: 82 − 57
                    val u1 = r(0, 7)
                    val u2 = r(u1 + 1, 9)
                    val t1 = r(3, 9)
                    val t2 = r(1, t1 - 2)
                    val x = t1 * 10 + u1
                    val y = t2 * 10 + u2
                    Task("$x $MINUS $y =", "${x - y}", plain = true)
                }
                3 -> {
                    // три числа: 45 + 27 − 18
                    val a = r(15, 55)
                    val b = r(12, 99 - a)
                    val c = r(11, a + b - 5)
                    Task("$a + $b $MINUS $c =", "${a + b - c}", plain = true)
                }
                4 -> {
                    val b = r(13, 48)
                    val s = r(b + 11, 99)
                    if (Random.nextBoolean()) Task("□ + $b = $s", "${s - b}", plain = true)
                    else Task("$s $MINUS □ = ${s - b}", "$b", plain = true)
                }
                5 -> {
                    val y = r(15, 70)
                    val x = r(y + 6, 99)
                    if (Random.nextBoolean()) t("На сколько $x больше $y?", x - y)
                    else t("На сколько $y меньше $x?", x - y)
                }
                else -> {
                    val x = r(25, 60)
                    val y = r(13, 99 - x)
                    if (Random.nextBoolean()) {
                        val c = r(7, x + y - 5)
                        t("Сумму чисел $x и $y уменьши на $c.", x + y - c)
                    } else {
                        val big = x + y
                        val c = r(7, 99 - x)
                        t("Разность чисел $big и $y увеличь на $c.", x + c)
                    }
                }
            }
        }
    }

    // ---------- Действия в два шага ----------

    private fun steps(level: Int): Task = when (level) {
        EASY -> when (r(1, 4)) {
            1 -> {
                val a = r(2, 5)
                val b = r(2, 5)
                val c = r(2, 20)
                t("К произведению чисел $a и $b прибавь $c.", a * b + c)
            }
            2 -> {
                val a = r(2, 5)
                val b = r(2, 5)
                val x = r(a * b + 1, a * b + 30)
                t("Из числа $x вычти произведение чисел $a и $b.", x - a * b)
            }
            3 -> {
                val c = r(2, 5)
                val q = r(2, 5)
                val s = c * q
                val x = r(1, s - 1)
                t("Сумму чисел $x и ${s - x} раздели на $c.", q)
            }
            else -> {
                val b = r(2, 5)
                val q = r(2, 5)
                val c = r(2, 20)
                t("Частное чисел ${b * q} и $b увеличь на $c.", q + c)
            }
        }
        MEDIUM -> mediumSteps()
        else -> when (r(1, 6)) {
            1 -> {
                val (a, b) = tablePair(HARD)
                val c = r(2, 9)
                val q = r(2, 9)
                t("Сложи произведение чисел $a и $b и частное чисел ${c * q} и $c.", a * b + q)
            }
            2 -> {
                val (a, b) = tablePair(HARD)
                val c = r(2, 9)
                val q = r(2, min(9, a * b - 1))
                t("Из произведения чисел $a и $b вычти частное чисел ${c * q} и $c.", a * b - q)
            }
            3 -> {
                val (a, b) = tablePair(HARD)
                var c = r(2, 9)
                var d = r(2, 9)
                while (c * d >= a * b) {
                    c = r(2, 9)
                    d = r(2, 5)
                    if (a * b <= 4) break
                }
                if (c * d >= a * b) t("Найди произведение чисел $a и $b.", a * b)
                else t("Из произведения чисел $a и $b вычти произведение чисел $c и $d.", a * b - c * d)
            }
            4 -> {
                val a = r(3, 6)
                val big = r(11, min(19, 99 / a))
                val n = r(2, 4)
                val q = r(11, min(20, big * a - 1))
                t("Произведение чисел $big и $a уменьши на частное чисел ${n * q} и $n.", big * a - q)
            }
            5 -> {
                val c = r(3, 9)
                val q = r(3, 9)
                val s = c * q
                val edge = max(2, s / 4)
                val x = r(edge, s - edge)
                val d = r(11, 40)
                t("Сумму чисел $x и ${s - x} раздели на $c, а результат увеличь на $d.", q + d)
            }
            else -> {
                val c = r(2, 3)
                val d = r(11, 99 / c)
                val x = r(d + 10, 99)
                t("Разность чисел $x и ${x - d} увеличь в $c ${raz(c)}.", d * c)
            }
        }
    }

    private fun mediumSteps(): Task = when (r(1, 7)) {
        1 -> {
            val c = r(2, 9)
            val q = r(3, 9)
            val s = c * q
            val edge = max(2, s / 4)
            val x = r(edge, s - edge)
            t("Сумму чисел $x и ${s - x} раздели на $c.", q)
        }
        2 -> {
            val c = r(2, 5)
            val d = r(2, min(30, 99 / c))
            val x = r(d + 10, 99)
            t("Разность чисел $x и ${x - d} увеличь в $c ${raz(c)}.", d * c)
        }
        3 -> {
            val a = r(2, 6)
            val big = r(11, min(19, 99 / a))
            val n = r(2, 9)
            val q = r(2, 9)
            t("Произведение чисел $big и $a уменьши на частное чисел ${n * q} и $n.", big * a - q)
        }
        4 -> {
            val b = r(2, 9)
            val q = r(2, 9)
            val c = r(10, 60)
            t("Частное чисел ${b * q} и $b увеличь на $c.", q + c)
        }
        5 -> {
            val a = r(2, 9)
            val b = r(2, 9)
            val x = r(a * b + 1, 100)
            t("Из числа $x вычти произведение чисел $a и $b.", x - a * b)
        }
        6 -> {
            val a = r(2, 9)
            val b = r(2, 9)
            val c = r(10, 40)
            t("К произведению чисел $a и $b прибавь $c.", a * b + c)
        }
        else -> {
            val c = r(2, 9)
            val q = r(2, 9)
            val s = c * q
            val x = r(s + 5, 99)
            t("Разность чисел $x и ${x - s} раздели на $c.", q)
        }
    }

    // ---------- Сравнение величин ----------

    private fun compare(level: Int): Task {
        val equal = Random.nextInt(3) == 0
        val q: Quad = when (level) {
            EASY -> when (r(1, 3)) {
                1 -> {
                    // 5 дм и 48 см
                    val dm = r(2, 9)
                    val real = dm * 10
                    val other = if (equal) real else shifted(real, 2, 9, min = 11)
                    Quad("$dm дм", "$other см", real, other)
                }
                2 -> {
                    // 7 м 6 дм и 7 м
                    val m = r(2, 9)
                    val dm = r(1, 9)
                    val otherM = if (Random.nextBoolean()) m else m + 1
                    Quad("$m м $dm дм", "$otherM м", m * 10 + dm, otherM * 10)
                }
                else -> {
                    // 3 м и 28 дм
                    val m = r(2, 9)
                    val real = m * 10
                    val other = if (equal) real else shifted(real, 2, 9, min = 11)
                    Quad("$m м", "$other дм", real, other)
                }
            }
            MEDIUM -> when (r(1, 5)) {
                1 -> {
                    val m = r(2, 9)
                    val cm = r(1, 99)
                    val real = m * 100 + cm
                    val other = if (equal) real else shifted(real, 1, 30, min = 101)
                    Quad("$m м $cm см", "$other см", real, other)
                }
                2 -> {
                    val m = r(2, 9)
                    val dm = r(1, 9)
                    val real = m * 10 + dm
                    val other = if (equal) real else shifted(real, 1, 9, min = 11)
                    Quad("$m м $dm дм", "$other дм", real, other)
                }
                3 -> {
                    val m = r(2, 9)
                    val dm = r(1, 9)
                    val real = m * 10 + dm
                    val other = if (equal) real else shifted(real, 1, 9, min = 11)
                    Quad("$other дм", "$m м $dm дм", other, real)
                }
                4 -> {
                    val m = r(2, 9)
                    val dm = r(1, 9)
                    val otherM = if (Random.nextBoolean()) m else m + 1
                    Quad("$m м $dm дм", "$otherM м", m * 10 + dm, otherM * 10)
                }
                else -> {
                    val dm = r(2, 9)
                    val real = dm * 10
                    val other = if (equal) real else shifted(real, 1, 9, min = 11)
                    Quad("$dm дм", "$other см", real, other)
                }
            }
            else -> when (r(1, 5)) {
                1 -> {
                    // 3 м 5 дм и 345 см
                    val m = r(1, 9)
                    val dm = r(1, 9)
                    val real = m * 100 + dm * 10
                    val other = if (equal) real else shifted(real, 1, 9, min = 101)
                    Quad("$m м $dm дм", "$other см", real, other)
                }
                2 -> {
                    // 4 дм 5 см и 45 см
                    val dm = r(1, 9)
                    val cm = r(1, 9)
                    val real = dm * 10 + cm
                    val other = if (equal) real else shifted(real, 1, 3, min = 11)
                    Quad("$dm дм $cm см", "$other см", real, other)
                }
                3 -> {
                    // 1 м и 98 см
                    val other = if (equal) 100 else shifted(100, 1, 9, min = 90)
                    if (Random.nextBoolean()) Quad("1 м", "$other см", 100, other)
                    else Quad("$other см", "1 м", other, 100)
                }
                4 -> {
                    // 6 м 96 см и 7 м
                    val m = r(2, 8)
                    val cm = r(80, 99)
                    val otherM = if (Random.nextBoolean()) m + 1 else m
                    Quad("$m м $cm см", "$otherM м", m * 100 + cm, otherM * 100)
                }
                else -> {
                    // близкие значения: 5 м 84 см и 587 см
                    val m = r(2, 9)
                    val cm = r(10, 95)
                    val real = m * 100 + cm
                    val other = if (equal) real else shifted(real, 1, 5, min = 101)
                    Quad("$other см", "$m м $cm см", other, real)
                }
            }
        }
        val sign = when {
            q.l > q.r -> ">"
            q.l < q.r -> "<"
            else -> "="
        }
        return Task("Поставь знак >, < или =\n${q.left}  ?  ${q.right}", sign, sign = true)
    }

    private data class Quad(val left: String, val right: String, val l: Int, val r: Int)

    private fun shifted(value: Int, from: Int, to: Int, min: Int): Int {
        val d = r(from, to)
        val up = value + d
        val down = value - d
        return if (down >= min && Random.nextBoolean()) down else up
    }

    // ---------- Текстовые задачи ----------

    private fun problems(level: Int): Task = when (level) {
        EASY -> when (r(1, 5)) {
            1 -> booksProblem(r(2, 5), r(2, 5))
            2 -> stickersProblem(r(2, 5), r(2, 5))
            3 -> candiesProblem(r(2, 5), r(2, 5))
            4 -> kittenProblem(r(2, 5), r(2, 5))
            else -> teamsProblem(r(2, 5), r(2, 5))
        }
        MEDIUM -> mediumProblem()
        else -> when (r(1, 6)) {
            1 -> {
                val a = r(3, 9)
                val b = r(2, 9)
                t(
                    "У Маши $a ${f(a, "наклейка", "наклейки", "наклеек")}, а у Пети в $b ${raz(b)} больше. " +
                        "Сколько наклеек у них вместе?",
                    a + a * b
                )
            }
            2 -> {
                val n = r(2, 6)
                val k = r(11, min(19, 99 / n))
                val total = n * k
                val c = r(5, total - 5)
                t(
                    "В $n ${f(n, "коробке", "коробках", "коробках")} по $k ${f(k, "карандашу", "карандаша", "карандашей")}. " +
                        "Ребятам раздали $c ${f(c, "карандаш", "карандаша", "карандашей")}. Сколько карандашей осталось?",
                    total - c
                )
            }
            3 -> {
                val n = r(4, 9)
                val k = r(6, min(12, 99 / n))
                val total = n * k
                val c = r(5, total - 5)
                t(
                    "В саду посадили $n ${f(n, "ряд", "ряда", "рядов")} деревьев, по $k в каждом. " +
                        "$c из них — вишни, остальные — яблони. Сколько яблонь в саду?",
                    total - c
                )
            }
            4 -> {
                val k = r(3, 9)
                val b = r(2, 9)
                t(
                    "Котёнок за день съедает $k ${f(k, "кусочек", "кусочка", "кусочков")} корма, а щенок — в $b ${raz(b)} больше. " +
                        "Сколько кусочков съедают они вместе за день?",
                    k + k * b
                )
            }
            5 -> {
                val b = r(3, 9)
                val q = r(3, 9)
                val c = r(2, 9)
                val p = b * q
                t(
                    "$p ${f(p, "книгу", "книги", "книг")} расставили поровну на $b ${f(b, "полку", "полки", "полок")}. " +
                        "Потом на каждую полку добавили ещё $c ${f(c, "книгу", "книги", "книг")}. Сколько книг стало на каждой полке?",
                    q + c
                )
            }
            else -> {
                val n = r(3, 9)
                val k = r(6, min(12, 99 / n))
                val total = n * k
                val c = r(3, total / 2)
                t(
                    "Для праздника купили $n ${f(n, "упаковку", "упаковки", "упаковок")} шариков, по $k штук в каждой. " +
                        "$c ${f(c, "шарик лопнул", "шарика лопнули", "шариков лопнули")}. Сколько шариков осталось?",
                    total - c
                )
            }
        }
    }

    private fun mediumProblem(): Task = when (r(1, 10)) {
        1 -> {
            val n = r(2, 6)
            val k = 5 * r(2, 8)
            val total = n * k
            val x = r(max(5, total / 3), total - 1)
            t(
                "В ящик положили $x ${f(x, "грушу", "груши", "груш")}, а в $n ${f(n, "корзину", "корзины", "корзин")} — " +
                    "по $k ${f(k, "груше", "груши", "груш")}. На сколько больше груш в корзинах, чем в ящике?",
                total - x
            )
        }
        2 -> {
            val n = r(3, 9)
            val k = r(4, 9)
            val c = r(5, 30)
            t(
                "Бабушка испекла $n ${f(n, "противень", "противня", "противней")} пирожков, " +
                    "по $k ${f(k, "пирожку", "пирожка", "пирожков")} на каждом, и ещё $c ${f(c, "ватрушку", "ватрушки", "ватрушек")}. " +
                    "Сколько всего пирожков и ватрушек испекла бабушка?",
                n * k + c
            )
        }
        3 -> {
            val n = r(3, 9)
            val k = r(3, min(12, 90 / n))
            val c = r(5, 40)
            t(
                "В саду посадили $n ${f(n, "ряд", "ряда", "рядов")} вишен, по $k ${f(k, "дереву", "дерева", "деревьев")} в каждом ряду. " +
                    "Ещё в саду есть $c ${f(c, "яблоня", "яблони", "яблонь")}. Сколько всего деревьев в саду?",
                n * k + c
            )
        }
        4 -> teamsProblem(r(2, 9), r(2, 9))
        5 -> {
            val n = r(2, 9)
            val k = r(4, 9)
            val total = n * k
            val c = r(2, total - 2)
            t(
                "В $n ${f(n, "коробке", "коробках", "коробках")} по $k ${f(k, "карандашу", "карандаша", "карандашей")}. " +
                    "Ребятам раздали $c ${f(c, "карандаш", "карандаша", "карандашей")}. Сколько карандашей осталось?",
                total - c
            )
        }
        6 -> stickersProblem(r(2, 9), r(2, 9))
        7 -> candiesProblem(r(2, 9), r(2, 9))
        8 -> {
            val a = r(2, 9)
            val b = r(2, 9)
            t(
                "В вазе $a ${f(a, "большое яблоко", "больших яблока", "больших яблок")}, а маленьких в $b ${raz(b)} больше. " +
                    "На сколько больше маленьких яблок, чем больших?",
                a * b - a
            )
        }
        9 -> booksProblem(r(2, 9), r(2, 9))
        else -> kittenProblem(r(2, 9), r(2, 9))
    }

    private fun booksProblem(b: Int, q: Int): Task {
        val p = b * q
        return t(
            "$p ${f(p, "книгу", "книги", "книг")} расставили поровну на $b ${f(b, "полку", "полки", "полок")}. " +
                "Сколько книг на каждой полке?",
            q
        )
    }

    private fun stickersProblem(a: Int, b: Int) = t(
        "У Маши $a ${f(a, "наклейка", "наклейки", "наклеек")}, а у Пети в $b ${raz(b)} больше. Сколько наклеек у Пети?",
        a * b
    )

    private fun candiesProblem(b: Int, q: Int): Task {
        val p = b * q
        return t(
            "У Кати $p ${f(p, "конфета", "конфеты", "конфет")}, это в $b ${raz(b)} больше, чем у Димы. Сколько конфет у Димы?",
            q
        )
    }

    private fun kittenProblem(k: Int, n: Int) = t(
        "Котёнок съедает $k ${f(k, "кусочек", "кусочка", "кусочков")} корма в день. " +
            "Сколько кусочков он съест за $n ${f(n, "день", "дня", "дней")}?",
        k * n
    )

    private fun teamsProblem(n: Int, q: Int): Task {
        val x = n * q
        return t(
            "В классе $x ${f(x, "ученик", "ученика", "учеников")}. Их разделили на $n ${f(n, "равную команду", "равные команды", "равных команд")}. " +
                "Сколько учеников в каждой команде?",
            q
        )
    }

    // ---------- Вспомогательное ----------

    private fun t(text: String, answer: Int) = Task(text, answer.toString())

    private fun r(from: Int, to: Int): Int = Random.nextInt(from, max(from, to) + 1)

    /** «раз» или «раза» после числа: в 2 раза, в 5 раз. */
    private fun raz(n: Int) = f(n, "раз", "раза", "раз")

    /** Форма слова для числа: 1 груша, 2 груши, 5 груш, 21 груша. */
    fun f(n: Int, one: String, few: String, many: String): String {
        val n10 = n % 10
        val n100 = n % 100
        return when {
            n10 == 1 && n100 != 11 -> one
            n10 in 2..4 && n100 !in 12..14 -> few
            else -> many
        }
    }
}
