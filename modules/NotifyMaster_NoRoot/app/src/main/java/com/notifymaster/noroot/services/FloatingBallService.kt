package com.notifymaster.noroot.services

import android.app.Service
import android.content.Intent
import android.graphics.PixelFormat
import android.os.IBinder
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.TextView
import com.notifymaster.noroot.R
import com.notifymaster.noroot.activities.PanelActivity

class FloatingBallService : Service() {
    private lateinit var windowManager: WindowManager
    private lateinit var ballView: View
    private lateinit var params: WindowManager.LayoutParams

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        ballView = LayoutInflater.from(this).inflate(R.layout.floating_ball, null)
        ballView.findViewById<TextView>(R.id.ball_count).text = "0"

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
            private var initialX = 0
            private var initialY = 0
            private var initialTouchX = 0f
            private var initialTouchY = 0f
            override fun onTouch(v: View, event: MotionEvent): Boolean {
                when (event.action) {
                    MotionEvent.ACTION_DOWN -> {
                        initialX = params.x
                        initialY = params.y
                        initialTouchX = event.rawX
                        initialTouchY = event.rawY
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

    private fun showPanel() {
        val intent = Intent(this, PanelActivity::class.java)
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        startActivity(intent)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int = START_STICKY
    override fun onBind(intent: Intent?): IBinder? = null
    override fun onDestroy() {
        windowManager.removeView(ballView)
        super.onDestroy()
    }
}
