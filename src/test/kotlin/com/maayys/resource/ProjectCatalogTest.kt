package com.maayys.resource

import com.google.gson.JsonObject
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.util.zip.ZipFile

class ProjectCatalogTest {
    @get:Rule val temp = TemporaryFolder()
    private fun upstream(): ProjectCatalog {
        val archive = File("app/src/main/assets/maayys-resources.zip")
        check(archive.isFile) { "Run scripts/prepare-resources.py before testing the Android port" }
        val destination = temp.newFolder()
        ZipFile(archive).use { zip ->
            zip.entries().asSequence().filter { !it.isDirectory && it.name.endsWith(".json") }.forEach {
                val path = File(destination, it.name)
                requireNotNull(path.parentFile).mkdirs()
                zip.getInputStream(it).use { input -> path.outputStream().use { out -> input.copyTo(out) } }
            }
        }
        return ProjectCatalog(destination)
    }
    @Test fun importsRealMaaYysTasksAndResolvesEveryDefaultConfiguration() {
        val catalog = upstream()
        assertTrue(catalog.tasks.size >= 30)
        assertEquals(8, catalog.resources.size)
        catalog.tasks.forEach { task ->
            try { catalog.overrides(task, JsonObject()) }
            catch (e: Exception) { throw AssertionError("Invalid defaults for ${task.get("name")}", e) }
        }
        assertEquals("启动游戏", catalog.task("打开游戏").get("entry").asString)
    }
    @Test fun soulDungeonInputsKeepNumericTypesAndInactiveChoicesDoNotOverride() {
        val catalog = upstream()
        val selections = JsonObject().apply {
            addProperty("御魂挑战目标", "业原火")
            addProperty("是否切换御魂阵容", "No")
            add("自动挑战次数", JsonObject().apply { addProperty("挑战次数", "7") })
            add("御魂阵容预设", JsonObject().apply { addProperty("group_name", "should not apply") })
        }
        val overrides = catalog.overrides(catalog.task("自动御魂"), selections)
        assertEquals("业原火", overrides.getAsJsonObject("自动御魂_点击目标副本").get("expected").asString)
        val number = overrides.getAsJsonObject("自动御魂-设置次数").getAsJsonObject("custom_action_param").get("expected_number")
        assertTrue(number.asJsonPrimitive.isNumber)
        assertEquals(7, number.asInt)
        assertFalse(overrides.has("自动御魂_装备挑战御魂副本御魂"))
    }
    @Test fun rejectsInvalidTaskAndResourceTraversal() {
        val catalog = upstream()
        assertThrows(IllegalArgumentException::class.java) { catalog.resolve("../outside") }
        assertThrows(IllegalStateException::class.java) { catalog.task("not-a-maayys-task") }
    }
}
