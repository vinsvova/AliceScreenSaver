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
                    if (task.sign) {
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
                    sb.append(t.text.replace("\n", " / ")).append("   ->   ").append(t.answer).append('\n')
                }
            }
            sb.append('\n')
        }
        val out = File("build/task-samples.txt")
        out.parentFile?.mkdirs()
        out.writeText(sb.toString())
    }
}
