package com.adblockerx.noroot.services

import android.app.Service
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.TextView
import com.adblockerx.noroot.R
import com.adblockerx.noroot.activities.PanelActivity
import com.adblockerx.noroot.utils.ConfigManager
import com.adblockerx.noroot.utils.LogStore

class FloatingBallService : Service() {
    private lateinit var windowManager: WindowManager
    private lateinit var ballView: View
    private lateinit var params: WindowManager.LayoutParams
    private var isPanelOpen = false
    private var handler: Handler? = null
    private var updateRunnable: Runnable? = null

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager

        try { LogStore.init(applicationContext) } catch (_: Throwable) {}
        try { ConfigManager.init(applicationContext) } catch (_: Throwable) {}

        ballView = LayoutInflater.from(this).inflate(R.layout.floating_ball, null)
        updateBallCount()

        params = WindowManager.LayoutParams(
            120, 120,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.END
            x = 0; y = 800
        }

        ballView.setOnClickListener { showPanel() }
        ballView.setOnTouchListener(object : View.OnTouchListener {
            private var initialX = 0; private var initialY = 0
            private var initialTouchX = 0f; private var initialTouchY = 0f
            override fun onTouch(v: View, event: MotionEvent): Boolean {
                when (event.action) {
                    MotionEvent.ACTION_DOWN -> {
                        initialX = params.x; initialY = params.y
                        initialTouchX = event.rawX; initialTouchY = event.rawY
                    }
                    MotionEvent.ACTION_MOVE -> {
                        params.x = initialX - (event.rawX - initialTouchX).toInt()
                        params.y = initialY + (event.rawY - initialTouchY).toInt()
                        windowManager.updateViewLayout(ballView, params)
                    }
                }
                return false
            }
        })

        windowManager.addView(ballView, params)
    }

    private fun updateBallCount() {
        try {
            val tv = ballView.findViewById<TextView>(R.id.ball_count) ?: return
            val count = try { 0L } catch (_: Throwable) { 0L }
            tv.text = count.toString()
        } catch (_: Throwable) {}
    }

    private fun showPanel() {
        if (isPanelOpen) return
        isPanelOpen = true
        val intent = Intent(this, PanelActivity::class.java)
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        startActivity(intent)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        try { LogStore.add("info", "悬浮球服务已启动") } catch (_: Throwable) {}
        startPeriodicUpdate()
        return START_STICKY
    }

    private fun startPeriodicUpdate() {
        if (handler == null) {
            handler = Handler(Looper.getMainLooper())
        }
        if (updateRunnable == null) {
            updateRunnable = object : Runnable {
                override fun run() {
                    try { updateBallCount() } catch (_: Throwable) {}
                    handler?.postDelayed(this, 1000L)
                }
            }
        }
        handler?.removeCallbacks(updateRunnable!!)
        handler?.post(updateRunnable!!)
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        try { updateRunnable?.let { handler?.removeCallbacks(it) } } catch (_: Throwable) {}
        handler = null
        updateRunnable = null
        if (::ballView.isInitialized) {
            try { windowManager.removeView(ballView) } catch (_: Exception) {}
        }
        super.onDestroy()
    }
}
