package com.aravindh.expenselogger.sms

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences

class ScanAlarmReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val pendingResult = goAsync()
        Thread {
            try {
                SmsScanJob.scan(context)
                context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
                    .edit()
                    .putLong("last_scanned", System.currentTimeMillis())
                    .apply()
            } finally {
                pendingResult.finish()
            }
        }.start()
    }
}
