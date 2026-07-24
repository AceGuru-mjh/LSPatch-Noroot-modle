package com.vipunlock.noroot.services

import android.app.Service
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.TypedValue
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.TextView
import com.vipunlock.noroot.R
import com.vipunlock.noroot.activities.PanelActivity
import com.vipunlock.noroot.utils.ConfigManager
import com.vipunlock.noroot.utils.LogStore

class FloatingBallService : Service() {
    private lateinit var windowManager: WindowManager
    private lateinit var ballView: View
    private lateinit var params: WindowManager.LayoutParams
    private var isPanelOpen = false
    private var handler: Handler? = null
    private var updateRunnable: Runnable? = null

    private fun dp(v: Int): Int =
        TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v.toFloat(), resources.displayMetrics).toInt()

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager

        try { LogStore.init(applicationContext) } catch (_: Throwable) {}
        try { ConfigManager.init(applicationContext) } catch (_: Throwable) {}

        ballView = LayoutInflater.from(this).inflate(R.layout.floating_ball, null)

        val size = dp(64)
        params = WindowManager.LayoutParams(
            size, size,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.END
            x = 0; y = dp(200)
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
        updateBallState()
    }

    private fun updateBallState() {
        try {
            val enabled = true
            val pulse = ballView.findViewById<View>(R.id.ball_pulse)
            val status = ballView.findViewById<View>(R.id.ball_status)
            val value = ballView.findViewById<TextView>(R.id.ball_value)
            pulse?.visibility = if (enabled) View.VISIBLE else View.GONE
            status?.alpha = if (enabled) 1f else 0.4f
            value?.text = if (enabled) "●" else "‖"
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
                    try { updateBallState() } catch (_: Throwable) {}
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