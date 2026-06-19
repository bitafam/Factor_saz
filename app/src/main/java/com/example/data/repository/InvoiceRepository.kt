package com.example.data.repository

import com.example.data.database.ConfigDao
import com.example.data.database.ConfigEntity
import com.example.data.database.InvoiceDao
import com.example.data.database.InvoiceEntity
import kotlinx.coroutines.flow.Flow

class InvoiceRepository(
    private val invoiceDao: InvoiceDao,
    private val configDao: ConfigDao
) {
    val allInvoices: Flow<List<InvoiceEntity>> = invoiceDao.getAllInvoices()

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
}
