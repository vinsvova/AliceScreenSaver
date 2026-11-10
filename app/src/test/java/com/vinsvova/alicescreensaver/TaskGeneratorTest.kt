package com.vinsvova.alicescreensaver

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class TaskGeneratorTest {

    @Test
    fun answersAreValid() {
        for (cat in TaskGenerator.ALL) {
            repeat(5000) {
                val task = TaskGenerator.one(cat)
                if (task.sign) {
                    assertTrue("$cat: ${task.text} -> ${task.answer}", task.answer in setOf(">", "<", "="))
                } else {
                    val n = task.answer.toIntOrNull()
                    assertTrue("$cat: ${task.text} -> ${task.answer}", n != null && n in 1..999)
                }
                assertTrue(task.text.isNotBlank())
            }
        }
    }

    @Test
    fun countIsRespected() {
        for (count in 1..10) {
            val list = TaskGenerator.generate(count, TaskGenerator.ALL.toSet())
            assertEquals(count, list.size)
            assertEquals(count, list.map { it.text }.distinct().size)
        }
        val only = TaskGenerator.generate(5, setOf(TaskGenerator.CAT_WORDS))
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

    /** Образцы заданий для просмотра глазами (выкладываются в артефакты сборки). */
    @Test
    fun writeSamples() {
        val sb = StringBuilder()
        for (cat in TaskGenerator.ALL) {
            sb.append("=== ").append(cat).append(" ===\n")
            repeat(40) {
                val t = TaskGenerator.one(cat)
                sb.append(t.text.replace("\n", " / ")).append("   ->   ").append(t.answer).append('\n')
            }
            sb.append('\n')
        }
        val out = File("build/task-samples.txt")
        out.parentFile?.mkdirs()
        out.writeText(sb.toString())
    }
}
