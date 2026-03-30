package com.aravindh.expenselogger.db

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "pending_sms")
data class PendingSms(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val smsId: Long = 0,        // SMS inbox _id for dedup
    val rawSms: String,
    val amount: Double,
    val merchant: String,
    val date: String,           // yyyy-MM-dd
    val bank: String,
    val paymentType: String,    // UPI / Card / NEFT / NACH / IMPS
    val txNature: String,       // Expense / Income / Settlement / Saving
    val ref: String = "",       // UPI Ref / UTR for dedup
    val isProcessed: Boolean = false
)
