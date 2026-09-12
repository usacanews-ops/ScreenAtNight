package com.example.screenatnight

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build

class ScheduleReceiver : BroadcastReceiver() {

    companion object {
        const val ACTION_ON = "com.example.screenatnight.ACTION_TRIGGER_ON"
        const val ACTION_OFF = "com.example.screenatnight.ACTION_TRIGGER_OFF"
    }

    override fun onReceive(context: Context, intent: Intent) {
        val serviceIntent = Intent(context, OverlayService::class.java)

        when (intent.action) {
            ACTION_ON -> {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(serviceIntent)
                } else {
                    context.startService(serviceIntent)
                }
            }
            ACTION_OFF -> {
                context.stopService(serviceIntent)
            }
            Intent.ACTION_BOOT_COMPLETED -> {
                val prefs = context.getSharedPreferences("screen_at_night_prefs", Context.MODE_PRIVATE)
                val isServiceActive = prefs.getBoolean("is_active", false)
                if (isServiceActive) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        context.startForegroundService(serviceIntent)
                    } else {
                        context.startService(serviceIntent)
                    }
                }
            }
        }
    }
}
