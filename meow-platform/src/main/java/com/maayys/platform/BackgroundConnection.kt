package com.maayys.platform

import android.content.ComponentName
import android.content.Context
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.os.Binder
import android.os.IBinder
import android.view.Surface
import com.aliothmoon.maameow.root.RootServiceBootstrapRegistry
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import rikka.shizuku.Shizuku
import java.io.File
import java.util.UUID
import java.util.concurrent.CopyOnWriteArrayList

/** One process/display per app. MAA-Meow supplies the Binder bootstrap and display implementation. */
object BackgroundConnection {
    @Volatile var remote: IBackgroundDisplay? = null
        private set
    @Volatile var display: Int = -1
        private set
    @Volatile var mode: String? = null
        private set
    private var process: Process? = null
    private val owner = Binder()
    private var shizukuArgs: Shizuku.UserServiceArgs? = null
    private var shizukuConnection: ServiceConnection? = null
    private val observers = CopyOnWriteArrayList<() -> Unit>()
    fun subscribe(observer: () -> Unit) { observers += observer }
    fun unsubscribe(observer: () -> Unit) { observers -= observer }
    private fun notifyChanged() { observers.forEach { runCatching(it) } }
    fun shizukuReady() = runCatching { Shizuku.pingBinder() && Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED }.getOrDefault(false)
    fun requestShizuku() { check(Shizuku.pingBinder()) { "请先启动 Shizuku" }; Shizuku.requestPermission(902) }
    private fun quote(value: String) = "'" + value.replace("'", "'\\''") + "'"

    @Synchronized fun connect(context: Context, selectedMode: String, launcher: ((String) -> Unit)? = null): IBackgroundDisplay {
        remote?.takeIf { it.asBinder().isBinderAlive && mode == selectedMode }?.let { return it }
        close()
        val app = context.applicationContext
        val binder: IBinder = when (selectedMode) {
            "ROOT" -> {
                val token = UUID.randomUUID().toString()
                val pending = RootServiceBootstrapRegistry.register(token)
                val lib = app.applicationInfo.nativeLibraryDir
                val command = "CLASSPATH=${quote(app.applicationInfo.sourceDir)} LD_LIBRARY_PATH=${quote(lib)} " +
                    "exec /system/bin/app_process -Djava.library.path=${quote(lib)} /system/bin --nice-name=${quote(app.packageName + ":background")} " +
                    "com.maayys.platform.BackgroundServiceMain ${quote(app.packageName)} ${android.os.Process.myUid() / 100000} ${quote(token)}"
                try {
                    if (launcher != null) launcher(command)
                    else process = ProcessBuilder("su", "-c", command).redirectErrorStream(true)
                        .redirectOutput(File(app.filesDir, "background-service.log")).start()
                    runBlocking { withTimeout(30_000) { pending.await() } }
                } catch (error: Exception) {
                    process?.destroyForcibly(); process = null
                    throw IllegalStateException("后台服务未连接，请检查 Root 授权。${error.message}", error)
                } finally { RootServiceBootstrapRegistry.unregister(token) }
            }
            "SHIZUKU" -> {
                check(shizukuReady()) { "请先在设置中启动并授权 Shizuku" }
                val ready = CompletableDeferred<IBinder>()
                val connection = object : ServiceConnection {
                    override fun onServiceConnected(name: ComponentName?, service: IBinder) { ready.complete(service) }
                    override fun onServiceDisconnected(name: ComponentName?) { remote = null; display = -1; notifyChanged() }
                }
                val args = Shizuku.UserServiceArgs(ComponentName(app.packageName, BackgroundDisplayService::class.java.name))
                    .daemon(false).processNameSuffix("background").debuggable(false).version(3)
                shizukuArgs = args; shizukuConnection = connection
                Shizuku.bindUserService(args, connection)
                try { runBlocking { withTimeout(20_000) { ready.await() } } }
                catch (error: Exception) { Shizuku.unbindUserService(args, connection, true); throw error }
            }
            else -> error("后台运行请选择 Root 或 Shizuku")
        }
        val service = IBackgroundDisplay.Stub.asInterface(binder)
        try {
            display = service.setup(1280, 720, 160, app.applicationInfo.nativeLibraryDir, owner)
            check(display > 0)
            binder.linkToDeath({ remote = null; display = -1; notifyChanged() }, 0)
            remote = service; mode = selectedMode
            notifyChanged()
            return service
        } catch (error: Exception) {
            runCatching { service.destroy() }; close(); throw error
        }
    }
    fun preview(surface: Surface?) { remote?.setPreview(surface) }
    @Synchronized fun close() {
        val service = remote; remote = null; display = -1; mode = null
        runCatching { service?.close(); service?.destroy() }
        val args = shizukuArgs; val connection = shizukuConnection
        if (args != null && connection != null) runCatching { Shizuku.unbindUserService(args, connection, true) }
        shizukuArgs = null; shizukuConnection = null
        process?.destroy(); process = null
        notifyChanged()
    }
}
