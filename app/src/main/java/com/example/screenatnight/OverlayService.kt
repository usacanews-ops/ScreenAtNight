package com.example.screenatnight

import android.app.*
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import androidx.core.app.NotificationCompat

class OverlayService : Service() {

    private var windowManager: WindowManager? = null
    private var overlayView: View? = null
    private var currentOpacity: Int = 40

    companion object {
        const val ACTION_SET_OPACITY = "ACTION_SET_OPACITY"
        const val ACTION_INCREASE_OPACITY = "ACTION_INCREASE_OPACITY"
        const val ACTION_DECREASE_OPACITY = "ACTION_DECREASE_OPACITY"
        const val ACTION_STOP_SERVICE = "ACTION_STOP_SERVICE"
        const val EXTRA_OPACITY = "EXTRA_OPACITY"
        const val CHANNEL_ID = "screen_at_night_service"
        const val NOTIF_ID = 1001
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        val prefs = getSharedPreferences("screen_at_night_prefs", Context.MODE_PRIVATE)
        currentOpacity = prefs.getInt("opacity", 40)
        
        createNotificationChannel()
        startForeground(NOTIF_ID, buildInteractiveNotification(currentOpacity))
        initOverlay()
    }

    private fun initOverlay() {
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        overlayView = View(this)

        updateColor(currentOpacity)

        val layoutFlag = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_SYSTEM_OVERLAY
        }

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            layoutFlag,
            WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
        }

        windowManager?.addView(overlayView, params)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP_SERVICE -> {
                stopSelf()
                return START_NOT_STICKY
            }
            ACTION_SET_OPACITY -> {
                val alpha = intent.getIntExtra(EXTRA_OPACITY, currentOpacity)
                applyNewOpacity(alpha)
            }
            ACTION_INCREASE_OPACITY -> {
                applyNewOpacity(currentOpacity + 5)
            }
            ACTION_DECREASE_OPACITY -> {
                applyNewOpacity(currentOpacity - 5)
            }
        }
        return START_STICKY
    }

    private fun applyNewOpacity(newVal: Int) {
        currentOpacity = newVal.coerceIn(0, 92)
        updateColor(currentOpacity)
        getSharedPreferences("screen_at_night_prefs", Context.MODE_PRIVATE)
            .edit()
            .putInt("opacity", currentOpacity)
            .apply()

        val notificationManager = getSystemService(NotificationManager::class.java)
        notificationManager?.notify(NOTIF_ID, buildInteractiveNotification(currentOpacity))
    }

    private fun updateColor(opacityPercent: Int) {
        val safePercent = opacityPercent.coerceIn(0, 92)
        val alphaValue = (safePercent * 255) / 100
        overlayView?.setBackgroundColor(Color.argb(alphaValue, 0, 0, 0))
    }

    private fun buildInteractiveNotification(level: Int): Notification {
        val launchIntent = Intent(this, MainActivity::class.java)
        val pLaunch = PendingIntent.getActivity(
            this, 0, launchIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        // PendingIntent for swiping away the notification or tapping cancel
        val stopIntent = Intent(this, OverlayService::class.java).apply {
            action = ACTION_STOP_SERVICE
        }
        val pStop = PendingIntent.getService(
            this, 1, stopIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        // Action: Increase darkness (+5%)
        val incIntent = Intent(this, OverlayService::class.java).apply {
            action = ACTION_INCREASE_OPACITY
        }
        val pInc = PendingIntent.getService(
            this, 2, incIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        // Action: Decrease darkness (-5%)
        val decIntent = Intent(this, OverlayService::class.java).apply {
            action = ACTION_DECREASE_OPACITY
        }
        val pDec = PendingIntent.getService(
            this, 3, decIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("ScreenAtNight Active ($level%)")
            .setContentText("Swipe away or tap 'Off' to exit dimmer")
            .setSmallIcon(android.R.drawable.ic_menu_view)
            .setContentIntent(pLaunch)
            .setDeleteIntent(pStop) // Swiping away cancels and stops the dimmer
            .setOngoing(false)      // Allows user to swipe away/dismiss the notification
            .addAction(android.R.drawable.ic_input_add, "+ Dark", pInc)
            .addAction(android.R.drawable.ic_delete, "- Light", pDec)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Off", pStop)
            .build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "ScreenAtNight Controls",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Screen dimmer controls and toggles"
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager?.createNotificationChannel(channel)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        if (overlayView != null) {
            windowManager?.removeView(overlayView)
            overlayView = null
        }
        val prefs = getSharedPreferences("screen_at_night_prefs", Context.MODE_PRIVATE)
        prefs.edit().putBoolean("is_active", false).apply()
    }
}
