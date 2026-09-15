package com.maayys.platform

import android.app.*
import android.content.Intent
import android.os.IBinder
import java.util.concurrent.Executors

/** Keeps the owning app/Binder alive when the game is on its own display. */
class BackgroundKeepAliveService : Service() {
    override fun onCreate() {
        super.onCreate()
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(NotificationChannel("maayys_background", "后台游戏", NotificationManager.IMPORTANCE_LOW))
        val close = PendingIntent.getService(this, 41, Intent(this, javaClass).setAction("close"), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val notification = Notification.Builder(this, "maayys_background")
            .setSmallIcon(android.R.drawable.ic_media_play).setContentTitle("MaaYYs · 后台游戏")
            .setContentText("游戏在独立显示器中运行").setOngoing(true)
            .addAction(Notification.Action.Builder(null, "关闭后台", close).build())
        packageManager.getLaunchIntentForPackage(packageName)?.let {
            notification.setContentIntent(PendingIntent.getActivity(this, 42, it, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT))
        }
        startForeground(1041, notification.build())
    }
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == "close") {
            val worker = Executors.newSingleThreadExecutor()
            worker.execute { BackgroundConnection.close(); stopSelf(); worker.shutdown() }
        }
        return START_NOT_STICKY
    }
    override fun onBind(intent: Intent?): IBinder? = null
}
