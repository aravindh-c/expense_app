package com.aravindh.expenselogger.db

import androidx.room.*

@Dao
interface SmsDao {

    @Query("SELECT * FROM pending_sms WHERE isProcessed = 0 ORDER BY id DESC")
    fun getPending(): List<PendingSms>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    fun insert(sms: PendingSms): Long

    @Query("UPDATE pending_sms SET isProcessed = 1 WHERE id = :id")
    fun markProcessed(id: Long)

    @Query("SELECT ref FROM pending_sms WHERE ref != ''")
    fun getAllRefs(): List<String>

    @Query("SELECT smsId FROM pending_sms WHERE smsId > 0")
    fun getAllSmsIds(): List<Long>

    @Query("DELETE FROM pending_sms WHERE isProcessed = 1")
    fun clearProcessed()
}
