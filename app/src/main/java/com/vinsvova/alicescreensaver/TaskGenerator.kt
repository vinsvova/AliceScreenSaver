package com.vinsvova.alicescreensaver

import kotlin.math.max
import kotlin.math.min
import kotlin.random.Random

/**
 * Генератор заданий в духе математических диктантов для начальной школы.
 * Все числа случайные, ответы всегда целые и положительные.
 */
object TaskGenerator {

    const val CAT_PLAIN = "plain"       // 7 × 8 =, 56 : 7 =
    const val CAT_WORDS = "words"       // таблица умножения словами
    const val CAT_STEPS = "steps"       // действия в два шага
    const val CAT_COMPARE = "compare"   // сравнение величин м, дм, см
    const val CAT_PROBLEMS = "problems" // текстовые задачи

    val ALL = listOf(CAT_PLAIN, CAT_WORDS, CAT_STEPS, CAT_COMPARE, CAT_PROBLEMS)

    fun generate(count: Int, categories: Set<String>): List<Task> {
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
            val task = one(order[i])
            if (result.none { it.text == task.text }) {
                result.add(task)
                i++
            }
        }
        return result
    }

    fun one(category: String): Task = when (category) {
        CAT_WORDS -> words()
        CAT_STEPS -> steps()
        CAT_COMPARE -> compare()
        CAT_PROBLEMS -> problems()
        else -> plain()
    }

    // ---------- Примеры ----------

    private fun plain(): Task {
        val a = r(2, 9)
        val b = r(2, 9)
        return if (Random.nextBoolean()) {
            Task("$a × $b =", "${a * b}", plain = true)
        } else {
            Task("${a * b} : $b =", "$a", plain = true)
        }
    }

    // ---------- Таблица умножения словами ----------

    private fun words(): Task {
        val a = r(2, 9)
        val b = r(2, 9)
        val p = a * b
        val variants: List<() -> Task> = listOf(
            { t("Увеличь $a в $b ${raz(b)}.", p) },
            { t("Уменьши $p в $b ${raz(b)}.", a) },
            { t("Найди произведение чисел $a и $b.", p) },
            { t("Чему равно произведение чисел $a и $b?", p) },
            { t("Найди частное чисел $p и $b.", a) },
            { t("Чему равно частное чисел $p и $b?", a) },
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
        return variants.random()()
    }

    // ---------- Действия в два шага ----------

    private fun steps(): Task {
        return when (r(1, 9)) {
            1 -> {
                // Сумму чисел раздели на число
                val c = r(2, 9)
                val q = r(3, 9)
                val s = c * q
                val edge = max(2, s / 4)
                val x = r(edge, s - edge)
                t("Сумму чисел $x и ${s - x} раздели на $c.", q)
            }
            2 -> {
                // Разность чисел увеличь в несколько раз
                val c = r(2, 5)
                val d = r(2, min(30, 99 / c))
                val x = r(d + 10, 99)
                t("Разность чисел $x и ${x - d} увеличь в $c ${raz(c)}.", d * c)
            }
            3 -> {
                // Произведение уменьши на частное
                val a = r(2, 6)
                val big = r(11, min(19, 99 / a))
                val n = r(2, 9)
                val q = r(2, 9)
                t("Произведение чисел $big и $a уменьши на частное чисел ${n * q} и $n.", big * a - q)
            }
            4 -> {
                // Частное увеличь на число
                val b = r(2, 9)
                val q = r(2, 9)
                val c = r(10, 60)
                t("Частное чисел ${b * q} и $b увеличь на $c.", q + c)
            }
            5 -> {
                // Из числа вычти произведение
                val a = r(2, 9)
                val b = r(2, 9)
                val x = r(a * b + 1, 100)
                t("Из числа $x вычти произведение чисел $a и $b.", x - a * b)
            }
            6 -> {
                // К произведению прибавь число
                val a = r(2, 9)
                val b = r(2, 9)
                val c = r(10, 40)
                t("К произведению чисел $a и $b прибавь $c.", a * b + c)
            }
            7 -> {
                // Умножение двузначного на однозначное
                val a = r(2, 6)
                val big = r(11, 99 / a)
                t("Найди произведение чисел $a и $big.", a * big)
            }
            8 -> {
                // Деление двузначного на однозначное
                val a = r(2, 6)
                val q = r(11, 99 / a)
                t("Найди частное чисел ${a * q} и $a.", q)
            }
            else -> {
                // Разность чисел раздели на число
                val c = r(2, 9)
                val q = r(2, 9)
                val s = c * q
                val x = r(s + 5, 99)
                t("Разность чисел $x и ${x - s} раздели на $c.", q)
            }
        }
    }

    // ---------- Сравнение величин ----------

    private fun compare(): Task {
        val equal = Random.nextInt(3) == 0
        val (left, right, l, rr) = when (r(1, 5)) {
            1 -> {
                // 5 м 84 см и 580 см
                val m = r(2, 9)
                val cm = r(1, 99)
                val real = m * 100 + cm
                val other = if (equal) real else shifted(real, 1, 30, min = 101)
                Quad("$m м $cm см", "$other см", real, other)
            }
            2 -> {
                // 4 м 8 дм и 44 дм
                val m = r(2, 9)
                val dm = r(1, 9)
                val real = m * 10 + dm
                val other = if (equal) real else shifted(real, 1, 9, min = 11)
                Quad("$m м $dm дм", "$other дм", real, other)
            }
            3 -> {
                // 81 дм и 8 м 1 дм
                val m = r(2, 9)
                val dm = r(1, 9)
                val real = m * 10 + dm
                val other = if (equal) real else shifted(real, 1, 9, min = 11)
                Quad("$other дм", "$m м $dm дм", other, real)
            }
            4 -> {
                // 7 м 6 дм и 7 м
                val m = r(2, 9)
                val dm = r(1, 9)
                val otherM = if (Random.nextBoolean()) m else m + 1
                Quad("$m м $dm дм", "$otherM м", m * 10 + dm, otherM * 10)
            }
            else -> {
                // 5 дм и 48 см
                val dm = r(2, 9)
                val real = dm * 10
                val other = if (equal) real else shifted(real, 1, 9, min = 11)
                Quad("$dm дм", "$other см", real, other)
            }
        }
        val sign = when {
            l > rr -> ">"
            l < rr -> "<"
            else -> "="
        }
        return Task("Поставь знак >, < или =\n$left  ?  $right", sign, sign = true)
    }

    private data class Quad(val left: String, val right: String, val l: Int, val r: Int)

    private fun shifted(value: Int, from: Int, to: Int, min: Int): Int {
        val d = r(from, to)
        val up = value + d
        val down = value - d
        return if (down >= min && Random.nextBoolean()) down else up
    }

    // ---------- Текстовые задачи ----------

    private fun problems(): Task {
        return when (r(1, 10)) {
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
            4 -> {
                val n = r(2, 9)
                val q = r(2, 9)
                val x = n * q
                t(
                    "В классе $x ${f(x, "ученик", "ученика", "учеников")}. Их разделили на $n ${f(n, "равную команду", "равные команды", "равных команд")}. " +
                        "Сколько учеников в каждой команде?",
                    q
                )
            }
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
            6 -> {
                val a = r(2, 9)
                val b = r(2, 9)
                t(
                    "У Маши $a ${f(a, "наклейка", "наклейки", "наклеек")}, а у Пети в $b ${raz(b)} больше. Сколько наклеек у Пети?",
                    a * b
                )
            }
            7 -> {
                val b = r(2, 9)
                val q = r(2, 9)
                val p = b * q
                t(
                    "У Кати $p ${f(p, "конфета", "конфеты", "конфет")}, это в $b ${raz(b)} больше, чем у Димы. Сколько конфет у Димы?",
                    q
                )
            }
            8 -> {
                val a = r(2, 9)
                val b = r(2, 9)
                t(
                    "В вазе $a ${f(a, "большое яблоко", "больших яблока", "больших яблок")}, а маленьких в $b ${raz(b)} больше. " +
                        "На сколько больше маленьких яблок, чем больших?",
                    a * b - a
                )
            }
            9 -> {
                val b = r(2, 9)
                val q = r(2, 9)
                val p = b * q
                t(
                    "$p ${f(p, "книгу", "книги", "книг")} расставили поровну на $b ${f(b, "полку", "полки", "полок")}. " +
                        "Сколько книг на каждой полке?",
                    q
                )
            }
            else -> {
                val k = r(2, 9)
                val n = r(2, 9)
                t(
                    "Котёнок съедает $k ${f(k, "кусочек", "кусочка", "кусочков")} корма в день. " +
                        "Сколько кусочков он съест за $n ${f(n, "день", "дня", "дней")}?",
                    k * n
                )
            }
        }
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
