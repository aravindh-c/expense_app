package com.aravindh.expenselogger.sms

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import androidx.core.content.ContextCompat
import com.aravindh.expenselogger.db.AppDatabase
import com.aravindh.expenselogger.db.PendingSms

object SmsScanJob {

    // Scan SMS inbox for the past 7 days
    private const val SCAN_WINDOW_MS = 7L * 24 * 60 * 60 * 1000

    // Skip tiny transactions below this amount (all types)
    private const val MIN_AMOUNT = 50.0

    fun scan(context: Context) {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_SMS)
            != PackageManager.PERMISSION_GRANTED
        ) return

        val dao = AppDatabase.get(context).smsDao()
        val existingRefs = dao.getAllRefs().toSet()
        val existingSmsIds = dao.getAllSmsIds().toSet()

        val since = System.currentTimeMillis() - SCAN_WINDOW_MS

        val cursor = context.contentResolver.query(
            Uri.parse("content://sms/inbox"),
            arrayOf("_id", "address", "body", "date"),
            "date > ?",
            arrayOf(since.toString()),
            "date DESC"
        ) ?: return

        cursor.use {
            val idxId = it.getColumnIndexOrThrow("_id")
            val idxAddress = it.getColumnIndexOrThrow("address")
            val idxBody = it.getColumnIndexOrThrow("body")

            while (it.moveToNext()) {
                val smsId = it.getLong(idxId)
                val address = it.getString(idxAddress) ?: continue
                val body = it.getString(idxBody) ?: continue

                // Only process SMS from known bank senders
                if (!SmsParser.isFromBank(address)) continue

                // Skip if already scanned by smsId
                if (existingSmsIds.contains(smsId)) continue

                val parsed = SmsParser.parse(body) ?: continue

                // Skip if already stored by ref number
                if (parsed.ref.isNotEmpty() && existingRefs.contains(parsed.ref)) continue

                // Skip tiny transactions below ₹50
                if (parsed.amount < MIN_AMOUNT) continue

                dao.insert(
                    PendingSms(
                        smsId = smsId,
                        rawSms = body,
                        amount = parsed.amount,
                        merchant = parsed.merchant,
                        date = parsed.date,
                        bank = parsed.bank,
                        paymentType = parsed.paymentType,
                        txNature = parsed.txNature,
                        ref = parsed.ref
                    )
                )
            }
        }
    }
}
