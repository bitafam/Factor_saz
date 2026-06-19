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
import com.example.data.model.ComposeInvoiceItem
import com.example.data.model.ComposeSimpleItem
import com.example.data.repository.InvoiceRepository
import com.example.util.HtmlInvoiceGenerator
import com.example.util.LicenseManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
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
    var sellerName by mutableStateOf("کایند استون")
    var sellerPhone by mutableStateOf("09140341941")
    var sellerAddress by mutableStateOf("استان اصفهان - شهرک صنعتی محمود آباد - خیابان ۴۴")
    var stoneCode by mutableStateOf("")
    var stoneType by mutableStateOf("")
    var managerSign by mutableStateOf("مدیر فروش")
    var salesSign by mutableStateOf("مسئول فروش")

    // Active lists of items
    val normalItems = mutableStateListOf<ComposeInvoiceItem>()
    val simpleItems = mutableStateListOf<ComposeSimpleItem>()

    // Calculated Grand Total
    val grandTotal: Double
        get() {
            val normalSum = normalItems.sumOf { it.totalAmount }
            val simpleSum = simpleItems.sumOf { it.totalAmount }
            return normalSum + simpleSum
        }

    // Input States for Activation Key
    var licenseInputKey by mutableStateOf("")
    var licensePriceInput by mutableStateOf("")

    // Admin generator state
    var adminTargetDeviceId by mutableStateOf("")
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
        val sdf = SimpleDateFormat("yyyy/MM/dd", Locale.getDefault())
        invoiceDate = sdf.format(Date())
    }

    // Navigate Utility
    fun navigateTo(screen: String) {
        viewModelScope.launch {
            val config = repository.getConfigDirect()
            val isLicensed = config?.isLicensed ?: false
            
            if (!isLicensed && screen != "ACTIVATION_LOCK") {
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
        normalItems.clear()
        simpleItems.clear()
        addNormalItemRow()
        setTodayDate()
        Toast.makeText(context, "فاکتور جدید ایجاد شد", Toast.LENGTH_SHORT).show()
    }

    // Add list items
    fun addNormalItemRow() {
        normalItems.add(ComposeInvoiceItem())
    }

    fun addSimpleItemRow(desc: String, amtStr: String) {
        simpleItems.add(ComposeSimpleItem(description = desc, totalAmountStr = amtStr))
    }

    // Update item lists at a index
    fun updateNormalItem(index: Int, updated: ComposeInvoiceItem) {
        if (index in normalItems.indices) {
            normalItems[index] = updated
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

    fun removeLastRow() {
        if (simpleItems.isNotEmpty()) {
            simpleItems.removeAt(simpleItems.size - 1)
        } else if (normalItems.isNotEmpty()) {
            normalItems.removeAt(normalItems.size - 1)
        }
    }

    // Check License Key Inputed or Special Admin Command
    fun attemptActivation() {
        val trimmed = licenseInputKey.trim()
        if (trimmed == LicenseManager.MASTER_ADMIN_PASSCODE) {
            // Master Admin Key entered - directly go into Admin Panel!
            navigateTo("ADMIN_PANEL")
            Toast.makeText(context, "پنل مدیریت همکار باز شد", Toast.LENGTH_SHORT).show()
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
            val superKey = LicenseManager.generateLicenseKey(currentConfig.deviceId)
            repository.saveConfig(currentConfig.copy(isLicensed = true, licenseKey = superKey))
            Toast.makeText(context, "برنامه به طور مستقیم فعال شد", Toast.LENGTH_SHORT).show()
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

    // Compute license keys inside Admin panel for other devices
    fun adminGenerateKeyForDevice() {
        if (adminTargetDeviceId.isNotBlank()) {
            adminCalculatedKey = LicenseManager.generateLicenseKey(adminTargetDeviceId)
        } else {
            Toast.makeText(context, "شناسه دستگاه را وارد کنید", Toast.LENGTH_SHORT).show()
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
                totalAmount = grandTotal
            )

            val savedId = repository.insertInvoice(entity)
            if (id == 0) {
                id = savedId.toInt()
            }
            Toast.makeText(context, "فاکتور ذخیره گردید", Toast.LENGTH_SHORT).show()
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

        val loadedNormals = ItemJsonConverter.deserializeInvoiceItems(invoice.itemsJson)
        val loadedSimples = ItemJsonConverter.deserializeSimpleItems(invoice.simpleItemsJson)

        normalItems.clear()
        normalItems.addAll(loadedNormals)

        simpleItems.clear()
        simpleItems.addAll(loadedSimples)

        if (normalItems.isEmpty()) {
            addNormalItemRow()
        }

        navigateTo("EDITOR")
        Toast.makeText(context, "فاکتور بارگذاری شد", Toast.LENGTH_SHORT).show()
    }

    // Delete saved invoice
    fun deleteInvoice(invoice: InvoiceEntity) {
        viewModelScope.launch {
            repository.deleteInvoice(invoice)
            Toast.makeText(context, "فاکتور حذف شد", Toast.LENGTH_SHORT).show()
        }
    }

    // Render HTML and execute Print action
    fun printCurrentInvoice() {
        val itemsJson = ItemJsonConverter.serializeInvoiceItems(normalItems)
        val simpleItemsJson = ItemJsonConverter.serializeSimpleItems(simpleItems)

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
            totalAmount = grandTotal
        )

        val html = HtmlInvoiceGenerator.generateHtml(entity)
        executeWebViewPrint(html, "Quartz_Invoice_${invoiceNo}")
    }

    private fun executeWebViewPrint(html: String, jobName: String) {
        try {
            val mainLooper = android.os.Looper.getMainLooper()
            val runnable = Runnable {
                val webView = android.webkit.WebView(context)
                webView.webViewClient = object : android.webkit.WebViewClient() {
                    override fun onPageFinished(view: android.webkit.WebView?, url: String?) {
                        val printManager = context.getSystemService(Context.PRINT_SERVICE) as android.print.PrintManager
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
            Toast.makeText(context, "خطا در پرینت: ${e.message}", Toast.LENGTH_LONG).show()
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
