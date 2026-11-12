package com.vinsvova.alicescreensaver

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class TaskGeneratorTest {

    private val levels = listOf(TaskGenerator.EASY, TaskGenerator.MEDIUM, TaskGenerator.HARD)

    @Test
    fun answersAreValid() {
        for (level in levels) {
            for (cat in TaskGenerator.ALL) {
                repeat(5000) {
                    val task = TaskGenerator.one(cat, level)
                    val msg = "L$level $cat: ${task.text} -> ${task.answer}"
                    if (task.isNote) {
                        assertEquals(msg, 4, task.options.size)
                        assertEquals(msg, 4, task.options.distinct().size)
                        assertTrue(msg, task.answer in task.options)
                        assertTrue(msg, task.clef == "treble" || task.clef == "bass")
                    } else if (task.sign) {
                        assertTrue(msg, task.answer in setOf(">", "<", "="))
                    } else {
                        val n = task.answer.toIntOrNull()
                        assertTrue(msg, n != null && n in 1..999)
                    }
                    assertTrue(task.text.isNotBlank())
                }
            }
        }
    }

    /** Проверяем арифметику в примерах вида «a + b − c =», «a × b =», «a : b =». */
    @Test
    fun plainExpressionsAreCorrect() {
        val re = Regex("""^(\d+) ([+−×:]) (\d+)(?: ([+−]) (\d+))? =$""")
        for (level in levels) {
            for (cat in listOf(TaskGenerator.CAT_PLAIN, TaskGenerator.CAT_EXTRA, TaskGenerator.CAT_ADDSUB)) {
                repeat(5000) {
                    val task = TaskGenerator.one(cat, level)
                    val m = re.matchEntire(task.text) ?: return@repeat
                    fun op(x: Int, o: String, y: Int): Int = when (o) {
                        "+" -> x + y
                        "−" -> x - y
                        "×" -> x * y
                        else -> {
                            assertEquals("остаток в ${task.text}", 0, x % y)
                            x / y
                        }
                    }
                    var v = op(m.groupValues[1].toInt(), m.groupValues[2], m.groupValues[3].toInt())
                    if (m.groupValues[4].isNotEmpty()) v = op(v, m.groupValues[4], m.groupValues[5].toInt())
                    assertEquals(task.text, v.toString(), task.answer)
                    assertTrue("больше 100: ${task.text}", v in 1..100)
                }
            }
        }
    }

    /** Нота на стане соответствует правильному ответу. */
    @Test
    fun notesMatchStaff() {
        val names = listOf("до", "ре", "ми", "фа", "соль", "ля", "си")
        for (level in levels) {
            repeat(3000) {
                val t = TaskGenerator.one(TaskGenerator.CAT_NOTES, level)
                val bottom = if (t.clef == "treble") 4 * 7 + 2 else 2 * 7 + 4
                val d = bottom + t.pos
                assertTrue("вне октав: $d", d in 14..41)
                assertEquals(names[d % 7], t.answer.substringBefore('\n'))
                if (level == TaskGenerator.EASY) assertTrue(t.pos in 0..8)
            }
        }
        // Конкретные ноты: до первой октавы в скрипичном ключе — первая добавочная снизу.
        assertEquals("до\nпервой октавы", TaskGenerator.noteLabel(4 * 7 + 2 - 2, true))
        assertEquals("соль\nбольшой октавы", TaskGenerator.noteLabel(2 * 7 + 4, true))
        assertEquals("фа\nмалой октавы", TaskGenerator.noteLabel(2 * 7 + 4 + 6, true))
    }

    @Test
    fun replacementIsDifferent() {
        for (cat in TaskGenerator.ALL) {
            repeat(200) {
                val t = TaskGenerator.one(cat, TaskGenerator.MEDIUM)
                val r = TaskGenerator.replacement(t)
                assertEquals(cat, r.cat)
            }
        }
    }

    @Test
    fun countIsRespected() {
        for (level in levels) {
            for (count in 1..10) {
                val list = TaskGenerator.generate(count, TaskGenerator.ALL.toSet(), level)
                assertEquals(count, list.size)
                assertEquals(count, list.map { it.text }.distinct().size)
            }
        }
        val only = TaskGenerator.generate(5, setOf(TaskGenerator.CAT_WORDS), TaskGenerator.EASY)
        assertEquals(5, only.size)
    }

    @Test
    fun plurals() {
        val f = TaskGenerator::f
        assertEquals("груша", f(1, "груша", "груши", "груш"))
        assertEquals("груши", f(3, "груша", "груши", "груш"))
        assertEquals("груш", f(5, "груша", "груши", "груш"))
        assertEquals("груш", f(11, "груша", "груши", "груш"))
        assertEquals("груш", f(12, "груша", "груши", "груш"))
        assertEquals("груша", f(21, "груша", "груши", "груш"))
        assertEquals("груши", f(34, "груша", "груши", "груш"))
    }

    /** Образцы заданий для просмотра глазами (выводятся в лог сборки). */
    @Test
    fun writeSamples() {
        val sb = StringBuilder()
        for (cat in TaskGenerator.ALL) {
            for (level in levels) {
                sb.append("=== ").append(cat).append(" / уровень ").append(level).append(" ===\n")
                repeat(12) {
                    val t = TaskGenerator.one(cat, level)
                    val extra = if (t.isNote) " [${t.clef} pos=${t.pos}] варианты: ${t.options.joinToString(" | ") { it.replace("\n", " ") }}" else ""
                    sb.append(t.text.replace("\n", " / ")).append(extra).append("   ->   ").append(t.answer.replace("\n", " ")).append('\n')
                }
            }
            sb.append('\n')
        }
        val out = File("build/task-samples.txt")
        out.parentFile?.mkdirs()
        out.writeText(sb.toString())
    }
}
