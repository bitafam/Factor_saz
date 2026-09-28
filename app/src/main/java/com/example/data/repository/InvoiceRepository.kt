package com.example.data.repository

import com.example.data.database.ActivatedDeviceDao
import com.example.data.database.ActivatedDeviceEntity
import com.example.data.database.ConfigDao
import com.example.data.database.ConfigEntity
import com.example.data.database.InvoiceDao
import com.example.data.database.InvoiceEntity
import kotlinx.coroutines.flow.Flow

class InvoiceRepository(
    private val invoiceDao: InvoiceDao,
    private val configDao: ConfigDao,
    private val activatedDeviceDao: ActivatedDeviceDao
) {
    val allInvoices: Flow<List<InvoiceEntity>> = invoiceDao.getAllInvoices()
    val deletedInvoices: Flow<List<InvoiceEntity>> = invoiceDao.getDeletedInvoices()
    val totalRevenue: Flow<Double> = invoiceDao.getTotalRevenue()
    val activeInvoiceCount: Flow<Int> = invoiceDao.getActiveInvoiceCount()

    suspend fun getAllInvoicesDirect(): List<InvoiceEntity> {
        return invoiceDao.getAllInvoicesDirect()
    }

    suspend fun getInvoiceById(id: Int): InvoiceEntity? {
        return invoiceDao.getInvoiceById(id)
    }

    suspend fun insertInvoice(invoice: InvoiceEntity): Long {
        return invoiceDao.insertInvoice(invoice)
    }

    suspend fun updateInvoice(invoice: InvoiceEntity) {
        invoiceDao.updateInvoice(invoice)
    }

    suspend fun deleteInvoice(invoice: InvoiceEntity) {
        invoiceDao.deleteInvoice(invoice)
    }

    suspend fun setDeletedStatus(id: Int, isDeleted: Boolean) {
        invoiceDao.setDeletedStatus(id, isDeleted)
    }

    suspend fun deleteInvoiceById(id: Int) {
        invoiceDao.deleteInvoiceById(id)
    }

    val configFlow: Flow<ConfigEntity?> = configDao.getConfigFlow()

    suspend fun getConfigDirect(): ConfigEntity? {
        return configDao.getConfigDirect()
    }

    suspend fun saveConfig(config: ConfigEntity) {
        configDao.saveConfig(config)
    }

    val allDevicesFlow: Flow<List<ActivatedDeviceEntity>> = activatedDeviceDao.getAllDevicesFlow()

    suspend fun getAllDevicesDirect(): List<ActivatedDeviceEntity> {
        return activatedDeviceDao.getAllDevicesDirect()
    }

    suspend fun getDeviceById(deviceId: String): ActivatedDeviceEntity? {
        return activatedDeviceDao.getDeviceById(deviceId)
    }

    suspend fun saveDevice(device: ActivatedDeviceEntity) {
        activatedDeviceDao.saveDevice(device)
    }

    suspend fun deleteDevice(device: ActivatedDeviceEntity) {
        activatedDeviceDao.deleteDevice(device)
    }

    suspend fun deleteDeviceById(deviceId: String) {
        activatedDeviceDao.deleteDeviceById(deviceId)
    }
}
