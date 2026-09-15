package com.maayys.app

import android.app.Activity
import android.app.AlertDialog
import android.os.Bundle
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast

/** Internal settings screen. Does not start services or request elevated privileges. */
class ControllerSettingsActivity : Activity() {
    private lateinit var settings: ControllerSettings
    private lateinit var status: TextView
    private lateinit var choices: RadioGroup
    private val modes = linkedMapOf<Int, ControllerMode>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        title = "控制权限设置"
        settings = ControllerSettings(this)
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            val padding = (20 * resources.displayMetrics.density).toInt()
            setPadding(padding, padding, padding, padding)
        }
        status = TextView(this)
        content.addView(status)
        content.addView(TextView(this).apply {
            text = "选择仅保存控制方式，不代表已经授权或连接。修改后需停止服务，再重新启动。\n\nRoot：需要设备已有 su，并由 Root 管理器授权。\nShizuku：需要宿主接入并完成 Shizuku 授权。\nADB：需要宿主接入并建立已授权的 ADB 连接。"
        })
        choices = RadioGroup(this)
        ControllerMode.entries.forEachIndexed { index, mode ->
            val id = index + 100
            modes[id] = mode
            choices.addView(RadioButton(this).apply {
                this.id = id
                text = when (mode) {
                    ControllerMode.ROOT -> "Root"
                    ControllerMode.SHIZUKU -> "Shizuku"
                    ControllerMode.ADB -> "ADB"
                }
            })
        }
        content.addView(choices)
        content.addView(Button(this).apply {
            text = "保存选择"
            setOnClickListener {
                val mode = modes[choices.checkedRadioButtonId]
                if (mode == null) {
                    Toast.makeText(this@ControllerSettingsActivity, "请先选择控制方式", Toast.LENGTH_SHORT).show()
                } else if (mode == ControllerMode.ROOT) {
                    AlertDialog.Builder(this@ControllerSettingsActivity)
                        .setTitle("使用 Root 模式？")
                        .setMessage("启动任务时将请求超级用户权限。此操作不会自动 Root 设备；请仅在信任本应用时授权。现在只保存选择，不执行 su。")
                        .setNegativeButton("取消", null)
                        .setPositiveButton("保存 Root 选择") { _, _ -> save(mode) }
                        .show()
                } else save(mode)
            }
        })
        content.addView(Button(this).apply {
            text = "清除选择"
            setOnClickListener {
                settings.clear()
                choices.clearCheck()
                refreshStatus()
                Toast.makeText(this@ControllerSettingsActivity, "已清除；不会停止正在运行的服务", Toast.LENGTH_LONG).show()
            }
        })
        setContentView(ScrollView(this).apply {
            addView(content, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        })
        modes.entries.firstOrNull { it.value == settings.selectedMode() }?.let { choices.check(it.key) }
        refreshStatus()
    }

    private fun save(mode: ControllerMode) {
        settings.select(mode)
        refreshStatus()
        Toast.makeText(this, "已保存；停止服务后重新启动生效", Toast.LENGTH_LONG).show()
    }

    private fun refreshStatus() {
        status.text = "已保存模式：${settings.selectedMode()?.name ?: "未选择"}\n授权状态：尚未在此页面检测\n"
    }
}