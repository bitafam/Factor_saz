package com.example.ui.viewmodel

import android.content.Context
import android.net.Uri
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
import com.example.util.cloud.ArvanCloudS3Client
import com.example.util.cloud.InvoiceCloudSyncManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import com.example.util.InvoiceBackupManager
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class InvoiceViewModel(
    private val repository: InvoiceRepository,
    private val context: Context
) : ViewModel() {

    // Nav-State Screen Target: "EDITOR", "HISTORY", "ACTIVATION_LOCK", "ADMIN_PANEL", "ACCOUNT", "SETTINGS", "CLOUD_FILE_MANAGER"
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

    // ArvanCloud Object Storage (صندوقچه ابری آروان کلود) Configuration States
    var arvanEndpoint by mutableStateOf("s3.ir-thr-at1.arvanstorage.ir")
    var arvanBucket by mutableStateOf("")
    var arvanAccessKey by mutableStateOf("")
    var arvanSecretKey by mutableStateOf("")
    var arvanCustomDomain by mutableStateOf("")
    var arvanAutoSync by mutableStateOf(true)
    var isTestingArvanConnection by mutableStateOf(false)
    var arvanTestStatusMessage by mutableStateOf<String?>(null)
    var isArvanTestSuccess by mutableStateOf<Boolean?>(null)

    // Current Invoice Cloud & Attachment States
    var cloudHtmlUrl by mutableStateOf("")
    val activeAttachments = mutableStateListOf<InvoiceAttachment>()
    var isUploadingAttachment by mutableStateOf(false)

    // Batch Cloud Sync Progress States (For Safe Restore & Full Queue Sync)
    var isBatchSyncActive by mutableStateOf(false)
    var batchSyncCurrent by mutableStateOf(0)
    var batchSyncTotal by mutableStateOf(0)
    var batchSyncCurrentInvoiceName by mutableStateOf("")
    var batchSyncStatusText by mutableStateOf("")
    var batchSyncSuccessCount by mutableStateOf(0)
    var batchSyncSkippedCount by mutableStateOf(0)
    var batchSyncFailedCount by mutableStateOf(0)

    // Cloud File Manager Screen States
    val cloudFiles = mutableStateListOf<CloudFileItem>()
    var isLoadingCloudFiles by mutableStateOf(false)
    var cloudFilesError by mutableStateOf<String?>(null)
    var cloudFileSearchQuery by mutableStateOf("")

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
            val percentSum = percentageItems.sumOf {
                val pct = com.example.util.importer.TextNormalizer.parseNumber(it.percentageStr) ?: 0.0
                ((baseSum * pct) / 100.0).let { kotlin.math.round(it) }
            }
            return (baseSum + percentSum).let { kotlin.math.round(it) }
        }

    // Input States for Activation Key
    var licenseInputKey by mutableStateOf("")
    var licensePriceInput by mutableStateOf("")

    // Admin generator state
    var adminTargetDeviceId by mutableStateOf("")
    var adminTargetUserName by mutableStateOf("")
    var adminCalculatedKey by mutableStateOf("")

    // Full Offline Backup & Restore States
    var isBackupOperationLoading by mutableStateOf(false)
    var backupOperationMessage by mutableStateOf<String?>(null)
    private val _availableBackups = MutableStateFlow<List<com.example.util.BackupFileInfo>>(emptyList())
    val availableBackups: StateFlow<List<com.example.util.BackupFileInfo>> = _availableBackups.asStateFlow()

    val totalRevenueFlow: StateFlow<Double> = repository.totalRevenue.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        0.0
    )
    val totalCountFlow: StateFlow<Int> = repository.activeInvoiceCount.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        0
    )

    private fun formatPriceSuggestion(numStr: String): String {
        return try {
            val n = numStr.toLongOrNull() ?: return numStr
            java.text.DecimalFormat("#,###").format(n)
        } catch (e: Exception) { numStr }
    }

    val suggestedPrices: StateFlow<List<String>> = repository.allInvoices.map { invoices: List<InvoiceEntity> ->
        val priceCounts = mutableMapOf<String, Int>()
        val recentList = mutableListOf<String>()
        for (inv in invoices) {
            val items = ItemJsonConverter.deserializeInvoiceItems(inv.itemsJson)
            for (item in items) {
                val p = item.price60cm.trim().replace(",", "").replace(" ", "")
                if (p.isNotBlank() && p != "0") {
                    priceCounts[p] = (priceCounts[p] ?: 0) + 1
                    if (!recentList.contains(p)) {
                        recentList.add(p)
                    }
                }
            }
        }
        val topFrequent = priceCounts.entries.sortedByDescending { it.value }.map { it.key }.take(3)
        val topRecent = recentList.take(3)
        val combined = (topRecent + topFrequent).distinct().take(5)
        if (combined.isEmpty()) {
            listOf("115,000,000", "95,000,000", "125,000,000")
        } else {
            combined.map { formatPriceSuggestion(it) }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), listOf("115,000,000", "95,000,000", "125,000,000"))

    val recentFrequentPrices: StateFlow<List<String>> = suggestedPrices

    fun isDuplicateInvoice(newInv: InvoiceEntity, existingList: List<InvoiceEntity>): Boolean {
        val newNo = newInv.invoiceNo.trim()
        val newBuyer = newInv.buyerName.trim()
        val newDate = newInv.invoiceDate.trim()
        val newCalcTotal = HtmlInvoiceGenerator.calculateInvoiceTotal(newInv)
        val newTotal = if (newCalcTotal > 0.0) newCalcTotal else newInv.totalAmount

        return existingList.any { existing ->
            if (existing.isDeleted) return@any false
            val exNo = existing.invoiceNo.trim()
            val exBuyer = existing.buyerName.trim()
            val exDate = existing.invoiceDate.trim()
            val exCalcTotal = HtmlInvoiceGenerator.calculateInvoiceTotal(existing)
            val exTotal = if (exCalcTotal > 0.0) exCalcTotal else existing.totalAmount

            // Match 1: Same invoice number (if not generic/blank) and same buyer
            val noMatch = exNo.isNotBlank() && !exNo.startsWith("INV-") && 
                    exNo.equals(newNo, ignoreCase = true) &&
                    exBuyer.isNotBlank() && exBuyer.equals(newBuyer, ignoreCase = true)
            if (noMatch) return@any true

            // Match 2: Exact buyer, date, and total amount
            val exactMatch = exBuyer.isNotBlank() && exBuyer.equals(newBuyer, ignoreCase = true) &&
                    exDate.isNotBlank() && exDate == newDate &&
                    kotlin.math.abs(exTotal - newTotal) < 1.0
            if (exactMatch) return@any true

            // Match 3: Same items JSON content, buyer, and total amount
            if (exBuyer.isNotBlank() && exBuyer.equals(newBuyer, ignoreCase = true) &&
                existing.itemsJson.isNotBlank() && existing.itemsJson == newInv.itemsJson &&
                kotlin.math.abs(exTotal - newTotal) < 1.0) {
                return@any true
            }

            false
        }
    }

    init {
        refreshAvailableBackups()
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
                arvanEndpoint = direct.arvanEndpoint.ifBlank { "s3.ir-thr-at1.arvanstorage.ir" }
                arvanBucket = direct.arvanBucket
                arvanAccessKey = direct.arvanAccessKey
                arvanSecretKey = direct.arvanSecretKey
                arvanCustomDomain = direct.arvanCustomDomain
                arvanAutoSync = direct.arvanAutoSync

                // Verify license locally against hardware hash to preserve activation across opens
                if (direct.isLicensed) {
                    val isValid = LicenseManager.verifyLicense(context, direct.licenseKey)
                    if (!isValid) {
                        repository.saveConfig(direct.copy(isLicensed = false, licenseKey = ""))
                    }
                }
            }

            // Ensure all existing invoices in database have exact totalAmount calculated from their items
            try {
                val existingInvoices = repository.getAllInvoicesDirect().toMutableList()
                for (inv in existingInvoices) {
                    val calcTotal = HtmlInvoiceGenerator.calculateInvoiceTotal(inv)
                    if (calcTotal > 0.0 && kotlin.math.abs(calcTotal - inv.totalAmount) >= 0.5) {
                        repository.updateInvoice(inv.copy(totalAmount = calcTotal))
                    }
                }

                // Sync/Read backups in public DOCUMENTS directory to auto-restore only if database is completely empty (reinstall)
                if (existingInvoices.isEmpty()) {
                    val backups = InvoiceBackupManager.loadAllBackups()
                    for (backup in backups) {
                        val exactTotal = HtmlInvoiceGenerator.calculateInvoiceTotal(backup)
                        val normalized = if (exactTotal > 0.0) backup.copy(totalAmount = exactTotal) else backup
                        if (!isDuplicateInvoice(normalized, existingInvoices)) {
                            val clean = normalized.copy(id = 0, isDeleted = false)
                            val newId = repository.insertInvoice(clean)
                            existingInvoices.add(clean.copy(id = newId.toInt()))
                        }
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
        cloudHtmlUrl = ""
        activeAttachments.clear()
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

    // Import Dialog States
    var isImportDialogOpen by mutableStateOf(false)
    var pendingImportResult by mutableStateOf<com.example.util.importer.ParsedInvoiceResult?>(null)
    var isImportingLoading by mutableStateOf(false)

    fun openImportDialog() {
        pendingImportResult = null
        isImportDialogOpen = true
    }

    fun closeImportDialog() {
        pendingImportResult = null
        isImportDialogOpen = false
    }

    fun importFromUri(uri: android.net.Uri) {
        viewModelScope.launch(Dispatchers.IO) {
            isImportingLoading = true
            try {
                val result = com.example.util.importer.InvoiceImportManager.parseFromUri(context, uri)
                launch(Dispatchers.Main) {
                    pendingImportResult = result
                    isImportingLoading = false
                }
            } catch (e: Exception) {
                launch(Dispatchers.Main) {
                    isImportingLoading = false
                    Toast.makeText(context, "خطا در پردازش فایل: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    fun importFromText(text: String) {
        viewModelScope.launch(Dispatchers.IO) {
            isImportingLoading = true
            try {
                val result = com.example.util.importer.InvoiceImportManager.parseFromText(text)
                launch(Dispatchers.Main) {
                    pendingImportResult = result
                    isImportingLoading = false
                }
            } catch (e: Exception) {
                launch(Dispatchers.Main) {
                    isImportingLoading = false
                    Toast.makeText(context, "خطا در پردازش متن: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    fun applyParsedInvoice(parsed: com.example.util.importer.ParsedInvoiceResult) {
        // Only apply invoice-specific transaction data (items, buyer, stone, date, invoice number)
        if (parsed.invoiceNo.isNotBlank()) invoiceNo = parsed.invoiceNo
        if (parsed.invoiceDate.isNotBlank()) invoiceDate = parsed.invoiceDate
        if (parsed.buyerName.isNotBlank()) buyerName = parsed.buyerName
        if (parsed.stoneCode.isNotBlank()) stoneCode = parsed.stoneCode
        if (parsed.stoneType.isNotBlank()) stoneType = parsed.stoneType

        // Strictly keep and reinforce the user's configured default store & seller settings
        viewModelScope.launch {
            val direct = repository.getConfigDirect()
            if (direct != null) {
                if (direct.defaultSellerName.isNotBlank()) sellerName = direct.defaultSellerName
                if (direct.defaultSellerPhone.isNotBlank()) sellerPhone = direct.defaultSellerPhone
                if (direct.defaultSellerAddress.isNotBlank()) sellerAddress = direct.defaultSellerAddress
                if (direct.defaultInvoiceTitle.isNotBlank()) invoiceTitle = direct.defaultInvoiceTitle
                if (direct.defaultInvoiceSubtitle.isNotBlank()) invoiceSubtitle = direct.defaultInvoiceSubtitle
                if (direct.defaultManagerSign.isNotBlank()) managerSign = direct.defaultManagerSign
                if (direct.defaultSalesSign.isNotBlank()) salesSign = direct.defaultSalesSign
                if (direct.defaultManagerSignImg.isNotBlank()) managerSignImgBase64 = direct.defaultManagerSignImg
                if (direct.defaultSalesSignImg.isNotBlank()) salesSignImgBase64 = direct.defaultSalesSignImg
            }
        }

        if (parsed.normalItems.isNotEmpty()) {
            normalItems.clear()
            normalItems.addAll(parsed.normalItems)
        }
        if (parsed.simpleItems.isNotEmpty()) {
            simpleItems.clear()
            simpleItems.addAll(parsed.simpleItems)
        }
        if (parsed.percentageItems.isNotEmpty()) {
            percentageItems.clear()
            percentageItems.addAll(parsed.percentageItems)
        }
        closeImportDialog()
        navigateTo("EDITOR")
        Toast.makeText(context, "فاکتور با موفقیت (${parsed.totalItemsCount} ردیف) بدون تغییر اطلاعات پیش‌فرض شما بارگذاری شد", Toast.LENGTH_LONG).show()
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

    /**
     * Saves a specific digital signature image directly as the app's default in settings/database.
     */
    fun saveSignatureAsDefault(isManager: Boolean, signatureBase64: String) {
        viewModelScope.launch {
            val currentConfig = repository.getConfigDirect() ?: ConfigEntity(deviceId = LicenseManager.getDeviceId(context))
            val updated = if (isManager) {
                currentConfig.copy(defaultManagerSignImg = signatureBase64)
            } else {
                currentConfig.copy(defaultSalesSignImg = signatureBase64)
            }
            repository.saveConfig(updated)
            val label = if (isManager) "امضای اول (مدیریت)" else "امضای دوم (فروش)"
            Toast.makeText(context, "$label به عنوان امضای پیش‌فرض در تنظیمات ذخیره شد", Toast.LENGTH_SHORT).show()
        }
    }

    /**
     * Updates ArvanCloud Object Storage (صندوقچه ابری) connection parameters.
     */
    fun updateArvanCloudSettings(
        endpoint: String,
        bucket: String,
        accessKey: String,
        secretKey: String,
        customDomain: String,
        autoSync: Boolean
    ) {
        viewModelScope.launch {
            val currentConfig = repository.getConfigDirect() ?: ConfigEntity(deviceId = LicenseManager.getDeviceId(context))
            val updated = currentConfig.copy(
                arvanEndpoint = endpoint.trim().ifBlank { "s3.ir-thr-at1.arvanstorage.ir" },
                arvanBucket = bucket.trim(),
                arvanAccessKey = accessKey.trim(),
                arvanSecretKey = secretKey.trim(),
                arvanCustomDomain = customDomain.trim(),
                arvanAutoSync = autoSync
            )
            repository.saveConfig(updated)
            arvanEndpoint = updated.arvanEndpoint
            arvanBucket = updated.arvanBucket
            arvanAccessKey = updated.arvanAccessKey
            arvanSecretKey = updated.arvanSecretKey
            arvanCustomDomain = updated.arvanCustomDomain
            arvanAutoSync = updated.arvanAutoSync
            Toast.makeText(context, "تنظیمات صندوقچه ابری آروان با موفقیت ذخیره گردید.", Toast.LENGTH_SHORT).show()
        }
    }

    /**
     * Tests online connection to ArvanCloud S3 bucket.
     */
    fun testArvanConnection() {
        if (arvanBucket.isBlank() || arvanAccessKey.isBlank() || arvanSecretKey.isBlank()) {
            arvanTestStatusMessage = "لطفاً نام صندوقچه (Bucket) و کلیدهای دسترسی (Access Key و Secret Key) را کامل کنید."
            isArvanTestSuccess = false
            return
        }
        viewModelScope.launch(Dispatchers.IO) {
            withContext(Dispatchers.Main) {
                isTestingArvanConnection = true
                arvanTestStatusMessage = "در حال اتصال به صندوقچه ابری آروان کلود..."
                isArvanTestSuccess = null
            }
            val client = ArvanCloudS3Client(
                endpoint = arvanEndpoint,
                bucket = arvanBucket,
                accessKey = arvanAccessKey,
                secretKey = arvanSecretKey,
                customDomain = arvanCustomDomain
            )
            val result = client.testConnection()
            withContext(Dispatchers.Main) {
                isTestingArvanConnection = false
                if (result.isSuccess) {
                    isArvanTestSuccess = true
                    arvanTestStatusMessage = "✅ " + result.getOrThrow()
                    Toast.makeText(context, "اتصال به صندوقچه ابری با موفقیت برقرار شد.", Toast.LENGTH_SHORT).show()
                } else {
                    isArvanTestSuccess = false
                    arvanTestStatusMessage = "❌ " + (result.exceptionOrNull()?.message ?: "خطا در اتصال")
                }
            }
        }
    }

    /**
     * Converts image to WebP, uploads to ArvanCloud in invoice dedicated customer folder, and updates active attachments.
     */
    fun uploadAttachmentToInvoice(context: Context, uri: Uri, title: String) {
        val currentBuyer = buyerName.trim()
        if (currentBuyer.isBlank()) {
            Toast.makeText(context, "لطفاً ابتدا نام خریدار را وارد کنید تا پوشه مخصوص به نام مشتری ایجاد گردد.", Toast.LENGTH_LONG).show()
            return
        }
        viewModelScope.launch(Dispatchers.IO) {
            withContext(Dispatchers.Main) {
                isUploadingAttachment = true
            }
            try {
                val webpBytes = InvoiceCloudSyncManager.convertImageToWebP(context, uri)
                if (webpBytes == null || webpBytes.isEmpty()) {
                    withContext(Dispatchers.Main) {
                        isUploadingAttachment = false
                        Toast.makeText(context, "فایل نامعتبر است یا فشرده‌سازی تصویر WebP با خطا مواجه شد.", Toast.LENGTH_LONG).show()
                    }
                    return@launch
                }

                val config = repository.getConfigDirect()
                val client = InvoiceCloudSyncManager.createClient(config)

                if (client != null && client.isConfigured) {
                    val uploadResult = InvoiceCloudSyncManager.uploadAttachment(
                        client = client,
                        buyerName = currentBuyer,
                        invoiceNo = invoiceNo,
                        title = title,
                        webpBytes = webpBytes,
                        existingCount = activeAttachments.size
                    )
                    if (uploadResult.isSuccess) {
                        val attachment = uploadResult.getOrThrow()
                        withContext(Dispatchers.Main) {
                            activeAttachments.add(attachment)
                            isUploadingAttachment = false
                            Toast.makeText(context, "تصویر به WebP تبدیل و در صندوقچه ابری بارگذاری شد.", Toast.LENGTH_SHORT).show()
                        }
                        autoSaveInvoiceSilently()
                    } else {
                        val err = uploadResult.exceptionOrNull()?.message ?: "خطا در بارگذاری ابری"
                        val fallback = InvoiceAttachment(
                            title = title.ifBlank { "ضمیمه ${activeAttachments.size + 1}" },
                            fileName = "attachment_${activeAttachments.size + 1}.webp",
                            cloudUrl = "",
                            localUri = uri.toString(),
                            uploadDate = JalaliCalendar.getTodayJalali(),
                            fileSizeKb = (webpBytes.size / 1024L).coerceAtLeast(1L)
                        )
                        withContext(Dispatchers.Main) {
                            activeAttachments.add(fallback)
                            isUploadingAttachment = false
                            Toast.makeText(context, "تصویر محلی اضافه شد اما بارگذاری ابری ناموفق بود: $err", Toast.LENGTH_LONG).show()
                        }
                        autoSaveInvoiceSilently()
                    }
                } else {
                    val localAtt = InvoiceAttachment(
                        title = title.ifBlank { "ضمیمه ${activeAttachments.size + 1}" },
                        fileName = "attachment_${activeAttachments.size + 1}.webp",
                        cloudUrl = "",
                        localUri = uri.toString(),
                        uploadDate = JalaliCalendar.getTodayJalali(),
                        fileSizeKb = (webpBytes.size / 1024L).coerceAtLeast(1L)
                    )
                    withContext(Dispatchers.Main) {
                        activeAttachments.add(localAtt)
                        isUploadingAttachment = false
                        Toast.makeText(context, "تصویر پیوست اضافه شد. (جهت بارگذاری در صندوقچه، تنظیمات را تکمیل فرمایید)", Toast.LENGTH_LONG).show()
                    }
                    autoSaveInvoiceSilently()
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    isUploadingAttachment = false
                    Toast.makeText(context, "خطا در افزودن پیوست: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    /**
     * Removes an attachment by index and optionally deletes it from ArvanCloud S3.
     */
    fun removeAttachmentFromInvoice(index: Int) {
        if (index in activeAttachments.indices) {
            val att = activeAttachments[index]
            activeAttachments.removeAt(index)
            if (att.fileName.isNotBlank()) {
                viewModelScope.launch(Dispatchers.IO) {
                    val config = repository.getConfigDirect()
                    val client = InvoiceCloudSyncManager.createClient(config)
                    if (client != null) {
                        val folder = InvoiceCloudSyncManager.getInvoiceFolderName(buyerName, invoiceNo)
                        client.deleteObject("$folder/${att.fileName}")
                    }
                }
            }
            autoSaveInvoiceSilently()
            Toast.makeText(context, "پیوست با موفقیت حذف گردید.", Toast.LENGTH_SHORT).show()
        }
    }

    /**
     * Fetches file and folder listing from ArvanCloud Object Storage.
     */
    fun loadCloudFiles(prefix: String = "invoices/") {
        viewModelScope.launch(Dispatchers.IO) {
            withContext(Dispatchers.Main) {
                isLoadingCloudFiles = true
                cloudFilesError = null
            }
            val config = repository.getConfigDirect()
            val client = if (config != null) ArvanCloudS3Client(
                endpoint = config.arvanEndpoint,
                bucket = config.arvanBucket,
                accessKey = config.arvanAccessKey,
                secretKey = config.arvanSecretKey,
                customDomain = config.arvanCustomDomain
            ) else null

            if (client == null || !client.isConfigured) {
                withContext(Dispatchers.Main) {
                    isLoadingCloudFiles = false
                    cloudFilesError = "تنظیمات صندوقچه ابری آروان کلود ناقص است. لطفاً ابتدا در بخش تنظیمات کلیدها را وارد فرمایید."
                }
                return@launch
            }

            val result = client.listObjects(prefix)
            withContext(Dispatchers.Main) {
                isLoadingCloudFiles = false
                if (result.isSuccess) {
                    cloudFiles.clear()
                    cloudFiles.addAll(result.getOrThrow())
                } else {
                    cloudFilesError = result.exceptionOrNull()?.message ?: "خطا در دریافت لیست فایل‌ها"
                }
            }
        }
    }

    /**
     * Deletes a file from ArvanCloud Object Storage.
     */
    fun deleteCloudFile(item: CloudFileItem) {
        viewModelScope.launch(Dispatchers.IO) {
            val config = repository.getConfigDirect()
            val client = if (config != null) ArvanCloudS3Client(
                endpoint = config.arvanEndpoint,
                bucket = config.arvanBucket,
                accessKey = config.arvanAccessKey,
                secretKey = config.arvanSecretKey,
                customDomain = config.arvanCustomDomain
            ) else null

            if (client == null || !client.isConfigured) return@launch

            val result = client.deleteObject(item.key)
            withContext(Dispatchers.Main) {
                if (result.isSuccess) {
                    cloudFiles.remove(item)
                    Toast.makeText(context, "فایل «${item.fileName}» از صندوقچه حذف گردید.", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(context, "خطا در حذف فایل: ${result.exceptionOrNull()?.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    /**
     * Uploads single invoice HTML directly to ArvanCloud.
     */
    fun uploadSingleInvoiceToCloud(invoice: InvoiceEntity) {
        viewModelScope.launch(Dispatchers.IO) {
            val config = repository.getConfigDirect()
            val client = if (config != null) ArvanCloudS3Client(
                endpoint = config.arvanEndpoint,
                bucket = config.arvanBucket,
                accessKey = config.arvanAccessKey,
                secretKey = config.arvanSecretKey,
                customDomain = config.arvanCustomDomain
            ) else null

            if (client == null || !client.isConfigured) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "لطفاً ابتدا تنظیمات صندوقچه آروان کلود را تکمیل فرمایید.", Toast.LENGTH_LONG).show()
                }
                return@launch
            }

            withContext(Dispatchers.Main) {
                Toast.makeText(context, "در حال تولید HTML و بارگذاری در صندوقچه...", Toast.LENGTH_SHORT).show()
            }

            val res = InvoiceCloudSyncManager.uploadInvoiceHtml(client, invoice)
            if (res.isSuccess) {
                val url = res.getOrThrow()
                val updated = invoice.copy(cloudHtmlUrl = url)
                repository.updateInvoice(updated)
                withContext(Dispatchers.Main) {
                    if (id == invoice.id) {
                        cloudHtmlUrl = url
                    }
                    Toast.makeText(context, "✅ فاکتور در صندوقچه ثبت شد و لینک آنلاین متصل گردید.", Toast.LENGTH_LONG).show()
                }
            } else {
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "خطا در بارگذاری ابری: ${res.exceptionOrNull()?.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    /**
     * Sequentially syncs all invoices in database to ArvanCloud queue with progress and deduplication.
     */
    fun syncAllInvoicesToCloud(forceReupload: Boolean = false) {
        viewModelScope.launch(Dispatchers.IO) {
            val config = repository.getConfigDirect()
            val client = if (config != null) ArvanCloudS3Client(
                endpoint = config.arvanEndpoint,
                bucket = config.arvanBucket,
                accessKey = config.arvanAccessKey,
                secretKey = config.arvanSecretKey,
                customDomain = config.arvanCustomDomain
            ) else null

            if (client == null || !client.isConfigured) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "ابتدا تنظیمات صندوقچه آروان کلود را ذخیره فرمایید.", Toast.LENGTH_LONG).show()
                }
                return@launch
            }

            val allInvoices = repository.getAllInvoicesDirect().filter { !it.isDeleted }
            if (allInvoices.isEmpty()) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "هیچ فاکتور فعالی برای همگام‌سازی وجود ندارد.", Toast.LENGTH_SHORT).show()
                }
                return@launch
            }

            withContext(Dispatchers.Main) {
                isBatchSyncActive = true
                batchSyncCurrent = 0
                batchSyncTotal = allInvoices.size
                batchSyncStatusText = "در حال آماده‌سازی صف بارگذاری..."
                batchSyncSuccessCount = 0
                batchSyncSkippedCount = 0
                batchSyncFailedCount = 0
            }

            val result = InvoiceCloudSyncManager.syncBatchQueue(
                client = client,
                invoices = allInvoices,
                forceReupload = forceReupload
            ) { current, total, name, msg ->
                viewModelScope.launch(Dispatchers.Main) {
                    batchSyncCurrent = current
                    batchSyncTotal = total
                    batchSyncCurrentInvoiceName = name
                    batchSyncStatusText = msg
                }
            }

            for (updatedInv in result.updatedInvoices) {
                repository.updateInvoice(updatedInv)
            }

            withContext(Dispatchers.Main) {
                isBatchSyncActive = false
                batchSyncSuccessCount = result.newlyUploaded
                batchSyncSkippedCount = result.skippedAlreadyUploaded
                batchSyncFailedCount = result.failedCount
                val summary = "همگام‌سازی پایان یافت:\n• ${result.newlyUploaded} فاکتور جدید بارگذاری شد.\n• ${result.skippedAlreadyUploaded} فاکتور قبلاً موجود بود (بدون داپلیکیت).\n• ${result.failedCount} خطا."
                Toast.makeText(context, summary, Toast.LENGTH_LONG).show()
            }
        }
    }

    fun dismissBatchSyncDialog() {
        isBatchSyncActive = false
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
            val attJson = ItemJsonConverter.serializeAttachments(activeAttachments)

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
                salesSignImgBase64 = salesSignImgBase64,
                cloudHtmlUrl = cloudHtmlUrl,
                attachmentsJson = attJson
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

            // Auto-upload HTML and sync to ArvanCloud Object Storage in background
            val config = repository.getConfigDirect()
            val client = InvoiceCloudSyncManager.createClient(config)
            if (client != null && client.isConfigured) {
                launch(Dispatchers.IO) {
                    val uploadResult = InvoiceCloudSyncManager.uploadInvoiceHtml(client, backupEntity)
                    if (uploadResult.isSuccess) {
                        val url = uploadResult.getOrThrow()
                        val updated = backupEntity.copy(cloudHtmlUrl = url)
                        repository.updateInvoice(updated)
                        withContext(Dispatchers.Main) {
                            cloudHtmlUrl = url
                            Toast.makeText(context, "✅ نسخه HTML فاکتور در صندوقچه ابری آروان ثبت و لینک متصل شد.", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            }
        }
    }

    // Auto-saves the invoice silently (no user alerts)
    fun autoSaveInvoiceSilently() {
        val nameToSave = buyerName.trim().ifBlank { "پیش‌نویس خودکار" }
        viewModelScope.launch {
            val itemsJson = ItemJsonConverter.serializeInvoiceItems(normalItems)
            val simpleItemsJson = ItemJsonConverter.serializeSimpleItems(simpleItems)
            val pctJson = ItemJsonConverter.serializePercentageItems(percentageItems)
            val attJson = ItemJsonConverter.serializeAttachments(activeAttachments)

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
                salesSignImgBase64 = salesSignImgBase64,
                cloudHtmlUrl = cloudHtmlUrl,
                attachmentsJson = attJson
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
        cloudHtmlUrl = invoice.cloudHtmlUrl
        invoiceTitle = invoice.invoiceTitle.ifBlank { "فاکتور فروش صنایع سنگ ایران کوارتز" }
        invoiceSubtitle = invoice.invoiceSubtitle.ifBlank { "مجری فروش اسلب کوارتز ساخت و نصب کانترتاپ کوارتز کاینداستون توتم گریفین" }

        val loadedNormals = ItemJsonConverter.deserializeInvoiceItems(invoice.itemsJson)
        val loadedSimples = ItemJsonConverter.deserializeSimpleItems(invoice.simpleItemsJson)
        val loadedPercentages = ItemJsonConverter.deserializePercentageItems(invoice.percentageItemsJson)
        val loadedAttachments = ItemJsonConverter.deserializeAttachments(invoice.attachmentsJson)

        normalItems.clear()
        normalItems.addAll(loadedNormals)

        simpleItems.clear()
        simpleItems.addAll(loadedSimples)

        percentageItems.clear()
        percentageItems.addAll(loadedPercentages)

        activeAttachments.clear()
        activeAttachments.addAll(loadedAttachments)

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

    fun startShareIntent(context: Context, file: java.io.File, customTitle: String = "اشتراک‌گذاری فایل") {
        try {
            val authority = "${context.packageName}.fileprovider"
            val uri = androidx.core.content.FileProvider.getUriForFile(context, authority, file)
            val mimeType = when {
                file.name.endsWith(".pdf") -> "application/pdf"
                file.name.endsWith(".qzb") || file.name.endsWith(".json") -> "application/octet-stream"
                else -> "*/*"
            }
            val intent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                type = mimeType
                putExtra(android.content.Intent.EXTRA_STREAM, uri)
                addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(android.content.Intent.createChooser(intent, customTitle))
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

    fun refreshAvailableBackups() {
        viewModelScope.launch(Dispatchers.IO) {
            val list = InvoiceBackupManager.listAvailableBackups()
            _availableBackups.value = list
        }
    }

    /**
     * Creates a full offline backup package of all invoices, configuration, and registered coworker licenses,
     * saves it in the device public storage, and opens the Android share sheet.
     */
    fun createAndShareFullBackup(context: Context) {
        viewModelScope.launch(Dispatchers.IO) {
            isBackupOperationLoading = true
            try {
                val invoices = repository.getAllInvoicesDirect()
                val config = repository.getConfigDirect()
                val devices = repository.getAllDevicesDirect()

                val backupFile = InvoiceBackupManager.saveFullBackupToFile(invoices, config, devices)
                refreshAvailableBackups()

                launch(Dispatchers.Main) {
                    isBackupOperationLoading = false
                    if (backupFile != null && backupFile.exists()) {
                        backupOperationMessage = "بکاپ کامل از ${invoices.size} فاکتور و اطلاعات فروشگاه با موفقیت ایجاد گردید.\n\nمسیر ذخیره:\n${backupFile.absolutePath}"
                        Toast.makeText(context, "بکاپ کامل (${invoices.size} فاکتور) ایجاد شد", Toast.LENGTH_SHORT).show()
                        startShareIntent(context, backupFile)
                    } else {
                        Toast.makeText(context, "خطا در ایجاد فایل بکاپ", Toast.LENGTH_LONG).show()
                    }
                }
            } catch (e: Exception) {
                launch(Dispatchers.Main) {
                    isBackupOperationLoading = false
                    Toast.makeText(context, "خطا در تولید فایل پشتیبان: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    /**
     * Restores full backup from a user-selected SAF Uri (file picker).
     */
    fun restoreBackupFromUri(context: Context, uri: android.net.Uri) {
        viewModelScope.launch(Dispatchers.IO) {
            isBackupOperationLoading = true
            try {
                val jsonContent = InvoiceBackupManager.readFromUri(context, uri)
                if (jsonContent.isNullOrBlank()) {
                    launch(Dispatchers.Main) {
                        isBackupOperationLoading = false
                        Toast.makeText(context, "خطا در خواندن فایل بکاپ انتخاب‌شده", Toast.LENGTH_LONG).show()
                    }
                    return@launch
                }

                val result = InvoiceBackupManager.parseBackupContent(jsonContent)
                applyRestoreResult(context, result)
            } catch (e: Exception) {
                launch(Dispatchers.Main) {
                    isBackupOperationLoading = false
                    Toast.makeText(context, "خطا در بازگردانی بکاپ: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    /**
     * Restores full backup directly from a local File.
     */
    fun restoreBackupFromFile(context: Context, file: java.io.File) {
        viewModelScope.launch(Dispatchers.IO) {
            isBackupOperationLoading = true
            try {
                val jsonContent = InvoiceBackupManager.readFromFile(file)
                if (jsonContent.isNullOrBlank()) {
                    launch(Dispatchers.Main) {
                        isBackupOperationLoading = false
                        Toast.makeText(context, "خطا در خواندن فایل بکاپ", Toast.LENGTH_LONG).show()
                    }
                    return@launch
                }

                val result = InvoiceBackupManager.parseBackupContent(jsonContent)
                applyRestoreResult(context, result)
            } catch (e: Exception) {
                launch(Dispatchers.Main) {
                    isBackupOperationLoading = false
                    Toast.makeText(context, "خطا در بازگردانی فایل: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private suspend fun applyRestoreResult(context: Context, result: com.example.util.BackupRestoreResult) {
        if (!result.success) {
            withContext(Dispatchers.Main) {
                isBackupOperationLoading = false
                Toast.makeText(context, result.errorMessage ?: "فایل بکاپ نامعتبر است", Toast.LENGTH_LONG).show()
            }
            return
        }

        var importedInvoicesCount = 0
        var duplicatesCount = 0
        val existingList = repository.getAllInvoicesDirect().toMutableList()
        for (inv in result.invoices) {
            val exactTotal = HtmlInvoiceGenerator.calculateInvoiceTotal(inv)
            val normalizedInv = if (exactTotal > 0.0) inv.copy(totalAmount = exactTotal) else inv
            if (isDuplicateInvoice(normalizedInv, existingList)) {
                duplicatesCount++
                continue
            }
            val cleanInv = normalizedInv.copy(id = 0, isDeleted = false)
            val newId = repository.insertInvoice(cleanInv)
            existingList.add(cleanInv.copy(id = newId.toInt()))
            importedInvoicesCount++
        }

        if (result.config != null) {
            val currentConfig = repository.getConfigDirect() ?: ConfigEntity(deviceId = LicenseManager.getDeviceId(context))
            val mergedConfig = currentConfig.copy(
                defaultSellerName = result.config.defaultSellerName,
                defaultSellerPhone = result.config.defaultSellerPhone,
                defaultSellerAddress = result.config.defaultSellerAddress,
                defaultInvoiceTitle = result.config.defaultInvoiceTitle,
                defaultInvoiceSubtitle = result.config.defaultInvoiceSubtitle,
                defaultManagerSign = result.config.defaultManagerSign,
                defaultSalesSign = result.config.defaultSalesSign,
                defaultManagerSignImg = result.config.defaultManagerSignImg,
                defaultSalesSignImg = result.config.defaultSalesSignImg
            )
            repository.saveConfig(mergedConfig)
            withContext(Dispatchers.Main) {
                sellerName = mergedConfig.defaultSellerName
                sellerPhone = mergedConfig.defaultSellerPhone
                sellerAddress = mergedConfig.defaultSellerAddress
                invoiceTitle = mergedConfig.defaultInvoiceTitle
                invoiceSubtitle = mergedConfig.defaultInvoiceSubtitle
                managerSign = mergedConfig.defaultManagerSign
                salesSign = mergedConfig.defaultSalesSign
                managerSignImgBase64 = mergedConfig.defaultManagerSignImg
                salesSignImgBase64 = mergedConfig.defaultSalesSignImg
            }
        }

        for (dev in result.devices) {
            repository.saveDevice(dev)
        }
        if (result.devices.isNotEmpty()) {
            val updatedDevices = repository.getAllDevicesDirect()
            InvoiceBackupManager.saveLicensesBackup(updatedDevices)
        }

        refreshAvailableBackups()

        withContext(Dispatchers.Main) {
            isBackupOperationLoading = false
            val configMsg = if (result.configRestored) " و مشخصات و امضاهای فروشگاه" else ""
            val licenseMsg = if (result.devicesCount > 0) " و ${result.devicesCount} لایسنس همکاران" else ""
            val dupMsg = if (duplicatesCount > 0) "\n($duplicatesCount فاکتور تکراری شناسایی و رد شد)" else ""
            backupOperationMessage = "بازیابی با موفقیت انجام شد:\n• $importedInvoicesCount فاکتور جدید بازیابی گردید.$dupMsg$configMsg$licenseMsg"
            Toast.makeText(context, "✅ بازیابی موفقیت‌آمیز بود ($importedInvoicesCount فاکتور)", Toast.LENGTH_LONG).show()
        }
    }

    /**
     * Imports one or multiple user-selected .qzb files, verifies duplicates, and adds only new invoices.
     * Prevents making duplicate file copies in the backup directory.
     */
    fun importQzbFiles(context: Context, uris: List<android.net.Uri>) {
        if (uris.isEmpty()) return
        viewModelScope.launch(Dispatchers.IO) {
            isBackupOperationLoading = true
            try {
                val existingInvoices = repository.getAllInvoicesDirect().toMutableList()
                var addedCount = 0
                var duplicateCount = 0

                for (uri in uris) {
                    val json = InvoiceBackupManager.readFromUri(context, uri) ?: continue
                    val parseResult = InvoiceBackupManager.parseBackupContent(json)
                    if (parseResult.success && parseResult.invoices.isNotEmpty()) {
                        for (inv in parseResult.invoices) {
                            val exactTotal = HtmlInvoiceGenerator.calculateInvoiceTotal(inv)
                            val normalizedInv = if (exactTotal > 0.0) inv.copy(totalAmount = exactTotal) else inv
                            if (isDuplicateInvoice(normalizedInv, existingInvoices)) {
                                duplicateCount++
                            } else {
                                val cleanInv = normalizedInv.copy(id = 0, isDeleted = false)
                                val newId = repository.insertInvoice(cleanInv)
                                existingInvoices.add(cleanInv.copy(id = newId.toInt()))
                                addedCount++
                            }
                        }
                    }
                }

                refreshAvailableBackups()

                withContext(Dispatchers.Main) {
                    isBackupOperationLoading = false
                    val msg = when {
                        addedCount > 0 && duplicateCount > 0 ->
                            "✅ $addedCount فاکتور جدید با موفقیت اضافه شد.\n($duplicateCount فاکتور تکراری شناسایی و رد شد)"
                        addedCount > 0 ->
                            "✅ $addedCount فاکتور با موفقیت به برنامه اضافه شد."
                        duplicateCount > 0 ->
                            "تمامی فاکتورهای انتخابی ($duplicateCount عدد) تکراری بودند و قبلاً در برنامه ثبت شده‌اند."
                        else ->
                            "فایل‌های انتخابی معتبر نبودند یا فاکتوری در آن‌ها یافت نشد."
                    }
                    backupOperationMessage = msg
                    Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    isBackupOperationLoading = false
                    Toast.makeText(context, "خطا در افزودن فاکتورها: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    /**
     * Imports a single .qzb invoice backup file (wrapper for importQzbFiles).
     */
    fun importSingleQzbFile(context: Context, uri: android.net.Uri) {
        importQzbFiles(context, listOf(uri))
    }

    /**
     * Reads and restores multiple user-selected .qzb files (wrapper for importQzbFiles).
     */
    fun restoreFromMultipleUris(context: Context, uris: List<android.net.Uri>) {
        importQzbFiles(context, uris)
    }

    fun deleteBackupFile(file: java.io.File) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                if (file.exists()) {
                    file.delete()
                }
                refreshAvailableBackups()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    /**
     * Reads and restores all .qzb invoices from a user-selected folder with duplicate checking.
     */
    fun restoreFromFolderTree(context: Context, treeUri: android.net.Uri) {
        viewModelScope.launch(Dispatchers.IO) {
            isBackupOperationLoading = true
            try {
                try {
                    val flags = android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION
                    context.contentResolver.takePersistableUriPermission(treeUri, flags)
                } catch (e: Exception) {
                    // Ignore persistable flag errors
                }

                val foundInvoices = InvoiceBackupManager.restoreFromTreeUri(context, treeUri)
                val existingInvoices = repository.getAllInvoicesDirect().toMutableList()
                var importedCount = 0
                var duplicatesCount = 0

                for (inv in foundInvoices) {
                    if (isDuplicateInvoice(inv, existingInvoices)) {
                        duplicatesCount++
                    } else {
                        val cleanInv = inv.copy(id = 0, isDeleted = false)
                        val newId = repository.insertInvoice(cleanInv)
                        existingInvoices.add(cleanInv.copy(id = newId.toInt()))
                        importedCount++
                    }
                }
                refreshAvailableBackups()

                withContext(Dispatchers.Main) {
                    isBackupOperationLoading = false
                    val dupMsg = if (duplicatesCount > 0) "\n($duplicatesCount فاکتور تکراری رد شد)" else ""
                    if (importedCount > 0) {
                        backupOperationMessage = "✅ بازیابی پوشه با موفقیت انجام شد:\n$importedCount فاکتور جدید به لیست اضافه شد.$dupMsg"
                        Toast.makeText(context, "$importedCount فاکتور با موفقیت اضافه شد", Toast.LENGTH_LONG).show()
                    } else if (duplicatesCount > 0) {
                        backupOperationMessage = "تمام $duplicatesCount فاکتور یافت شده در این پوشه، از قبل در برنامه وجود دارند و تکراری بودند."
                        Toast.makeText(context, "فاکتور جدیدی یافت نشد (تمام موارد تکراری بودند)", Toast.LENGTH_LONG).show()
                    } else {
                        backupOperationMessage = "هیچ فایل فاکتور با پسوند .qzb در این پوشه یافت نشد."
                        Toast.makeText(context, "فایل فاکتوری در این پوشه یافت نشد", Toast.LENGTH_LONG).show()
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    isBackupOperationLoading = false
                    Toast.makeText(context, "خطا در خواندن پوشه: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    fun triggerSyncAndLoadBackups(context: Context) {
        viewModelScope.launch(Dispatchers.IO) {
            isBackupOperationLoading = true
            try {
                var importedCount = 0
                val backups = InvoiceBackupManager.loadAllBackups()
                for (backup in backups) {
                    val existing = repository.getInvoiceById(backup.id)
                    if (existing == null) {
                        repository.insertInvoice(backup)
                        importedCount++
                    } else {
                        // Ensure it's not marked deleted if active in backup
                        if (!backup.isDeleted && existing.isDeleted) {
                            repository.setDeletedStatus(backup.id, false)
                        }
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

                refreshAvailableBackups()

                withContext(Dispatchers.Main) {
                    isBackupOperationLoading = false
                    if (importedCount > 0) {
                        backupOperationMessage = "✅ $importedCount فاکتور از پوشه پشتیبان Documents/IranQuartz/Backup faktors با موفقیت شناسایی و بازیابی شد."
                        Toast.makeText(context, "$importedCount فاکتور با موفقیت از حافظه بازگردانی شد.", Toast.LENGTH_LONG).show()
                    } else if (backups.isNotEmpty()) {
                        Toast.makeText(context, "تمامی فاکتورهای حافظه (${backups.size} فاکتور) در دیتابیس حاضر و همگام هستند.", Toast.LENGTH_SHORT).show()
                    } else {
                        backupOperationMessage = "فایلی در مسیرهای پیش‌فرض پیدا نشد. می‌توانید با دکمه «انتخاب پوشه Backup faktors»، پوشه را انتخاب کنید تا تمام فایل‌ها خوانده شوند."
                        Toast.makeText(context, "فایلی در پوشه پیش‌فرض یافت نشد. دکمه انتخاب پوشه را بزنید.", Toast.LENGTH_LONG).show()
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    isBackupOperationLoading = false
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
