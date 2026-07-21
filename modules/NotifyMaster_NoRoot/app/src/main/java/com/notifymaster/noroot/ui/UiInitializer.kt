package com.notifymaster.noroot.ui

object UiInitializer {
    fun initAllUi(context: android.content.Context) {
        try {
            val svcClass = Class.forName("com.notifymaster.noroot.services.FloatingBallService")
            val intent = android.content.Intent(context, svcClass)
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        } catch (_: Throwable) { }
    }
}
