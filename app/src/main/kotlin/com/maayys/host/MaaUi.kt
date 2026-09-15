@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
package com.maayys.host

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.view.Surface
import android.view.TextureView
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.automirrored.rounded.ListAlt
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.*
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.google.gson.*
import com.maayys.app.ControllerMode
import com.maayys.platform.BackgroundConnection
import com.aliothmoon.maameow.theme.MaaDesignTokens
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.concurrent.Executors

private val Blue = Color(0xFF2B6BCA)
private val CardShape = RoundedCornerShape(MaaDesignTokens.CornerRadius.card)
private val pageInsets = PaddingValues(horizontal = 20.dp, vertical = 12.dp)

@Composable fun MaaTheme(mode: String, content: @Composable () -> Unit) {
    val dark = mode == "dark" || mode == "system" && isSystemInDarkTheme()
    // Palette and spacing follow MAA-Meow Theme.kt and DesignTokens.kt.
    val colors = if (dark) darkColorScheme(primary = Color(0xFF9ABEFF), onPrimary = Color(0xFF092C59),
        secondary = Color(0xFF9ABEFF), secondaryContainer = Color(0xFF173C6B), onSecondaryContainer = Color(0xFFD8E7FF),
        primaryContainer = Color(0xFF173C6B), onPrimaryContainer = Color(0xFFD8E7FF),
        background = Color(0xFF121212), surface = Color(0xFF1C1C1E), surfaceVariant = Color(0xFF2C2C2E),
        onSurface = Color(0xFFF4F3F0), onSurfaceVariant = Color(0xFFA4A3AB), outlineVariant = Color(0xFF343438))
    else lightColorScheme(primary = Blue, onPrimary = Color.White, primaryContainer = Color(0xFFE5EFFF),
        secondary = Blue, secondaryContainer = Color(0xFFE5EFFF), onSecondaryContainer = Color(0xFF183E72),
        onPrimaryContainer = Color(0xFF183E72), background = Color(0xFFF5F2ED), surface = Color(0xFFFEFCF8),
        surfaceVariant = Color(0xFFECE8E2), onSurface = Color(0xFF252831), onSurfaceVariant = Color(0xFF7B7E87),
        outlineVariant = Color(0xFFE8E4DE))
    val view = androidx.compose.ui.platform.LocalView.current
    SideEffect {
        (view.context as? android.app.Activity)?.window?.let {
            androidx.core.view.WindowInsetsControllerCompat(it, view).isAppearanceLightStatusBars = !dark
            androidx.core.view.WindowInsetsControllerCompat(it, view).isAppearanceLightNavigationBars = !dark
        }
    }
    MaterialTheme(colorScheme = colors, shapes = Shapes(medium = CardShape, large = RoundedCornerShape(20.dp)), content = content)
}

private data class Destination(val title: String, val icon: ImageVector)
private val destinations = listOf(Destination("首页", Icons.Rounded.Home), Destination("任务", Icons.AutoMirrored.Rounded.ListAlt),
    Destination("后台", Icons.Rounded.DesktopWindows), Destination("定时", Icons.Rounded.Schedule), Destination("设置", Icons.Rounded.Settings))

@Composable fun MaaApp(model: AppModel) {
    MaaTheme(model.theme) {
        var channelPicker by remember { mutableStateOf(false) }
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val wide = maxWidth >= 840.dp
            Row(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
                if (wide) NavigationRail(containerColor = MaterialTheme.colorScheme.surface, modifier = Modifier.fillMaxHeight(), header = {
                    Image(painterResource(R.drawable.maayys_logo), "MaaYYs", Modifier.padding(vertical = 20.dp).size(40.dp).clip(CircleShape))
                }) { destinations.forEachIndexed { index, page -> NavigationRailItem(selected = model.tab == index, onClick = { model.tab = index }, icon = { Icon(page.icon, page.title) }, label = { Text(page.title) }, modifier = Modifier.padding(vertical = 8.dp)) } }
                Scaffold(modifier = Modifier.weight(1f), containerColor = MaterialTheme.colorScheme.background,
                    topBar = {
                        TopAppBar(title = { Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            if (!wide) Image(painterResource(R.drawable.maayys_logo), null, Modifier.size(32.dp).clip(RoundedCornerShape(10.dp)))
                            Text(if (model.tab == 0) "MaaYYs" else destinations[model.tab].title, fontWeight = FontWeight.Bold)
                        } }, actions = { IconButton(onClick = { model.showLogs = true }) { Icon(Icons.Rounded.ReceiptLong, "运行日志") } },
                            colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background))
                    }, bottomBar = {
                        if (!wide) NavigationBar(containerColor = MaterialTheme.colorScheme.surface, tonalElevation = 0.dp) {
                            destinations.forEachIndexed { index, page -> NavigationBarItem(selected = model.tab == index, onClick = { model.tab = index }, icon = { Icon(page.icon, page.title) }, label = { Text(page.title) }, colors = NavigationBarItemDefaults.colors(indicatorColor = MaterialTheme.colorScheme.primaryContainer)) }
                        }
                    }
                ) { insets ->
                    Box(Modifier.padding(insets).fillMaxSize(), contentAlignment = Alignment.TopCenter) {
                        Box(Modifier.widthIn(max = 1080.dp).fillMaxSize()) {
                            when (model.tab) {
                                0 -> HomePage(model) { channelPicker = true }
                                1 -> TasksPage(model)
                                2 -> BackgroundPage(model)
                                3 -> SchedulePage(model)
                                else -> SettingsPage(model) { channelPicker = true }
                            }
                        }
                    }
                }
            }
        }
        if (channelPicker) AlertDialog(onDismissRequest = { channelPicker = false }, title = { Text("选择游戏区服") },
            text = { Column(Modifier.verticalScroll(rememberScrollState())) {
                Text("请选择手机上安装的阴阳师客户端。", color = MaterialTheme.colorScheme.onSurfaceVariant)
                model.catalog?.resources?.forEachIndexed { index, item ->
                    Row(Modifier.fillMaxWidth().clickable(enabled = !model.running && !model.backgroundActive) { model.selectChannel(index); channelPicker = false }.padding(vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(selected = model.resourceIndex == index, onClick = null)
                        Text(item.get("label")?.asString ?: item.get("name").asString, Modifier.padding(start = 12.dp))
                    }
                }
                if (model.backgroundActive || model.running) Text("关闭运行中的任务和后台游戏后可更换区服。", style = MaterialTheme.typography.bodySmall)
            } }, confirmButton = { TextButton(onClick = { channelPicker = false }) { Text("完成") } })
        model.taskToConfigure?.let { TaskSheet(model, it) }
        if (model.showLogs) LogSheet(model)
        model.error?.let { message -> AlertDialog(onDismissRequest = { model.error = null }, icon = { Icon(Icons.Rounded.Info, null) }, title = { Text("需要处理一下") }, text = { Text(message, Modifier.heightIn(max = 300.dp).verticalScroll(rememberScrollState())) }, confirmButton = { TextButton(onClick = { model.error = null }) { Text("知道了") } }, dismissButton = { TextButton(onClick = { model.error = null; model.showLogs = true }) { Text("查看日志") } }) }
    }
}

@Composable private fun PageColumn(content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(pageInsets), verticalArrangement = Arrangement.spacedBy(20.dp), content = content)
}
@Composable private fun SectionTitle(title: String, caption: String? = null, action: (@Composable () -> Unit)? = null) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) { Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold); caption?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 4.dp)) } }
        action?.invoke()
    }
}
@Composable private fun IconTile(icon: ImageVector, modifier: Modifier = Modifier) {
    Box(modifier.size(42.dp).clip(RoundedCornerShape(13.dp)).background(MaterialTheme.colorScheme.primaryContainer), contentAlignment = Alignment.Center) { Icon(icon, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(22.dp)) }
}
@Composable private fun StatusPill(text: String, active: Boolean = false) {
    Surface(shape = CircleShape, color = if (active) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant) {
        Row(Modifier.padding(horizontal = 10.dp, vertical = 5.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Box(Modifier.size(6.dp).background(if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant, CircleShape))
            Text(text, style = MaterialTheme.typography.labelMedium, color = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
@Composable private fun SettingRow(icon: ImageVector, title: String, detail: String, onClick: (() -> Unit)? = null, trailing: (@Composable () -> Unit)? = null) {
    Row(Modifier.fillMaxWidth().then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier).padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Icon(icon, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(22.dp))
        Column(Modifier.weight(1f)) { Text(title, style = MaterialTheme.typography.bodyLarge); Text(detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 4.dp)) }
        if (trailing != null) trailing() else if (onClick != null) Icon(Icons.Rounded.ChevronRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable private fun HomePage(model: AppModel, chooseChannel: () -> Unit) = PageColumn {
    Card(shape = RoundedCornerShape(22.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
        Row(Modifier.fillMaxWidth().padding(22.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("阴阳师 · 日常助手", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                Text("庭院日常\n轻松安排", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onPrimaryContainer)
                Text("日常、副本与活动，一处管理", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = .65f))
            }
            Image(painterResource(R.drawable.maayys_logo), "MaaYYs 阴阳师头像", Modifier.size(106.dp).clip(RoundedCornerShape(22.dp)))
        }
    }
    Card(shape = CardShape, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) { Text("运行状态", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant); Text(model.status, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 6.dp)) }
                if (model.loading) CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp) else StatusPill(if (model.running) "进行中" else "空闲", model.running)
            }
            if (model.running) { Text(model.currentTask.orEmpty(), style = MaterialTheme.typography.bodyMedium); LinearProgressIndicator(Modifier.fillMaxWidth()) }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Column(Modifier.weight(1f).clickable { model.tab = 3 }) { Text("控制方式", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant); Text(model.mode?.name ?: "待配置", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 6.dp)) }
                Column(Modifier.weight(1f).clickable(onClick = chooseChannel)) { Text("游戏区服", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant); Text(model.channel, style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 6.dp)) }
                Column(Modifier.weight(1f).clickable { model.tab = 2 }) { Text("运行模式", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant); Text(if (model.backgroundMode) "后台运行" else "前台运行", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 6.dp)) }
            }
            Button(onClick = { if (model.running) model.stopTask() else model.tab = 1 }, enabled = !model.loading, modifier = Modifier.fillMaxWidth().height(48.dp), shape = RoundedCornerShape(12.dp)) {
                Icon(if (model.running) Icons.Rounded.Stop else Icons.Rounded.PlayArrow, null); Spacer(Modifier.width(8.dp)); Text(if (model.running) "停止当前任务" else "选择任务", fontWeight = FontWeight.SemiBold)
            }
        }
    }
    SectionTitle("常用任务", "从熟悉的日常开始") { TextButton(onClick = { model.tab = 1 }) { Text("全部任务"); Icon(Icons.Rounded.ChevronRight, null, Modifier.size(18.dp)) } }
    val quick = listOf("打开游戏", "自动御魂", "日常奖励领取", "结界突破").mapNotNull { name -> model.catalog?.tasks?.firstOrNull { it.get("name").asString == name || it.get("name").asString.contains(name) } }
    quick.chunked(2).forEach { pair -> Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        pair.forEachIndexed { index, task -> Card(onClick = { model.configure(task) }, modifier = Modifier.weight(1f), shape = CardShape, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                IconTile(if (index == 0) Icons.Rounded.AutoAwesome else Icons.Rounded.SportsEsports)
                Text(task.get("label")?.asString ?: task.get("name").asString, fontWeight = FontWeight.Medium)
                Text("配置并开始", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } }
        if (pair.size == 1) Spacer(Modifier.weight(1f))
    } }
    Card(onClick = { model.tab = 2 }, shape = CardShape, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        SettingRow(Icons.Rounded.PictureInPictureAlt, "让游戏在后台运行", "独立游戏空间，前台继续使用手机", trailing = { Icon(Icons.AutoMirrored.Rounded.ArrowForward, null, tint = MaterialTheme.colorScheme.primary) })
    }
    Text("MaaYYs · ${model.catalog?.version ?: "正在准备资源"}", Modifier.align(Alignment.CenterHorizontally).padding(bottom = 8.dp), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    ResourceUpdateCard(model)
}

@Composable private fun ResourceUpdateCard(model: AppModel) {
    var checked by rememberSaveable { mutableStateOf(false) }
    Card(shape = CardShape, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        SettingRow(Icons.Rounded.SystemUpdate, "MaaYYs 资源", model.updateMessage ?: "${model.catalog?.version ?: "加载中"} · 包含任务、模板和模型", trailing = {
            TextButton(onClick = { checked = true; model.checkMirrorUpdate() }, enabled = !model.loading && !model.resourceUpdateBusy) { Text(if (model.resourceUpdateBusy) "检查中…" else if (checked) "再次检查" else "检查更新") }
        })
    }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        Text("更新来源", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        FilterChip(selected = model.updateSource == "mirror", onClick = { model.selectUpdateSource("mirror") }, label = { Text("Mirror酱") })
        FilterChip(selected = model.updateSource == "github", onClick = { model.selectUpdateSource("github") }, label = { Text("GitHub") })
    }
    OutlinedTextField(value = model.mirrorCdk, onValueChange = model::saveMirrorCdk, label = { Text("Mirror酱 CDK（仅下载时需要）") }, placeholder = { Text("不填写也可以检查版本") }, singleLine = true, shape = RoundedCornerShape(10.dp), modifier = Modifier.fillMaxWidth())
    Card(shape = CardShape, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        SettingRow(Icons.Rounded.Memory, "MaaFramework Core", model.coreUpdateMessage ?: "当前内置 Core：5.13.0 · ${if (model.updateSource == "mirror") "Mirror酱" else "GitHub"}", trailing = {
            TextButton(onClick = model::checkCoreUpdate, enabled = !model.loading && !model.coreUpdateBusy) { Text(if (model.coreUpdateBusy) "检查中…" else "检查更新") }
        })
    }
}

@Composable private fun TasksPage(model: AppModel) {
    var query by rememberSaveable { mutableStateOf("") }; var group by rememberSaveable { mutableStateOf("all") }
    val groups = listOf("all" to "全部", "daily" to "日常", "standalone" to "副本", "promotion" to "活动")
    val tasks = model.catalog?.tasks.orEmpty().filter { task ->
        (query.isBlank() || task.get("name").asString.contains(query, true) || task.get("label")?.asString?.contains(query, true) == true) &&
            (group == "all" || task.getAsJsonArray("group")?.any { it.asString == group } == true)
    }
    LazyColumn(contentPadding = pageInsets, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item { Text("你的阴阳师任务", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold) }
        item { Text("${model.catalog?.tasks?.size ?: 0} 项任务 · ${model.channel}", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        item { OutlinedTextField(value = query, onValueChange = { query = it }, leadingIcon = { Icon(Icons.Rounded.Search, null) }, placeholder = { Text("搜索任务") }, singleLine = true, shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth()) }
        item { Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) { groups.forEach { (key, label) -> FilterChip(selected = group == key, onClick = { group = key }, label = { Text(label) }) } } }
        items(tasks, key = { it.get("name").asString }) { task ->
            Card(onClick = { model.configure(task) }, shape = CardShape, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    IconTile(when (task.getAsJsonArray("group")?.firstOrNull()?.asString) { "promotion" -> Icons.Rounded.Celebration; "standalone" -> Icons.Rounded.SportsEsports; else -> Icons.Rounded.AutoAwesome })
                    Column(Modifier.weight(1f)) {
                        Text(task.get("label")?.asString ?: task.get("name").asString, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Medium)
                        Text(task.get("description")?.asString?.replace(Regex("<[^>]+>"), "")?.take(70) ?: "${task.getAsJsonArray("option")?.size() ?: 0} 项可配置选项", maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 5.dp))
                    }
                    Switch(checked = model.taskEnabled(task.get("name").asString), onCheckedChange = { model.toggleTaskEnabled(task.get("name").asString, it) })
                }
            }
        }
        if (tasks.isEmpty()) item { Text(if (model.loading) "正在加载任务…" else "没有找到匹配的任务", Modifier.padding(24.dp), color = MaterialTheme.colorScheme.onSurfaceVariant) }
    }
}

@Composable private fun TaskSheet(model: AppModel, task: JsonObject) {
    ModalBottomSheet(onDismissRequest = { model.taskToConfigure = null }, containerColor = MaterialTheme.colorScheme.background) {
        Column(Modifier.fillMaxWidth().fillMaxHeight(.9f).padding(horizontal = 20.dp)) {
            Text(task.get("label")?.asString ?: task.get("name").asString, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Text("${model.channel} · ${if (model.backgroundMode) "后台运行" else "前台运行"}", Modifier.padding(top = 8.dp, bottom = 18.dp), color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                task.get("description")?.asString?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
                val names = task.getAsJsonArray("option")
                if (names == null || names.size() == 0) Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) { Text("这项任务无需额外配置。", Modifier.padding(20.dp)) }
                names?.forEach { OptionEditor(model, it.asString, emptySet()) }
                Spacer(Modifier.height(8.dp))
            }
            Button(onClick = model::startTask, enabled = !model.running && !model.loading, modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp).height(50.dp), shape = RoundedCornerShape(12.dp)) { Icon(Icons.Rounded.PlayArrow, null); Spacer(Modifier.width(8.dp)); Text(if (model.running) "已有任务运行中" else "开始任务", fontWeight = FontWeight.SemiBold) }
        }
    }
}

@Composable private fun OptionEditor(model: AppModel, name: String, ancestors: Set<String>) {
    if (name in ancestors) return
    val option = model.catalog?.options?.getAsJsonObject(name) ?: return
    Card(shape = CardShape, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(option.get("label")?.asString ?: name, fontWeight = FontWeight.Medium, style = MaterialTheme.typography.titleSmall)
            option.get("description")?.asString?.let { Text(it, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall) }
            when (option.get("type")?.asString ?: "select") {
                "input" -> option.getAsJsonArray("inputs").forEach { item ->
                    val field = item.asJsonObject; val key = field.get("name").asString
                    val values = model.selections.get(name)?.takeIf { it.isJsonObject }?.asJsonObject ?: JsonObject()
                    OutlinedTextField(value = (values.get(key) ?: field.get("default") ?: JsonPrimitive("")).asString,
                        onValueChange = { model.choose(name, values.deepCopy().apply { addProperty(key, it) }) }, label = { Text(field.get("label")?.asString ?: key) }, singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = if (field.get("pipeline_type")?.asString == "int") KeyboardType.Number else KeyboardType.Text), shape = RoundedCornerShape(10.dp), modifier = Modifier.fillMaxWidth())
                }
                "checkbox" -> {
                    val value = model.selections.get(name) ?: option.get("default_case")
                    val choices = when { value?.isJsonArray == true -> value.asJsonArray.map { it.asString }; value != null -> listOf(value.asString); else -> emptyList() }
                    option.getAsJsonArray("cases").forEach { item ->
                        val choice = item.asJsonObject; val key = choice.get("name").asString
                        Row(Modifier.fillMaxWidth().clickable { model.choose(name, JsonArray().apply { (if (key in choices) choices - key else choices + key).forEach { add(it) } }) }, verticalAlignment = Alignment.CenterVertically) { Checkbox(checked = key in choices, onCheckedChange = null); Text(choice.get("label")?.asString ?: key, Modifier.padding(start = 10.dp)) }
                        if (key in choices) choice.getAsJsonArray("option")?.forEach { OptionEditor(model, it.asString, ancestors + name) }
                    }
                }
                else -> {
                    val cases = option.getAsJsonArray("cases").map { it.asJsonObject }
                    val selected = model.selections.get(name)?.asString ?: option.get("default_case")?.asString ?: cases.first().get("name").asString
                    val choice = cases.firstOrNull { it.get("name").asString == selected } ?: cases.first()
                    if (option.get("type")?.asString == "switch" && cases.size == 2 && cases.map { it.get("name").asString }.toSet() == setOf("Yes", "No")) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Text(if (selected == "Yes") "已开启" else "已关闭", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Switch(checked = selected == "Yes", onCheckedChange = { model.choose(name, JsonPrimitive(if (it) "Yes" else "No")) })
                        }
                    } else {
                        var expanded by remember { mutableStateOf(false) }
                        Box {
                            OutlinedButton(onClick = { expanded = true }, shape = RoundedCornerShape(10.dp), modifier = Modifier.fillMaxWidth()) { Text(choice.get("label")?.asString ?: selected, Modifier.weight(1f)); Icon(Icons.Rounded.ExpandMore, null) }
                            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }, modifier = Modifier.heightIn(max = 340.dp)) { cases.forEach { case -> DropdownMenuItem(text = { Text(case.get("label")?.asString ?: case.get("name").asString) }, onClick = { model.choose(name, case.get("name")); expanded = false }) } }
                        }
                    }
                    choice.getAsJsonArray("option")?.forEach { OptionEditor(model, it.asString, ancestors + name) }
                }
            }
        }
    }
}

@Composable private fun BackgroundPage(model: AppModel) = PageColumn {
    SectionTitle("游戏在后台，手机留给你", "独立运行 · 实时预览 · 手动接管")
    Card(shape = RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(containerColor = Color(0xFF171C26))) {
        Box(Modifier.fillMaxWidth().aspectRatio(16f / 9f).heightIn(max = 360.dp), contentAlignment = Alignment.Center) {
            if (model.backgroundActive) BackgroundPreview() else Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Icon(Icons.Rounded.DesktopWindows, null, Modifier.size(44.dp), tint = Color(0xFF7C8A9F))
                Text(if (model.backgroundBusy) "正在准备后台空间…" else "后台游戏尚未启动", color = Color(0xFFDDE4EF), fontWeight = FontWeight.Medium)
                if (model.backgroundBusy) CircularProgressIndicator(Modifier.size(22.dp), color = Color(0xFF9ABEFF), strokeWidth = 2.dp)
                else Text("启动后，阴阳师画面将显示在这里", style = MaterialTheme.typography.bodySmall, color = Color(0xFF8794A8))
            }
        }
    }
    Card(shape = CardShape, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        SettingRow(Icons.Rounded.SportsEsports, "阴阳师 · ${model.channel}", if (model.backgroundActive) "游戏空间正在运行" else "${model.mode?.name ?: "尚未选择控制方式"} · 1280 × 720", trailing = { StatusPill(if (model.backgroundActive) "已启动" else "待启动", model.backgroundActive) })
        HorizontalDivider(Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.outlineVariant)
        SettingRow(Icons.Rounded.Layers, "任务在后台执行", "任务识别和触控仅作用于游戏空间", trailing = { Switch(checked = model.backgroundMode, onCheckedChange = model::setBackground, enabled = !model.running) })
    }
    if (!model.backgroundActive) Button(onClick = model::openBackground, enabled = !model.loading && !model.backgroundBusy, modifier = Modifier.fillMaxWidth().height(50.dp), shape = RoundedCornerShape(12.dp)) { Icon(Icons.Rounded.PlayArrow, null); Spacer(Modifier.width(8.dp)); Text("启动后台游戏", fontWeight = FontWeight.SemiBold) }
    else {
        Button(onClick = { if (model.running) model.stopTask() else model.startSavedTask() }, enabled = !model.backgroundBusy && !model.loading, modifier = Modifier.fillMaxWidth().height(50.dp), shape = RoundedCornerShape(12.dp)) { Icon(if (model.running) Icons.Rounded.Stop else Icons.Rounded.PlayArrow, null); Spacer(Modifier.width(8.dp)); Text(if (model.running) "停止任务" else "开始任务", fontWeight = FontWeight.SemiBold) }
        Text(model.savedTask()?.get("label")?.asString?.let { "当前任务：$it" } ?: "尚未在任务页配置自动化任务", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedButton(onClick = { model.closeBackground(true) }, enabled = !model.backgroundBusy, modifier = Modifier.weight(1f), shape = RoundedCornerShape(12.dp)) { Text("返回前台") }
            OutlinedButton(onClick = { model.closeBackground() }, enabled = !model.backgroundBusy, modifier = Modifier.weight(1f), shape = RoundedCornerShape(12.dp)) { Text("关闭后台", color = MaterialTheme.colorScheme.error) }
        }
    }
    Text("需要 Root 或已启动并授权的 Shizuku。部分系统和游戏版本可能限制独立显示器运行。", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable private fun BackgroundPreview() {
    var manual by rememberSaveable { mutableStateOf(false) }
    val worker = remember { Executors.newSingleThreadExecutor() }
    DisposableEffect(Unit) { onDispose { worker.execute { runCatching { BackgroundConnection.preview(null) } }; worker.shutdown() } }
    if (manual) {
        Dialog(onDismissRequest = { manual = false }, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
            Surface(Modifier.fillMaxSize(), color = Color.Black) {
                Box(Modifier.fillMaxSize()) {
                    PreviewSurface(worker)
                    FilledIconButton(onClick = { manual = false }, modifier = Modifier.align(Alignment.TopEnd).padding(18.dp), colors = IconButtonDefaults.filledIconButtonColors(containerColor = Color.Black.copy(alpha = .72f), contentColor = Color.White)) {
                        Icon(Icons.Rounded.Close, "退出手动操作")
                    }
                }
            }
        }
    } else {
        Box(Modifier.fillMaxSize()) {
            PreviewSurface(worker)
            FilledTonalButton(onClick = { manual = true }, modifier = Modifier.align(Alignment.TopEnd).padding(10.dp), contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)) { Icon(Icons.Rounded.TouchApp, null, Modifier.size(16.dp)); Spacer(Modifier.width(6.dp)); Text("手动操作", style = MaterialTheme.typography.labelSmall) }
        }
    }
}

@Composable private fun PreviewSurface(worker: java.util.concurrent.ExecutorService) {
    AndroidView(factory = { context -> TextureView(context).apply {
        surfaceTextureListener = object : TextureView.SurfaceTextureListener {
            override fun onSurfaceTextureAvailable(texture: android.graphics.SurfaceTexture, width: Int, height: Int) { val surface = Surface(texture); worker.execute { try { BackgroundConnection.preview(surface) } finally { surface.release() } } }
            override fun onSurfaceTextureSizeChanged(texture: android.graphics.SurfaceTexture, width: Int, height: Int) {}
            override fun onSurfaceTextureDestroyed(texture: android.graphics.SurfaceTexture): Boolean { worker.execute { runCatching { BackgroundConnection.preview(null) } }; return true }
            override fun onSurfaceTextureUpdated(texture: android.graphics.SurfaceTexture) {}
        }
    } }, update = { view -> view.setOnTouchListener { _, event ->
        if (view.width == 0 || view.height == 0) false else {
            val index = event.actionIndex
            val action = when (event.actionMasked) { android.view.MotionEvent.ACTION_DOWN, android.view.MotionEvent.ACTION_POINTER_DOWN -> 0; android.view.MotionEvent.ACTION_UP, android.view.MotionEvent.ACTION_POINTER_UP -> 1; android.view.MotionEvent.ACTION_MOVE -> 2; else -> 3 }
            val x = (event.getX(index) * 1280 / view.width).toInt().coerceIn(0,1279); val y = (event.getY(index) * 720 / view.height).toInt().coerceIn(0,719); val contact = event.getPointerId(index)
            worker.execute { runCatching { BackgroundConnection.remote?.touch(action, x, y, contact) } }; true
        }
    } }, modifier = Modifier.fillMaxSize())
}

@Composable private fun SchedulePage(model: AppModel) = PageColumn {
    var selectedDays by remember { mutableStateOf(model.scheduleDays) }
    var selectedTimes by remember { mutableStateOf(model.scheduleMinutes) }
    var timeText by remember { mutableStateOf("") }
    SectionTitle("定时运行", "自动继承任务分页中已启用的任务和参数")
    Card(shape = CardShape, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Text("运行日期（周一至周日）", fontWeight = FontWeight.Medium)
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) { listOf(2 to "周一",3 to "周二",4 to "周三",5 to "周四",6 to "周五",7 to "周六",1 to "周日").forEach { (day,label) -> FilterChip(selected = day in selectedDays, onClick = { selectedDays = if(day in selectedDays) selectedDays-day else selectedDays+day }, label = { Text(label) }) } }
            Text("运行时间（可添加多个时间点）", fontWeight = FontWeight.Medium)
            if (selectedTimes.isNotEmpty()) Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) { selectedTimes.sorted().forEach { minute -> InputChip(selected = true, onClick = { selectedTimes = selectedTimes-minute }, label = { Text("%02d:%02d".format(minute/60, minute%60)) }, trailingIcon = { Icon(Icons.Rounded.Close, "移除", Modifier.size(16.dp)) }) } }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(value = timeText, onValueChange = { timeText = it }, label = { Text("HH:mm") }, placeholder = { Text("例如 07:30") }, singleLine = true, modifier = Modifier.weight(1f), shape = RoundedCornerShape(10.dp))
                Button(onClick = { val m = Regex("^([01]?\\d|2[0-3]):[0-5]\\d$").matchEntire(timeText.trim())?.let { val h=it.groupValues[1].toInt(); val min=timeText.substringAfter(':').toInt(); h*60+min }; if(m != null) { selectedTimes = selectedTimes+m; timeText="" } }) { Text("添加") }
            }
            Button(onClick = { model.setSchedule(selectedDays, selectedTimes) }, enabled = selectedDays.isNotEmpty() && selectedTimes.isNotEmpty(), modifier = Modifier.fillMaxWidth()) { Text("保存定时设置") }
        }
    }
    Card(shape = CardShape, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("任务继承规则", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onPrimaryContainer)
            val enabled = model.catalog?.tasks?.count { model.taskEnabled(it.get("name").asString) } ?: 0
            Text("任务页当前启用 $enabled 项。定时只运行这些任务；任务参数直接使用任务页已保存的配置。", color = MaterialTheme.colorScheme.onPrimaryContainer)
            Text("不在定时页重复显示任务开关。要调整任务范围，请前往“任务”分页。", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha=.7f))
        }
    }
    Text(if (model.scheduleMinutes.isEmpty()) "尚未保存定时设置" else "已保存：每周 ${model.scheduleDays.size} 天，${model.scheduleMinutes.size} 个时间点", style=MaterialTheme.typography.bodySmall, color=MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable private fun SettingsPage(model: AppModel, chooseChannel: () -> Unit) = PageColumn {
    SectionTitle("运行环境")
    Card(shape = CardShape, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        SettingRow(Icons.Rounded.Public, "游戏区服", model.channel, chooseChannel)
        HorizontalDivider(Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.outlineVariant)
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("控制方式", fontWeight = FontWeight.Medium)
            Text("仅使用你选择的方式，不会自动切换权限。", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            listOf(ControllerMode.ROOT to "Root", ControllerMode.SHIZUKU to "Shizuku").forEach { (mode, title) -> Row(Modifier.fillMaxWidth().clickable(enabled = !model.running && !model.backgroundActive) { model.selectMode(mode) }.padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                RadioButton(selected = model.mode == mode, onClick = null)
                Column(Modifier.padding(start = 12.dp).weight(1f)) { Text(title, fontWeight = FontWeight.Medium); Text(if (mode == ControllerMode.ROOT) "已有 Root 的设备，启动时请求授权" else "用于后台游戏，需要 Shizuku 服务", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            } }
            if (model.mode == ControllerMode.SHIZUKU) OutlinedButton(onClick = model::authorizeShizuku, modifier = Modifier.fillMaxWidth()) { Text("请求 Shizuku 授权") }
        }
    }
    SectionTitle("外观")
    Card(shape = CardShape, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("主题", fontWeight = FontWeight.Medium)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { listOf("system" to "跟随系统", "light" to "浅色", "dark" to "深色").forEach { (key, title) -> FilterChip(selected = model.theme == key, onClick = { model.selectTheme(key) }, label = { Text(title) }) } }
        }
    }
    SectionTitle("关于")
    Card(shape = CardShape, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        SettingRow(Icons.Rounded.Info, "MaaYYs Android", "${BuildConfig.VERSION_NAME} · 阴阳师自动化助手")
        SettingRow(Icons.Rounded.Inventory2, "任务资源", "MaaYYs ${model.catalog?.version ?: "加载中"}")
        SettingRow(Icons.Rounded.FavoriteBorder, "开源致谢", "MaaYYs · MaaFramework · MAA-Meow")
    }
    Text("MaaYYs 的 Android 移植。界面与后台显示能力参考、复用 MAA-Meow，阴阳师任务沿用 MaaYYs。", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable private fun LogSheet(model: AppModel) {
    val context = LocalContext.current
    ModalBottomSheet(onDismissRequest = { model.showLogs = false }) {
        Column(Modifier.fillMaxHeight(.85f).padding(horizontal = 20.dp)) {
            SectionTitle("运行日志", "用于查看任务进度和定位问题") { TextButton(onClick = { (context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(ClipData.newPlainText("MaaYYs 日志", model.logs)) }) { Text("复制") } }
            Spacer(Modifier.height(16.dp))
            SelectionContainerCompat(model.logs.ifBlank { "还没有运行记录。开始任务后，执行过程会显示在这里。" })
        }
    }
}
@Composable private fun SelectionContainerCompat(text: String) {
    androidx.compose.foundation.text.selection.SelectionContainer { Text(text, modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(bottom = 24.dp), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
}
