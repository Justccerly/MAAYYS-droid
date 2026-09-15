package com.maayys.platform

import android.os.Looper
import com.aliothmoon.maameow.root.RootServiceBootstrapClient

/** Uses MAA-Meow's token-checked Binder bootstrap and app-death cleanup. */
object BackgroundServiceMain {
    @JvmStatic fun main(args: Array<String>) {
        require(args.size == 3) { "Expected package, user id and bootstrap token" }
        if (Looper.getMainLooper() == null) Looper.prepareMainLooper()
        val service = BackgroundDisplayService()
        val result = RootServiceBootstrapClient.attachRemoteService(args[0], args[1].toInt(), args[2], service)
            ?: error("Could not attach background display service")
        val owner = result.lifecycleBinder()
        owner.linkToDeath({ service.destroy() }, 0)
        // Keep the BinderProxy alive for the lifetime of the process.
        lifecycle = owner
        Looper.loop()
    }
    private var lifecycle: android.os.IBinder? = null
}
