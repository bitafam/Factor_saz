package com.example.ui.viewmodel

import android.content.Context
import android.widget.Toast
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.data.database.ConfigEntity
import com.example.data.database.InvoiceEntity
import com.example.data.database.ItemJsonConverter
import com.example.data.model.*
import com.example.data.repository.InvoiceRepository
import com.example.util.HtmlInvoiceGenerator
import com.example.util.LicenseManager
import com.example.util.JalaliCalendar
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import java.io.File
import com.example.util.InvoiceBackupManager
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class InvoiceViewModel(
    private val repository: InvoiceRepository,
    private val context: Context
) : ViewModel() {

    // Nav-State Screen Target: "EDITOR", "HISTORY", "ACTIVATION_LOCK", "ADMIN_PANEL"
    var currentScreen by mutableStateOf("EDITOR")
        private set

    // Observing App License Config from Database
    val appConfig: StateFlow<ConfigEntity?> = repository.configFlow
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = null
        )

    // Observing registered devices list for Admin
    val activatedDevices: StateFlow<List<com.example.data.database.ActivatedDeviceEntity>> = repository.allDevicesFlow
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    // Observing Saved Invoices from Database
    val savedInvoices: StateFlow<List<InvoiceEntity>> = repository.allInvoices
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    // Active Invoice Header States
    var id by mutableStateOf(0)
    var invoiceNo by mutableStateOf("")
    var invoiceDate by mutableStateOf("")
    var buyerName by mutableStateOf("")
    var sellerName by mutableStateOf("")
    var sellerPhone by mutableStateOf("")
    var sellerAddress by mutableStateOf("")
    var stoneCode by mutableStateOf("")
    var stoneType by mutableStateOf("")
    var managerSign by mutableStateOf("")
    var salesSign by mutableStateOf("")
    var managerSignImgBase64 by mutableStateOf("")
    var salesSignImgBase64 by mutableStateOf("")

    // Customizable Invoice Titles
    var invoiceTitle by mutableStateOf("")
    var invoiceSubtitle by mutableStateOf("")

    val deletedInvoices: StateFlow<List<InvoiceEntity>> = repository.deletedInvoices
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    // Active lists of items
    val normalItems = mutableStateListOf<ComposeInvoiceItem>()
    val simpleItems = mutableStateListOf<ComposeSimpleItem>()
    val percentageItems = mutableStateListOf<ComposePercentageItem>()

    // Calculated Grand Total including base sums and any percentage adjustments
    val grandTotal: Double
        get() {
            val normalSum = normalItems.sumOf { it.totalAmount }
            val simpleSum = simpleItems.sumOf { it.totalAmount }
            val baseSum = normalSum + simpleSum
            val percentSum = percentageItems.sumOf { (baseSum * (it.percentageStr.toDoubleOrNull() ?: 0.0)) / 100.0 }
            return baseSum + percentSum
        }

    // Input States for Activation Key
    var licenseInputKey by mutableStateOf("")
    var licensePriceInput by mutableStateOf("")

    // Admin generator state
    var adminTargetDeviceId by mutableStateOf("")
    var adminTargetUserName by mutableStateOf("")
    var adminCalculatedKey by mutableStateOf("")

    init {
        // Prepare database Configuration and check device ID on startup
        viewModelScope.launch {
            val direct = repository.getConfigDirect()
            if (direct == null) {
                val devId = LicenseManager.getDeviceId(context)
                repository.saveConfig(
                    ConfigEntity(
                        deviceId = devId,
                        isLicensed = false,
                        licensePrice = "۵,۰۰۰,۰۰۰ تومان"
                    )
                )
                sellerName = ""
                sellerPhone = ""
                sellerAddress = ""
                invoiceTitle = ""
                invoiceSubtitle = ""
                managerSign = ""
                salesSign = ""
                managerSignImgBase64 = ""
                salesSignImgBase64 = ""
            } else {
                sellerName = direct.defaultSellerName
                sellerPhone = direct.defaultSellerPhone
                sellerAddress = direct.defaultSellerAddress
                invoiceTitle = direct.defaultInvoiceTitle
                invoiceSubtitle = direct.defaultInvoiceSubtitle
                managerSign = direct.defaultManagerSign
                salesSign = direct.defaultSalesSign
                managerSignImgBase64 = direct.defaultManagerSignImg
                salesSignImgBase64 = direct.defaultSalesSignImg

                // Verify license locally against hardware hash to preserve activation across opens
                if (direct.isLicensed) {
                    val isValid = LicenseManager.verifyLicense(context, direct.licenseKey)
                    if (!isValid) {
                        repository.saveConfig(direct.copy(isLicensed = false, licenseKey = ""))
                    }
                }
            }

            // Sync/Read backups in public DOCUMENTS directory to auto-restore all records upon reinstall
            try {
                val backups = InvoiceBackupManager.loadAllBackups()
                for (backup in backups) {
                    val existing = repository.getInvoiceById(backup.id)
                    if (existing == null) {
                        repository.insertInvoice(backup)
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }

            // Auto-restore admin's coworker device license backups upon reinstall as well
            try {
                val backupLicenses = InvoiceBackupManager.loadLicensesBackup()
                for (lic in backupLicenses) {
                    val existing = repository.getDeviceById(lic.deviceId)
                    if (existing == null) {
                        repository.saveDevice(lic)
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        setupDefaultInvoice()
    }

    private fun setupDefaultInvoice() {
        if (invoiceNo.isBlank()) {
            invoiceNo = "INV-${(1000..9999).random()}"
        }
        setTodayDate()
        if (normalItems.isEmpty()) {
            addNormalItemRow()
        }
    }

    fun setTodayDate() {
        invoiceDate = JalaliCalendar.getTodayJalali()
    }

    // Navigate Utility
    fun navigateTo(screen: String) {
        viewModelScope.launch {
            val config = repository.getConfigDirect()
            val isLicensed = config?.isLicensed ?: false
            
            if (!isLicensed && screen != "ACTIVATION_LOCK" && screen != "ADMIN_PANEL") {
                // If not activated, force activation lock except for the Activation lock screens
                currentScreen = "ACTIVATION_LOCK"
            } else {
                currentScreen = screen
            }
        }
    }

    // Clear and start new invoice
    fun createNewInvoice() {
        id = 0
        invoiceNo = "INV-${(1000..9999).random()}"
        buyerName = ""
        stoneCode = ""
        stoneType = ""
        viewModelScope.launch {
            val direct = repository.getConfigDirect()
            sellerName = direct?.defaultSellerName ?: ""
            sellerPhone = direct?.defaultSellerPhone ?: ""
            sellerAddress = direct?.defaultSellerAddress ?: ""
            invoiceTitle = direct?.defaultInvoiceTitle ?: ""
            invoiceSubtitle = direct?.defaultInvoiceSubtitle ?: ""
            managerSign = direct?.defaultManagerSign ?: ""
            salesSign = direct?.defaultSalesSign ?: ""
            managerSignImgBase64 = direct?.defaultManagerSignImg ?: ""
            salesSignImgBase64 = direct?.defaultSalesSignImg ?: ""
        }
        normalItems.clear()
        simpleItems.clear()
        percentageItems.clear()
        addNormalItemRow()
        setTodayDate()
        Toast.makeText(context, "فاکتور جدید ایجاد شد", Toast.LENGTH_SHORT).show()
    }

    // Update permanent default settings
    fun updateDefaultSettings(
        sName: String,
        sPhone: String,
        sAddress: String,
        title: String,
        sub: String,
        mSign: String,
        sSign: String,
        mSignImg: String,
        sSignImg: String
    ) {
        viewModelScope.launch {
            val currentConfig = repository.getConfigDirect() ?: ConfigEntity(deviceId = LicenseManager.getDeviceId(context))
            val updated = currentConfig.copy(
                defaultSellerName = sName,
                defaultSellerPhone = sPhone,
                defaultSellerAddress = sAddress,
                defaultInvoiceTitle = title,
                defaultInvoiceSubtitle = sub,
                defaultManagerSign = mSign,
                defaultSalesSign = sSign,
                defaultManagerSignImg = mSignImg,
                defaultSalesSignImg = sSignImg
            )
            repository.saveConfig(updated)
            
            // Also apply to current editor instance
            sellerName = sName
            sellerPhone = sPhone
            sellerAddress = sAddress
            invoiceTitle = title
            invoiceSubtitle = sub
            managerSign = mSign
            salesSign = sSign
            managerSignImgBase64 = mSignImg
            salesSignImgBase64 = sSignImg
            
            Toast.makeText(context, "تنظیمات پیش‌فرض با موفقیت ذخیره شد", Toast.LENGTH_SHORT).show()
        }
    }

    // Add list items
    fun addNormalItemRow() {
        normalItems.add(ComposeInvoiceItem())
    }

    fun addSimpleItemRow(desc: String, amtStr: String, qtyStr: String = "") {
        simpleItems.add(ComposeSimpleItem(description = desc, totalAmountStr = amtStr, quantityStr = qtyStr))
    }

    fun addPercentageItemRow(desc: String, pctStr: String) {
        percentageItems.add(ComposePercentageItem(description = desc, percentageStr = pctStr))
    }

    // Update item lists at a index
    fun updateNormalItem(index: Int, updated: ComposeInvoiceItem) {
        if (index in normalItems.indices) {
            normalItems[index] = updated
        }
    }

    fun updateSimpleItem(index: Int, updated: ComposeSimpleItem) {
        if (index in simpleItems.indices) {
            simpleItems[index] = updated
        }
    }

    fun updatePercentageItem(index: Int, updated: ComposePercentageItem) {
        if (index in percentageItems.indices) {
            percentageItems[index] = updated
        }
    }

    fun removeNormalItemAt(index: Int) {
        if (index in normalItems.indices) {
            normalItems.removeAt(index)
        }
    }

    fun removeSimpleItemAt(index: Int) {
        if (index in simpleItems.indices) {
            simpleItems.removeAt(index)
        }
    }

    fun removePercentageItemAt(index: Int) {
        if (index in percentageItems.indices) {
            percentageItems.removeAt(index)
        }
    }

    fun removeLastRow() {
        if (percentageItems.isNotEmpty()) {
            percentageItems.removeAt(percentageItems.size - 1)
        } else if (simpleItems.isNotEmpty()) {
            simpleItems.removeAt(simpleItems.size - 1)
        } else if (normalItems.isNotEmpty()) {
            normalItems.removeAt(normalItems.size - 1)
        }
    }

    // Check License Key Inputed or Special Admin Command
    fun attemptActivation() {
        val trimmed = licenseInputKey.trim()
        if (trimmed == LicenseManager.MASTER_ADMIN_PASSCODE) {
            viewModelScope.launch {
                val currentConfig = repository.getConfigDirect() ?: ConfigEntity(deviceId = LicenseManager.getDeviceId(context))
                val superKey = LicenseManager.MASTER_ADMIN_PASSCODE
                repository.saveConfig(
                    currentConfig.copy(
                        isLicensed = true,
                        licenseKey = superKey
                    )
                )
                Toast.makeText(context, "برنامه با موفقیت فعال شد! خوش آمدید همکار گرامی.", Toast.LENGTH_LONG).show()
                navigateTo("EDITOR")
            }
            return
        }

        val isValid = LicenseManager.verifyLicense(context, trimmed)
        if (isValid) {
            viewModelScope.launch {
                val currentConfig = repository.getConfigDirect() ?: ConfigEntity(deviceId = LicenseManager.getDeviceId(context))
                repository.saveConfig(
                    currentConfig.copy(
                        isLicensed = true,
                        licenseKey = trimmed
                    )
                )
                Toast.makeText(context, "برنامه با موفقیت فعال شد!", Toast.LENGTH_LONG).show()
                navigateTo("EDITOR")
            }
        } else {
            Toast.makeText(context, "کد فعال‌سازی نامعتبر است", Toast.LENGTH_SHORT).show()
        }
    }

    // De-activate license (re-lock) for testing/delivery
    fun adminDeactivate() {
        viewModelScope.launch {
            val currentConfig = repository.getConfigDirect() ?: ConfigEntity(deviceId = LicenseManager.getDeviceId(context))
            repository.saveConfig(currentConfig.copy(isLicensed = false, licenseKey = ""))
            Toast.makeText(context, "لایسنس غیرفعال شد", Toast.LENGTH_SHORT).show()
            navigateTo("ACTIVATION_LOCK")
        }
    }

    // Force Activate Bypass inside Admin panel
    fun adminForceActivate() {
        viewModelScope.launch {
            val currentConfig = repository.getConfigDirect() ?: ConfigEntity(deviceId = LicenseManager.getDeviceId(context))
            val superKey = LicenseManager.MASTER_ADMIN_PASSCODE // Save master admin key
            repository.saveConfig(currentConfig.copy(isLicensed = true, licenseKey = superKey))
            Toast.makeText(context, "برنامه به عنوان لایسنس ادمین فعال شد", Toast.LENGTH_SHORT).show()
            navigateTo("EDITOR")
        }
    }

    // Modify base license price in DB
    fun adminUpdatePrice(newPrice: String) {
        viewModelScope.launch {
            val currentConfig = repository.getConfigDirect() ?: ConfigEntity(deviceId = LicenseManager.getDeviceId(context))
            repository.saveConfig(currentConfig.copy(licensePrice = newPrice))
            Toast.makeText(context, "قیمت لایسنس به‌روزرسانی شد", Toast.LENGTH_SHORT).show()
        }
    }

    // Compute license keys inside Admin panel for other devices and register them
    fun adminGenerateKeyForDevice() {
        val target = adminTargetDeviceId.trim().uppercase()
        val userVal = adminTargetUserName.trim()
        if (target.isNotBlank()) {
            val key = LicenseManager.generateLicenseKey(target)
            adminCalculatedKey = key
            viewModelScope.launch(Dispatchers.IO) {
                val todayJalali = JalaliCalendar.getTodayJalali()
                repository.saveDevice(
                    com.example.data.database.ActivatedDeviceEntity(
                        deviceId = target,
                        licenseKey = key,
                        activationDate = todayJalali,
                        isRevoked = false,
                        userName = userVal
                    )
                )
                // Write active licenses list backup permanently to public storage
                val updatedDevices = repository.getAllDevicesDirect()
                InvoiceBackupManager.saveLicensesBackup(updatedDevices)

                launch(Dispatchers.Main) {
                    Toast.makeText(context, "لایسنس با موفقیت تولید و در لیست همکاران ثبت شد", Toast.LENGTH_LONG).show()
                    adminTargetDeviceId = ""
                    adminTargetUserName = ""
                }
            }
        } else {
            Toast.makeText(context, "شناسه دستگاه را وارد کنید", Toast.LENGTH_SHORT).show()
        }
    }

    // Revoke/Delete coworker license access
    fun deleteDeviceLicense(deviceId: String) {
        viewModelScope.launch(Dispatchers.IO) {
            repository.deleteDeviceById(deviceId)
            // Synchronize public list backup
            val updatedDevices = repository.getAllDevicesDirect()
            InvoiceBackupManager.saveLicensesBackup(updatedDevices)
            
            launch(Dispatchers.Main) {
                Toast.makeText(context, "دسترسی لایسنس برای دستگاه لغو و حذف شد.", Toast.LENGTH_SHORT).show()
            }
        }
    }

    // Save active draft to DB for loading/restoring later
    fun saveCurrentInvoice() {
        if (buyerName.isBlank()) {
            Toast.makeText(context, "لطفاً نام خریدار را وارد کنید", Toast.LENGTH_SHORT).show()
            return
        }

        viewModelScope.launch {
            val itemsJson = ItemJsonConverter.serializeInvoiceItems(normalItems)
            val simpleItemsJson = ItemJsonConverter.serializeSimpleItems(simpleItems)
            val pctJson = ItemJsonConverter.serializePercentageItems(percentageItems)

            val entity = InvoiceEntity(
                id = id,
                invoiceNo = invoiceNo,
                invoiceDate = invoiceDate,
                buyerName = buyerName,
                sellerName = sellerName,
                sellerPhone = sellerPhone,
                sellerAddress = sellerAddress,
                stoneCode = stoneCode,
                stoneType = stoneType,
                managerSign = managerSign,
                salesSign = salesSign,
                itemsJson = itemsJson,
                simpleItemsJson = simpleItemsJson,
                percentageItemsJson = pctJson,
                invoiceTitle = invoiceTitle,
                invoiceSubtitle = invoiceSubtitle,
                totalAmount = grandTotal,
                managerSignImgBase64 = managerSignImgBase64,
                salesSignImgBase64 = salesSignImgBase64
            )

            val savedId = repository.insertInvoice(entity)
            val finalId = if (id == 0) savedId.toInt() else id
            if (id == 0) {
                id = finalId
            }
            
            // Backup the saved invoice data permanently inside the public 'Backup faktors' directory
            val backupEntity = entity.copy(id = finalId)
            val backupFile = InvoiceBackupManager.saveInvoiceBackup(backupEntity)
            
            val pathInfo = if (backupFile != null) {
                "\n\nذخیره در بکاپ مخصوص برنامه:\n${backupFile.absolutePath}"
            } else ""

            Toast.makeText(context, "فاکتور ذخیره گردید.$pathInfo", Toast.LENGTH_LONG).show()
        }
    }

    // Auto-saves the invoice silently (no user alerts)
    fun autoSaveInvoiceSilently() {
        val nameToSave = buyerName.trim().ifBlank { "پیش‌نویس خودکار" }
        viewModelScope.launch {
            val itemsJson = ItemJsonConverter.serializeInvoiceItems(normalItems)
            val simpleItemsJson = ItemJsonConverter.serializeSimpleItems(simpleItems)
            val pctJson = ItemJsonConverter.serializePercentageItems(percentageItems)

            val entity = InvoiceEntity(
                id = id,
                invoiceNo = invoiceNo,
                invoiceDate = invoiceDate,
                buyerName = nameToSave,
                sellerName = sellerName,
                sellerPhone = sellerPhone,
                sellerAddress = sellerAddress,
                stoneCode = stoneCode,
                stoneType = stoneType,
                managerSign = managerSign,
                salesSign = salesSign,
                itemsJson = itemsJson,
                simpleItemsJson = simpleItemsJson,
                percentageItemsJson = pctJson,
                invoiceTitle = invoiceTitle,
                invoiceSubtitle = invoiceSubtitle,
                totalAmount = grandTotal,
                managerSignImgBase64 = managerSignImgBase64,
                salesSignImgBase64 = salesSignImgBase64
            )

            val savedId = repository.insertInvoice(entity)
            val finalId = if (id == 0) savedId.toInt() else id
            if (id == 0) {
                id = finalId
            }
            // Silent backup of draft to storage
            InvoiceBackupManager.saveInvoiceBackup(entity.copy(id = finalId))
        }
    }

    // Load invoice into active editor state
    fun loadInvoice(invoice: InvoiceEntity) {
        id = invoice.id
        invoiceNo = invoice.invoiceNo
        invoiceDate = invoice.invoiceDate
        buyerName = invoice.buyerName
        sellerName = invoice.sellerName
        sellerPhone = invoice.sellerPhone
        sellerAddress = invoice.sellerAddress
        stoneCode = invoice.stoneCode
        stoneType = invoice.stoneType
        managerSign = invoice.managerSign
        salesSign = invoice.salesSign
        managerSignImgBase64 = invoice.managerSignImgBase64
        salesSignImgBase64 = invoice.salesSignImgBase64
        invoiceTitle = invoice.invoiceTitle.ifBlank { "فاکتور فروش صنایع سنگ ایران کوارتز" }
        invoiceSubtitle = invoice.invoiceSubtitle.ifBlank { "مجری فروش اسلب کوارتز ساخت و نصب کانترتاپ کوارتز کاینداستون توتم گریفین" }

        val loadedNormals = ItemJsonConverter.deserializeInvoiceItems(invoice.itemsJson)
        val loadedSimples = ItemJsonConverter.deserializeSimpleItems(invoice.simpleItemsJson)
        val loadedPercentages = ItemJsonConverter.deserializePercentageItems(invoice.percentageItemsJson)

        normalItems.clear()
        normalItems.addAll(loadedNormals)

        simpleItems.clear()
        simpleItems.addAll(loadedSimples)

        percentageItems.clear()
        percentageItems.addAll(loadedPercentages)

        if (normalItems.isEmpty()) {
            addNormalItemRow()
        }

        navigateTo("EDITOR")
        Toast.makeText(context, "فاکتور بارگذاری شد", Toast.LENGTH_SHORT).show()
    }

    // Soft delete saved invoice
    fun deleteInvoice(invoice: InvoiceEntity) {
        viewModelScope.launch {
            repository.setDeletedStatus(invoice.id, true)
            Toast.makeText(context, "فاکتور به سطل زباله منتقل شد. قابل بازیابی در حساب کاربری.", Toast.LENGTH_LONG).show()
        }
    }

    fun restoreInvoice(invoice: InvoiceEntity) {
        viewModelScope.launch {
            repository.setDeletedStatus(invoice.id, false)
            Toast.makeText(context, "فاکتور با موفقیت بازیابی شد", Toast.LENGTH_SHORT).show()
        }
    }

    fun permanentDeleteInvoice(invoice: InvoiceEntity) {
        viewModelScope.launch {
            repository.deleteInvoice(invoice)
            Toast.makeText(context, "فاکتور برای همیشه حذف شد", Toast.LENGTH_SHORT).show()
        }
    }

    // Render HTML and execute Print action
    fun printCurrentInvoice(printContext: Context) {
        // Auto-save silently first to prevent data loss
        autoSaveInvoiceSilently()

        val itemsJson = ItemJsonConverter.serializeInvoiceItems(normalItems)
        val simpleItemsJson = ItemJsonConverter.serializeSimpleItems(simpleItems)
        val pctJson = ItemJsonConverter.serializePercentageItems(percentageItems)

        val entity = InvoiceEntity(
            id = id,
            invoiceNo = invoiceNo,
            invoiceDate = invoiceDate,
            buyerName = buyerName,
            sellerName = sellerName,
            sellerPhone = sellerPhone,
            sellerAddress = sellerAddress,
            stoneCode = stoneCode,
            stoneType = stoneType,
            managerSign = managerSign,
            salesSign = salesSign,
            itemsJson = itemsJson,
            simpleItemsJson = simpleItemsJson,
            percentageItemsJson = pctJson,
            invoiceTitle = invoiceTitle,
            invoiceSubtitle = invoiceSubtitle,
            totalAmount = grandTotal,
            managerSignImgBase64 = managerSignImgBase64,
            salesSignImgBase64 = salesSignImgBase64
        )

        val html = HtmlInvoiceGenerator.generateHtml(entity)
        val finalJobName = if (buyerName.trim().isNotBlank()) {
            "فاکتور_${buyerName.trim()}"
        } else {
            "فاکتور_${invoiceNo}"
        }
        executeWebViewPrint(printContext, html, finalJobName)
    }

    fun printSummaryReport(printContext: Context, startDate: String, endDate: String, selectedInvoices: List<InvoiceEntity>) {
        try {
            val html = HtmlInvoiceGenerator.generateSummaryHtml(startDate, endDate, selectedInvoices)
            val safeStart = startDate.replace("/", ".")
            val safeEnd = endDate.replace("/", ".")
            val jobName = "خلاصه_فاکتور_های_از_${safeStart}_تا_${safeEnd}"
            executeWebViewPrint(printContext, html, jobName)
        } catch (e: Exception) {
            Toast.makeText(printContext, "خطا در چاپ گزارش: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun executeWebViewPrint(printContext: Context, html: String, jobName: String) {
        try {
            val mainLooper = android.os.Looper.getMainLooper()
            val runnable = Runnable {
                val webView = android.webkit.WebView(printContext)
                webView.webViewClient = object : android.webkit.WebViewClient() {
                    override fun onPageFinished(view: android.webkit.WebView?, url: String?) {
                        val printManager = printContext.getSystemService(Context.PRINT_SERVICE) as android.print.PrintManager
                        val printAdapter = webView.createPrintDocumentAdapter(jobName)
                        val printAttributes = android.print.PrintAttributes.Builder()
                            .setMediaSize(android.print.PrintAttributes.MediaSize.ISO_A4)
                            .setResolution(android.print.PrintAttributes.Resolution("pdf", "PDF Output", 600, 600))
                            .setMinMargins(android.print.PrintAttributes.Margins.NO_MARGINS)
                            .build()
                        printManager.print(jobName, printAdapter, printAttributes)
                    }
                }
                webView.loadDataWithBaseURL(null, html, "text/html", "utf-8", null)
            }

            if (android.os.Looper.myLooper() == mainLooper) {
                runnable.run()
            } else {
                android.os.Handler(mainLooper).post(runnable)
            }
        } catch (e: Exception) {
            Toast.makeText(printContext, "خطا در پرینت: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    var showShareConfirmDialog by mutableStateOf(false)
        private set
    var pendingShareInvoice by mutableStateOf<InvoiceEntity?>(null)
        private set

    fun dismissShareConfirmDialog() {
        showShareConfirmDialog = false
        pendingShareInvoice = null
    }

    private fun startShareIntent(context: Context, file: java.io.File) {
        try {
            val authority = "${context.packageName}.fileprovider"
            val uri = androidx.core.content.FileProvider.getUriForFile(context, authority, file)
            val intent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                type = "application/pdf"
                putExtra(android.content.Intent.EXTRA_STREAM, uri)
                addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(android.content.Intent.createChooser(intent, "اشتراک‌گذاری فاکتور پی‌دی‌اف"))
        } catch (e: Exception) {
            Toast.makeText(context, "خطا در اشتراک‌گذاری فایل: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    fun saveInvoicePdfProgrammatically(context: Context, invoice: InvoiceEntity, onComplete: (File?) -> Unit) {
        try {
            val html = HtmlInvoiceGenerator.generateHtml(invoice)
            val safeBuyer = invoice.buyerName.trim().replace(Regex("[\\\\/:*?\"<>|]"), "_")
            val safeNo = invoice.invoiceNo.trim().replace(Regex("[\\\\/:*?\"<>|]"), "_")
            val fileName = "فاکتور_${safeBuyer}_${safeNo}.pdf"
            
            val pdfDir = InvoiceBackupManager.getPdfFolder()
            val outputFile = File(pdfDir, fileName)
            
            val mainLooper = android.os.Looper.getMainLooper()
            android.os.Handler(mainLooper).post {
                val webView = android.webkit.WebView(context)
                webView.webViewClient = object : android.webkit.WebViewClient() {
                    override fun onPageFinished(view: android.webkit.WebView?, url: String?) {
                        val printAdapter = webView.createPrintDocumentAdapter("pdf_print")
                        val printAttributes = android.print.PrintAttributes.Builder()
                            .setMediaSize(android.print.PrintAttributes.MediaSize.ISO_A4)
                            .setResolution(android.print.PrintAttributes.Resolution("pdf", "PDF Output", 600, 600))
                            .setMinMargins(android.print.PrintAttributes.Margins.NO_MARGINS)
                            .build()
                        
                        try {
                            val pfd = android.os.ParcelFileDescriptor.open(
                                outputFile,
                                android.os.ParcelFileDescriptor.MODE_READ_WRITE or 
                                android.os.ParcelFileDescriptor.MODE_CREATE or 
                                android.os.ParcelFileDescriptor.MODE_TRUNCATE
                            )
                            android.print.PdfPrinterBridge.printToPdf(printAdapter, printAttributes, pfd, object : android.print.PdfPrinterBridge.PdfResultCallback {
                                override fun onWriteFinished() {
                                    try { pfd.close() } catch (e: Exception) {}
                                    onComplete(outputFile)
                                }
                                override fun onWriteFailed(error: String?) {
                                    try { pfd.close() } catch (e: Exception) {}
                                    onComplete(null)
                                }
                            })
                        } catch (e: Exception) {
                            e.printStackTrace()
                            onComplete(null)
                        }
                    }
                }
                webView.loadDataWithBaseURL(null, html, "text/html", "utf-8", null)
            }
        } catch (e: Exception) {
            e.printStackTrace()
            onComplete(null)
        }
    }

    fun saveSummaryPdfProgrammatically(context: Context, startDate: String, endDate: String, selectedInvoices: List<InvoiceEntity>, onComplete: (File?) -> Unit) {
        try {
            val html = HtmlInvoiceGenerator.generateSummaryHtml(startDate, endDate, selectedInvoices)
            val safeStart = startDate.replace("/", ".")
            val safeEnd = endDate.replace("/", ".")
            val fileName = "خلاصه_فاکتور_های_از_${safeStart}_تا_${safeEnd}.pdf"
            
            val summaryDir = InvoiceBackupManager.getSummaryFolder()
            val outputFile = File(summaryDir, fileName)
            
            val mainLooper = android.os.Looper.getMainLooper()
            android.os.Handler(mainLooper).post {
                val webView = android.webkit.WebView(context)
                webView.webViewClient = object : android.webkit.WebViewClient() {
                    override fun onPageFinished(view: android.webkit.WebView?, url: String?) {
                        val printAdapter = webView.createPrintDocumentAdapter("pdf_summary_print")
                        val printAttributes = android.print.PrintAttributes.Builder()
                            .setMediaSize(android.print.PrintAttributes.MediaSize.ISO_A4)
                            .setResolution(android.print.PrintAttributes.Resolution("pdf", "PDF Output", 600, 600))
                            .setMinMargins(android.print.PrintAttributes.Margins.NO_MARGINS)
                            .build()
                        
                        try {
                            val pfd = android.os.ParcelFileDescriptor.open(
                                outputFile,
                                android.os.ParcelFileDescriptor.MODE_READ_WRITE or 
                                android.os.ParcelFileDescriptor.MODE_CREATE or 
                                android.os.ParcelFileDescriptor.MODE_TRUNCATE
                            )
                            android.print.PdfPrinterBridge.printToPdf(printAdapter, printAttributes, pfd, object : android.print.PdfPrinterBridge.PdfResultCallback {
                                override fun onWriteFinished() {
                                    try { pfd.close() } catch (e: Exception) {}
                                    onComplete(outputFile)
                                }
                                override fun onWriteFailed(error: String?) {
                                    try { pfd.close() } catch (e: Exception) {}
                                    onComplete(null)
                                }
                            })
                        } catch (e: Exception) {
                            e.printStackTrace()
                            onComplete(null)
                        }
                    }
                }
                webView.loadDataWithBaseURL(null, html, "text/html", "utf-8", null)
            }
        } catch (e: Exception) {
            e.printStackTrace()
            onComplete(null)
        }
    }

    fun triggerSyncAndLoadBackups(context: Context) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                var importedCount = 0
                val backups = InvoiceBackupManager.loadAllBackups()
                for (backup in backups) {
                    val existing = repository.getInvoiceById(backup.id)
                    if (existing == null) {
                        repository.insertInvoice(backup)
                        importedCount++
                    }
                }
                
                // Also load and restore coworker devices
                val backupLicenses = InvoiceBackupManager.loadLicensesBackup()
                for (lic in backupLicenses) {
                    val existing = repository.getDeviceById(lic.deviceId)
                    if (existing == null) {
                        repository.saveDevice(lic)
                    }
                }

                launch(Dispatchers.Main) {
                    Toast.makeText(context, "$importedCount فاکتور با موفقیت از پوشه پشتیبان بازیابی شدند.", Toast.LENGTH_LONG).show()
                }
            } catch (e: Exception) {
                launch(Dispatchers.Main) {
                    Toast.makeText(context, "خطا در همگام‌سازى بکاپ: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    fun autoSavePdfAndShare(context: Context, invoice: InvoiceEntity) {
        Toast.makeText(context, "در حال تولید فایل PDF فاکتور...", Toast.LENGTH_SHORT).show()
        saveInvoicePdfProgrammatically(context, invoice) { file ->
            if (file != null && file.exists()) {
                val absPath = file.absolutePath
                Toast.makeText(context, "فایل PDF فاکتور ذخیره شد:\n$absPath", Toast.LENGTH_LONG).show()
                startShareIntent(context, file)
            } else {
                Toast.makeText(context, "خطا در تبدیل PDF خودکار. بازکردن چاپگر...", Toast.LENGTH_SHORT).show()
                try {
                    val html = HtmlInvoiceGenerator.generateHtml(invoice)
                    val finalJobName = if (invoice.buyerName.trim().isNotBlank()) {
                        "فاکتور_${invoice.buyerName.trim()}"
                    } else {
                        "فاکتور_${invoice.invoiceNo}"
                    }
                    executeWebViewPrint(context, html, finalJobName)
                } catch (ex: Exception) {
                    Toast.makeText(context, "خطا در شروع پرینت: ${ex.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    // Share/Print saved invoice from History list
    fun shareInvoice(shareContext: Context, invoice: InvoiceEntity) {
        try {
            val safeBuyer = invoice.buyerName.trim().replace(Regex("[\\\\/:*?\"<>|]"), "_")
            val safeNo = invoice.invoiceNo.trim().replace(Regex("[\\\\/:*?\"<>|]"), "_")
            val fileName = "فاکتور_${safeBuyer}_${safeNo}.pdf"
            
            val pdfDir = InvoiceBackupManager.getPdfFolder()
            val pdfFile = File(pdfDir, fileName)
            
            if (pdfFile.exists() && pdfFile.length() > 0) {
                // File found, share directly!
                startShareIntent(shareContext, pdfFile)
            } else {
                // File not found, render and share automatically!
                autoSavePdfAndShare(shareContext, invoice)
            }
        } catch (e: Exception) {
            Toast.makeText(shareContext, "خطا در شروع اشتراک‌گذاری: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }
}

class InvoiceViewModelFactory(
    private val repository: InvoiceRepository,
    private val context: Context
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(InvoiceViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return InvoiceViewModel(repository, context) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
