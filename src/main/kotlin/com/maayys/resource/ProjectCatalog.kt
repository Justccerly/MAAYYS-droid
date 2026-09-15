package com.maayys.resource

import com.google.gson.*
import java.io.File

/** Reads MaaYYs ProjectInterface imports; never substitutes Android-specific tasks. */
class ProjectCatalog(val root: File) {
    val document: JsonObject = JsonParser.parseString(File(root, "interface.json").readText()).asJsonObject
    val version: String = document.get("version").asString
    val resources: List<JsonObject> = document.getAsJsonArray("resource").map { it.asJsonObject }
    val tasks = mutableListOf<JsonObject>()
    val options = JsonObject()
    val presets = mutableListOf<JsonObject>()

    init {
        val loaded = HashSet<String>()
        fun load(file: File) {
            require(loaded.size < 1000) { "Too many project imports" }
            if (!loaded.add(file.canonicalPath)) return
            val doc = JsonParser.parseString(file.readText()).asJsonObject
            doc.getAsJsonArray("task")?.forEach { tasks += it.asJsonObject }
            doc.getAsJsonObject("option")?.entrySet()?.forEach { (k, v) -> options.add(k, v) }
            doc.getAsJsonArray("preset")?.forEach { presets += it.asJsonObject }
            doc.getAsJsonArray("import")?.forEach { load(resolve(it.asString)) }
        }
        load(resolve("interface.json"))
        require(tasks.map { it.get("name").asString }.distinct().size == tasks.size) { "Duplicate task names" }
    }

    fun resolve(path: String): File {
        require(!File(path).isAbsolute && !path.contains('\\') && !path.contains(':')) { "Invalid resource path" }
        val file = File(root, path).canonicalFile
        require(file.path.startsWith(root.canonicalPath + File.separator)) { "Resource escapes installation" }
        return file
    }

    fun resourcePaths(index: Int): List<File> = resources[index].getAsJsonArray("path").map {
        resolve(it.asString).also { path -> require(path.isDirectory) { "Missing resource: $path" } }
    }

    fun task(name: String): JsonObject = tasks.firstOrNull { it.get("name").asString == name }
        ?: error("Unknown MaaYYs task: $name")

    fun gamePackage(resourceIndex: Int): String {
        var pkg: String? = null
        resourcePaths(resourceIndex).forEach { directory ->
            File(directory, "pipeline").walkTopDown().filter { it.isFile && it.extension == "json" }.forEach { file ->
                val node = JsonParser.parseString(file.readText()).asJsonObject.getAsJsonObject("启动游戏")
                node?.get("package")?.asString?.let { pkg = it }
            }
        }
        return requireNotNull(pkg) { "所选资源没有提供阴阳师包名" }
    }

    /** Recursive JSON-object merge is the ProjectInterface pipeline_override rule. */
    fun overrides(task: JsonObject, selections: JsonObject): JsonObject {
        val result = JsonObject()
        fun merge(target: JsonObject, source: JsonObject) {
            source.entrySet().forEach { (key, value) ->
                val old = target.get(key)
                if (old?.isJsonObject == true && value.isJsonObject) merge(old.asJsonObject, value.asJsonObject)
                else target.add(key, value.deepCopy())
            }
        }
        task.getAsJsonObject("pipeline_override")?.let { merge(result, it) }
        fun applyOption(name: String, ancestors: Set<String>) {
            require(name !in ancestors) { "Circular option: $name" }
            val option = options.getAsJsonObject(name) ?: error("Unknown option: $name")
            when (option.get("type")?.asString ?: "select") {
                "input" -> {
                    val values = selections.get(name)?.takeIf { it.isJsonObject }?.asJsonObject ?: JsonObject()
                    val replacements = HashMap<String, JsonElement>()
                    option.getAsJsonArray("inputs").forEach { field ->
                        val f = field.asJsonObject
                        val key = f.get("name").asString
                        val text = (values.get(key) ?: f.get("default") ?: JsonPrimitive("")).asString
                        val pattern = f.get("verify")?.asString
                        require(pattern == null || Regex(pattern).matches(text)) { "Invalid value: $name / $key" }
                        replacements[key] = when (f.get("pipeline_type")?.asString ?: "string") {
                            "int" -> JsonPrimitive(text.toLong())
                            "float" -> JsonPrimitive(text.toDouble().also { require(it.isFinite()) })
                            "bool" -> JsonPrimitive(text.toBooleanStrict())
                            "string" -> JsonPrimitive(text)
                            else -> error("Unsupported input type: ${f.get("pipeline_type")}")
                        }
                    }
                    fun substitute(value: JsonElement): JsonElement = when {
                        value.isJsonObject -> JsonObject().apply { value.asJsonObject.entrySet().forEach { (k, v) -> add(k, substitute(v)) } }
                        value.isJsonArray -> JsonArray().apply { value.asJsonArray.forEach { add(substitute(it)) } }
                        value.isJsonPrimitive && value.asJsonPrimitive.isString -> {
                            val text = value.asString
                            replacements.entries.firstOrNull { text == "{${it.key}}" }?.value?.deepCopy()
                                ?: JsonPrimitive(replacements.entries.fold(text) { s, (k, v) -> s.replace("{$k}", v.asString) })
                        }
                        else -> value.deepCopy()
                    }
                    option.getAsJsonObject("pipeline_override")?.let { merge(result, substitute(it).asJsonObject) }
                }
                "select", "switch", "checkbox" -> {
                    val cases = option.getAsJsonArray("cases") ?: error("Missing cases: $name")
                    val value = selections.get(name) ?: option.get("default_case")
                    val choices = when {
                        value?.isJsonArray == true -> value.asJsonArray.map { it.asString }
                        value != null -> listOf(value.asString)
                        option.get("type")?.asString == "checkbox" -> emptyList()
                        else -> listOf(cases.first().asJsonObject.get("name").asString)
                    }
                    choices.forEach { choice ->
                        val selected = cases.map { it.asJsonObject }.firstOrNull { it.get("name").asString == choice }
                            ?: error("Unknown case: $name / $choice")
                        selected.getAsJsonObject("pipeline_override")?.let { merge(result, it) }
                        selected.getAsJsonArray("option")?.forEach { applyOption(it.asString, ancestors + name) }
                    }
                }
                else -> error("Unsupported option type: ${option.get("type")}")
            }
        }
        task.getAsJsonArray("option")?.forEach { applyOption(it.asString, emptySet()) }
        return result
    }
}
