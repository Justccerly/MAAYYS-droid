package com.maayys.app

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.app.PendingIntent
import android.content.Intent
import android.os.IBinder
import com.maayys.runtime.RuntimeEvent
import com.maayys.runtime.RuntimeEventListener

class MaaAgentForegroundService : Service() {
    override fun onCreate() {
        super.onCreate()
        val manager = requireNotNull(getSystemService(NotificationManager::class.java)) {
            "Notification service unavailable"
        }
        manager.createNotificationChannel(NotificationChannel(CHANNEL, "MaaYYs", NotificationManager.IMPORTANCE_LOW))
        startForeground(ID, notification())
    }
    private val commands = java.util.concurrent.Executors.newSingleThreadExecutor()
    private var agent: AgentService? = null
    private var initialized = false
    private var configuration: String? = null
    private val eventListener = RuntimeEventListener { event ->
        RuntimeStatusBus.publish(event)
        val text = when (event) {
            is RuntimeEvent.Started -> "任务开始：${event.task}"
            is RuntimeEvent.Finished -> "任务完成：${event.task}（${if (event.success) "成功" else "失败"}）"
            is RuntimeEvent.Failed -> "任务失败：${event.task}"
            RuntimeEvent.Stopped -> "服务已停止"
        }
        try { getSystemService(NotificationManager::class.java)?.notify(ID, notification(text)) }
        catch (_: Exception) { }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action
        val task = intent?.getStringExtra(EXTRA_TASK)
        val bundle = intent?.getBundleExtra(EXTRA_PARAMS)
        commands.execute {
            try {
                val params = bundle?.keySet()?.associateWith { key ->
                    requireNotNull(bundle.getString(key)) { "Task parameters must be strings" }
                } ?: emptyMap()
                when (action) {
                    ACTION_START, ACTION_RUN_TASK -> {
                        if (action == ACTION_RUN_TASK) require(!task.isNullOrBlank()) { "Missing task" }
                        val prefs = getSharedPreferences("project", MODE_PRIVATE)
                        val selectedConfiguration = "${ControllerSettings(this).selectedMode()}:${prefs.getInt("resource", 0)}:${prefs.getBoolean("background", false)}"
                        if (configuration != null && configuration != selectedConfiguration) releaseAgent()
                        val service = agent ?: (application as? AgentDependencies)
                            ?.createAgentService(applicationContext, eventListener)?.also { agent = it }
                            ?: error("Host Application must implement AgentDependencies")
                        if (!initialized) {
                            check(service.start()) { "Runtime initialization failed" }
                            initialized = true
                            configuration = selectedConfiguration
                        }
                        if (action == ACTION_RUN_TASK) {
                            if (!service.run(requireNotNull(task), params)) {
                                android.util.Log.w(CHANNEL, "Task rejected: $task")
                                eventListener.onEvent(RuntimeEvent.Failed(task, "任务未接受，可能已有任务正在执行"))
                            }
                        }
                    }
                    ACTION_STOP, null -> { releaseAgent(); stopSelfResult(startId) }
                    else -> { android.util.Log.w(CHANNEL, "Unknown service action"); stopSelfResult(startId) }
                }
            } catch (error: Exception) {
                android.util.Log.e(CHANNEL, "Agent command failed", error)
                com.maayys.runtime.RuntimeLog.append(error.stackTraceToString())
                eventListener.onEvent(RuntimeEvent.Failed(task ?: "初始化", error.message ?: error.javaClass.simpleName))
                releaseAgent()
                stopSelfResult(startId)
            } catch (error: LinkageError) {
                android.util.Log.e(CHANNEL, "Native runtime unavailable", error)
                com.maayys.runtime.RuntimeLog.append(error.stackTraceToString())
                eventListener.onEvent(RuntimeEvent.Failed(task ?: "初始化", error.message ?: "原生库加载失败"))
                releaseAgent()
                stopSelfResult(startId)
            }
        }
        return START_NOT_STICKY
    }

    private fun releaseAgent() {
        try { agent?.stop() }
        catch (error: Exception) { android.util.Log.e(CHANNEL, "Agent cleanup failed", error) }
        catch (error: LinkageError) { android.util.Log.e(CHANNEL, "Native cleanup failed", error) }
        finally { agent = null; initialized = false; configuration = null }
    }

    override fun onBind(intent: Intent?): IBinder? = null
    override fun onDestroy() {
        commands.execute { releaseAgent(); com.maayys.runtime.RuntimeLog.append("任务运行时已释放"); RuntimeStatusBus.publish(RuntimeEvent.Stopped) }
        commands.shutdown()
        stopForeground(STOP_FOREGROUND_REMOVE)
        super.onDestroy()
    }
    private fun notification(text: String = "自动化服务运行中"): Notification = Notification.Builder(this, CHANNEL)
        .setContentTitle("MaaYYs")
        .setContentText(text)
        .setSmallIcon(android.R.drawable.ic_media_play)
        .addAction(Notification.Action.Builder(null, "停止任务", PendingIntent.getService(
            this, 1, Intent(this, MaaAgentForegroundService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )).build())
        .setOngoing(true).build()
    companion object {
        const val ACTION_START = "com.maayys.action.START"
        const val ACTION_RUN_TASK = "com.maayys.action.RUN_TASK"
        const val ACTION_STOP = "com.maayys.action.STOP"
        const val EXTRA_TASK = "task"
        const val EXTRA_PARAMS = "params"
        private const val CHANNEL = "maayys_agent"
        private const val ID = 1001
    }
}
