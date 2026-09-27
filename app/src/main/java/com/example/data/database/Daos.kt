package com.example.data.database

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface InvoiceDao {
    @Query("SELECT * FROM invoices WHERE isDeleted = 0 ORDER BY createdAt DESC")
    fun getAllInvoices(): Flow<List<InvoiceEntity>>

    @Query("SELECT * FROM invoices ORDER BY createdAt DESC")
    suspend fun getAllInvoicesDirect(): List<InvoiceEntity>

    @Query("SELECT * FROM invoices WHERE isDeleted = 1 ORDER BY createdAt DESC")
    fun getDeletedInvoices(): Flow<List<InvoiceEntity>>

    @Query("SELECT * FROM invoices WHERE id = :id LIMIT 1")
    suspend fun getInvoiceById(id: Int): InvoiceEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertInvoice(invoice: InvoiceEntity): Long

    @Update
    suspend fun updateInvoice(invoice: InvoiceEntity)

    @Delete
    suspend fun deleteInvoice(invoice: InvoiceEntity)

    @Query("UPDATE invoices SET isDeleted = :isDeleted WHERE id = :id")
    suspend fun setDeletedStatus(id: Int, isDeleted: Boolean)

    @Query("DELETE FROM invoices WHERE id = :id")
    suspend fun deleteInvoiceById(id: Int)
}

@Dao
interface ConfigDao {
    @Query("SELECT * FROM app_config WHERE id = 'current_config' LIMIT 1")
    fun getConfigFlow(): Flow<ConfigEntity?>

    @Query("SELECT * FROM app_config WHERE id = 'current_config' LIMIT 1")
    suspend fun getConfigDirect(): ConfigEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveConfig(config: ConfigEntity)
}

@Dao
interface ActivatedDeviceDao {
    @Query("SELECT * FROM activated_devices ORDER BY activationDate DESC")
    fun getAllDevicesFlow(): Flow<List<ActivatedDeviceEntity>>

    @Query("SELECT * FROM activated_devices ORDER BY activationDate DESC")
    suspend fun getAllDevicesDirect(): List<ActivatedDeviceEntity>

    @Query("SELECT * FROM activated_devices WHERE deviceId = :deviceId LIMIT 1")
    suspend fun getDeviceById(deviceId: String): ActivatedDeviceEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveDevice(device: ActivatedDeviceEntity)

    @Delete
    suspend fun deleteDevice(device: ActivatedDeviceEntity)

    @Query("DELETE FROM activated_devices WHERE deviceId = :deviceId")
    suspend fun deleteDeviceById(deviceId: String)
}
