package com.aravindh.expenselogger.sms

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import java.util.Calendar

object AlarmScheduler {

    // Scan times: 10:00am, 2:00pm, 6:00pm, 11:00pm
    private val SCAN_TIMES = listOf(
        10 to 0,
        14 to 0,
        18 to 0,
        23 to 0
    )

    fun schedule(context: Context) {
        val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

        SCAN_TIMES.forEachIndexed { idx, (hour, min) ->
            val cal = Calendar.getInstance().apply {
                set(Calendar.HOUR_OF_DAY, hour)
                set(Calendar.MINUTE, min)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
                // If this time already passed today, schedule for tomorrow
                if (timeInMillis <= System.currentTimeMillis()) {
                    add(Calendar.DAY_OF_YEAR, 1)
                }
            }

            val pi = PendingIntent.getBroadcast(
                context,
                idx,
                Intent(context, ScanAlarmReceiver::class.java),
                pendingIntentFlags()
            )

            // Repeats every 24 hours at the scheduled time
            am.setRepeating(
                AlarmManager.RTC_WAKEUP,
                cal.timeInMillis,
                AlarmManager.INTERVAL_DAY,
                pi
            )
        }
    }

    fun cancel(context: Context) {
        val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        SCAN_TIMES.forEachIndexed { idx, _ ->
            val pi = PendingIntent.getBroadcast(
                context,
                idx,
                Intent(context, ScanAlarmReceiver::class.java),
                pendingIntentFlags()
            )
            am.cancel(pi)
        }
    }

    private fun pendingIntentFlags(): Int {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M)
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        else
            PendingIntent.FLAG_UPDATE_CURRENT
    }
}
