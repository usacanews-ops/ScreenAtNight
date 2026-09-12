package com.example.screenatnight

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import java.util.Calendar

class ScheduleReceiver : BroadcastReceiver() {

    companion object {
        const val ACTION_ON = "com.example.screenatnight.ACTION_TRIGGER_ON"
        const val ACTION_OFF = "com.example.screenatnight.ACTION_TRIGGER_OFF"
    }

    override fun onReceive(context: Context, intent: Intent) {
        val prefs = context.getSharedPreferences("screen_at_night_prefs", Context.MODE_PRIVATE)
        val isScheduleEnabled = prefs.getBoolean("schedule_enabled", false)
        val isSolarEnabled = prefs.getBoolean("solar_enabled", false)

        when (intent.action) {
            ACTION_ON -> {
                prefs.edit().putBoolean("is_active", true).apply()
                val opacity = prefs.getInt("opacity", 40)

                val serviceIntent = Intent(context, OverlayService::class.java).apply {
                    action = OverlayService.ACTION_SET_OPACITY
                    putExtra(OverlayService.EXTRA_OPACITY, opacity)
                }

                try {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        context.startForegroundService(serviceIntent)
                    } else {
                        context.startService(serviceIntent)
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }

                // Automatically reschedule for the next day
                if (isScheduleEnabled || isSolarEnabled) {
                    rescheduleNextDay(context, isTurnOn = true)
                }
            }

            ACTION_OFF -> {
                prefs.edit().putBoolean("is_active", false).apply()
                val serviceIntent = Intent(context, OverlayService::class.java).apply {
                    action = OverlayService.ACTION_STOP_SERVICE
                }
                context.stopService(serviceIntent)

                // Automatically reschedule for the next day
                if (isScheduleEnabled || isSolarEnabled) {
                    rescheduleNextDay(context, isTurnOn = false)
                }
            }

            Intent.ACTION_BOOT_COMPLETED -> {
                if (isScheduleEnabled || isSolarEnabled) {
                    rescheduleAllAlarms(context)
                }
            }
        }
    }

    private fun rescheduleNextDay(context: Context, isTurnOn: Boolean) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        val prefs = context.getSharedPreferences("screen_at_night_prefs", Context.MODE_PRIVATE)

        val hourKey = if (isTurnOn) "start_hour" else "end_hour"
        val minKey = if (isTurnOn) "start_min" else "end_min"
        val action = if (isTurnOn) ACTION_ON else ACTION_OFF
        val reqCode = if (isTurnOn) 101 else 102

        val targetCal = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, prefs.getInt(hourKey, if (isTurnOn) 22 else 6))
            set(Calendar.MINUTE, prefs.getInt(minKey, 0))
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
            add(Calendar.DATE, 1) // Next day
        }

        val intent = Intent(context, ScheduleReceiver::class.java).apply {
            this.action = action
            setPackage(context.packageName)
        }

        val pendingIntent = PendingIntent.getBroadcast(
            context,
            reqCode,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, targetCal.timeInMillis, pendingIntent)
        } else {
            alarmManager.setExact(AlarmManager.RTC_WAKEUP, targetCal.timeInMillis, pendingIntent)
        }
    }

    private fun rescheduleAllAlarms(context: Context) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        val prefs = context.getSharedPreferences("screen_at_night_prefs", Context.MODE_PRIVATE)
        val now = Calendar.getInstance()

        fun setAlarm(isTurnOn: Boolean) {
            val hourKey = if (isTurnOn) "start_hour" else "end_hour"
            val minKey = if (isTurnOn) "start_min" else "end_min"
            val action = if (isTurnOn) ACTION_ON else ACTION_OFF
            val reqCode = if (isTurnOn) 101 else 102

            val cal = Calendar.getInstance().apply {
                set(Calendar.HOUR_OF_DAY, prefs.getInt(hourKey, if (isTurnOn) 22 else 6))
                set(Calendar.MINUTE, prefs.getInt(minKey, 0))
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
                if (before(now)) {
                    add(Calendar.DATE, 1)
                }
            }

            val intent = Intent(context, ScheduleReceiver::class.java).apply {
                this.action = action
                setPackage(context.packageName)
            }

            val pendingIntent = PendingIntent.getBroadcast(
                context,
                reqCode,
                intent,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, cal.timeInMillis, pendingIntent)
            } else {
                alarmManager.setExact(AlarmManager.RTC_WAKEUP, cal.timeInMillis, pendingIntent)
            }
        }

        setAlarm(isTurnOn = true)
        setAlarm(isTurnOn = false)
    }
}
