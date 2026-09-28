@file:OptIn(ExperimentalMaterial3Api::class)
package com.example.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import android.net.Uri
import com.example.util.JalaliCalendar
import com.example.data.database.InvoiceEntity
import com.example.data.model.*
import com.example.ui.viewmodel.InvoiceViewModel
import java.text.DecimalFormat

private val formatter = DecimalFormat("#,###")

fun formatMoney(value: Any): String {
    return try {
        val num = when (value) {
            is String -> com.example.util.importer.TextNormalizer.parseNumber(value) ?: 0.0
            is Number -> value.toDouble()
            else -> 0.0
        }
        formatter.format(num)
    } catch (e: Exception) {
        value.toString()
    }
}

fun formatInputWithCommas(input: String): String {
    val englishDigits = input
        .replace('۰', '0')
        .replace('۱', '1')
        .replace('۲', '2')
        .replace('۳', '3')
        .replace('۴', '4')
        .replace('۵', '5')
        .replace('۶', '6')
        .replace('۷', '7')
        .replace('۸', '8')
        .replace('۹', '9')
        .replace(",", "")
        .replace(" ", "")
    if (englishDigits.isBlank()) return ""
    val parsed = englishDigits.toLongOrNull() ?: return input
    return formatter.format(parsed)
}

@Composable
fun AppNavigationRoot(viewModel: InvoiceViewModel) {
    val context = LocalContext.current
    val configState by viewModel.appConfig.collectAsStateWithLifecycle()

    // Determine current screen taking licensing into account
    val isLicensed = configState?.isLicensed ?: false
    val activeScreen = if (!isLicensed && viewModel.currentScreen != "ADMIN_PANEL") {
        "ACTIVATION_LOCK"
    } else {
        viewModel.currentScreen
    }

    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
        Scaffold(
            bottomBar = {
                if (isLicensed && activeScreen != "ADMIN_PANEL" && activeScreen != "ACTIVATION_LOCK") {
                    NavigationBar(
                        containerColor = MaterialTheme.colorScheme.surface,
                        tonalElevation = 8.dp
                    ) {
                        NavigationBarItem(
                            selected = activeScreen == "HISTORY",
                            onClick = { viewModel.navigateTo("HISTORY") },
                            icon = { Icon(Icons.Default.List, contentDescription = "آرشیو فاکتورها") },
                            label = { Text("فاکتورهای ذخیره شده", fontSize = 11.sp, fontWeight = FontWeight.Bold) },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = MaterialTheme.colorScheme.primary,
                                selectedTextColor = MaterialTheme.colorScheme.primary,
                                unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        )
                        NavigationBarItem(
                            selected = activeScreen == "EDITOR",
                            onClick = { viewModel.navigateTo("EDITOR") },
                            icon = { Icon(Icons.Default.AddCircle, contentDescription = "ساخت فاکتور") },
                            label = { Text("ساخت فاکتور", fontSize = 11.sp, fontWeight = FontWeight.Bold) },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = MaterialTheme.colorScheme.primary,
                                selectedTextColor = MaterialTheme.colorScheme.primary,
                                unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        )
                        NavigationBarItem(
                            selected = activeScreen == "ACCOUNT",
                            onClick = { viewModel.navigateTo("ACCOUNT") },
                            icon = { Icon(Icons.Default.AccountCircle, contentDescription = "حساب کاربری") },
                            label = { Text("حساب کاربری", fontSize = 11.sp, fontWeight = FontWeight.Bold) },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = MaterialTheme.colorScheme.primary,
                                selectedTextColor = MaterialTheme.colorScheme.primary,
                                unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        )
                    }
                }
            }
        ) { innerPadding ->
            Surface(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                color = MaterialTheme.colorScheme.background
            ) {
                AnimatedContent(
                    targetState = activeScreen,
                    transitionSpec = {
                        fadeIn() togetherWith fadeOut()
                    },
                    label = "ScreenTransition"
                ) { target ->
                    when (target) {
                        "ACTIVATION_LOCK" -> ActivationLockScreen(viewModel)
                        "ADMIN_PANEL" -> AdminPanelScreen(viewModel)
                        "EDITOR" -> EditorScreen(viewModel)
                        "HISTORY" -> HistoryScreen(viewModel)
                        "ACCOUNT" -> AccountScreen(viewModel)
                        "SETTINGS" -> SettingsScreen(viewModel)
                        else -> EditorScreen(viewModel)
                    }
                }
            }
            // Persistent Import Dialog
            InvoiceImportDialog(viewModel = viewModel)
        }
    }
}

/**
 * Modern License Activation Page.
 * Blocks usage until license key verified. Seller details master override accessible.
 */
@Composable
fun ActivationLockScreen(viewModel: InvoiceViewModel) {
    val context = LocalContext.current
    val configState by viewModel.appConfig.collectAsStateWithLifecycle()
    val deviceId = configState?.deviceId ?: ""
    val licensePrice = configState?.licensePrice ?: "۵,۰۰۰,۰۰۰ تومان"

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        MaterialTheme.colorScheme.background,
                        MaterialTheme.colorScheme.surface
                    )
                )
            )
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(20.dp))
                .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.95f))
                .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(20.dp))
                .padding(24.dp)
        ) {
            // Icon
            Box(
                modifier = Modifier
                    .size(64.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Lock,
                    contentDescription = "قفل",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(32.dp)
                )
            }

            Text(
                text = "فعال‌سازی نرم‌افزار فاکتور کوارتز",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center
            )

            Text(
                text = "جهت استفاده از تمامی امکانات و ثبت فاکتورهای کوارتز، لایسنس فعالساز را تهیه فرمایید.",
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                lineHeight = 20.sp,
                modifier = Modifier.padding(horizontal = 8.dp)
            )

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

            // Display Price
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = "هزینه فعال‌سازی یکباره برنامه:",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = licensePrice,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary, // Visual Accent Gold
                    modifier = Modifier.padding(top = 4.dp)
                )
            }

            // Device Identification Key Display
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .clickable {
                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            val clip = ClipData.newPlainText("Device ID", deviceId)
                            clipboard.setPrimaryClip(clip)
                            Toast.makeText(context, "کد شناسایی کپی شد", Toast.LENGTH_SHORT).show()
                        }
                        .padding(12.dp)
                        .fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "شناسه دستگاه شما:",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = deviceId,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text("کپی", fontSize = 11.sp, color = MaterialTheme.colorScheme.primary)
                        Icon(
                            imageVector = Icons.Default.Share,
                            contentDescription = "کپی",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }

            // Active Input
            OutlinedTextField(
                value = viewModel.licenseInputKey,
                onValueChange = { viewModel.licenseInputKey = it },
                label = { Text("کد فعال‌سازی لایسنس") },
                placeholder = { Text("XXXX-XXXX-XX") },
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    focusedLabelColor = MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor = MaterialTheme.colorScheme.outline
                ),
                leadingIcon = {
                    Icon(imageVector = Icons.Default.Lock, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("activation_key_input")
            )

            Button(
                onClick = { viewModel.attemptActivation() },
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                ),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .testTag("activate_app_button")
            ) {
                Icon(imageVector = Icons.Default.Check, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("فعال‌سازی و ورود", fontSize = 14.sp, fontWeight = FontWeight.Bold)
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Clickable Customer Support Box (Phone & Email)
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "پشتیبانی و مشاوره فروش لایسنس:",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Phone,
                                contentDescription = "تلفن",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "۰۹۱۰۲۳۷۸۰۵۵",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }

                        Button(
                            onClick = {
                                try {
                                    val intent = android.content.Intent(
                                        android.content.Intent.ACTION_DIAL,
                                        android.net.Uri.parse("tel:09102378055")
                                    )
                                    context.startActivity(intent)
                                } catch (e: Exception) {
                                    android.widget.Toast.makeText(context, "خطا در تماس", android.widget.Toast.LENGTH_SHORT).show()
                                }
                            },
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                            modifier = Modifier.height(32.dp),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Icon(Icons.Default.Phone, contentDescription = null, modifier = Modifier.size(12.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("تماس با ما", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                        }
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Email,
                                contentDescription = "ایمیل",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "salarybusines@gmail.com",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }

                        Button(
                            onClick = {
                                try {
                                    val intent = android.content.Intent(
                                        android.content.Intent.ACTION_SENDTO,
                                        android.net.Uri.parse("mailto:salarybusines@gmail.com")
                                    )
                                    context.startActivity(intent)
                                } catch (e: Exception) {
                                    android.widget.Toast.makeText(context, "خطا در ارسال ایمیل", android.widget.Toast.LENGTH_SHORT).show()
                                }
                            },
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                            modifier = Modifier.height(32.dp),
                            shape = RoundedCornerShape(8.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.secondary,
                                contentColor = MaterialTheme.colorScheme.onSecondary
                            )
                        ) {
                            Icon(Icons.Default.Send, contentDescription = null, modifier = Modifier.size(12.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("ارسال ایمیل", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Written Developer Credit Logo (tap to open Admin Panel)
            Text(
                text = "توسعه دهنده امیرحسین سالاری (پنل مدیریت)",
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .padding(top = 4.dp)
                    .clickable {
                        viewModel.navigateTo("ADMIN_PANEL")
                    }
                    .padding(horizontal = 8.dp, vertical = 4.dp)
            )
        }
    }
}

/**
 * Developer Creator Panel to Calculate Licenses, Override Activation Status, and Manage Configs.
 */
@Composable
fun AdminPanelScreen(viewModel: InvoiceViewModel) {
    val context = LocalContext.current
    val configState by viewModel.appConfig.collectAsStateWithLifecycle()
    val thisDeviceId = configState?.deviceId ?: ""

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("پنل همکار فاکتور ساز سنگ کوارتز", fontSize = 16.sp, fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = { viewModel.navigateTo("EDITOR") }) {
                        Icon(imageVector = Icons.Default.Close, contentDescription = "بازگشت")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    titleContentColor = MaterialTheme.colorScheme.onSurface,
                    navigationIconContentColor = MaterialTheme.colorScheme.onSurface
                ),
                modifier = Modifier.height(52.dp),
                windowInsets = WindowInsets(0, 0, 0, 0)
            )
        }
    ) { p ->
        val scrollState = rememberScrollState()
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(p)
                .background(MaterialTheme.colorScheme.background)
                .verticalScroll(scrollState)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // General Details
            Card(
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("مدیریت دسترسی مستقیم", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                    Text(
                        "به عنوان توسعه‌دهنده برنامه، شما می‌توانید مستقیماً لایسنس این گوشی را فعال یا بازنشانی کنید.",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Button(
                            onClick = { viewModel.adminForceActivate() },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF10B981), contentColor = Color.White),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(imageVector = Icons.Default.Check, contentDescription = null)
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("فعال‌سازی مستقیم", fontSize = 12.sp)
                        }

                        Button(
                            onClick = { viewModel.adminDeactivate() },
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error, contentColor = Color.White),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(imageVector = Icons.Default.Lock, contentDescription = null)
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("غیرفعال‌سازی لایسنس", fontSize = 12.sp)
                        }
                    }
                }
            }

            // Key Generator for Buyer IDs
            Card(
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(" محاسبه لایسنس خریدار جدید", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                    Text(
                        "نام خریدار و شناسایی دستگاه خریدار (مثلاً QZ-8F2C1) را وارد کنید تا لایسنس فعالسازی معتبر ایجاد گردد:",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    OutlinedTextField(
                        value = viewModel.adminTargetUserName,
                        onValueChange = { viewModel.adminTargetUserName = it },
                        label = { Text("نام کاربر (خریدار)") },
                        placeholder = { Text("مثال: همکار محترم جناب احمدی") },
                        singleLine = true,
                        shape = RoundedCornerShape(10.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = MaterialTheme.colorScheme.primary,
                            focusedLabelColor = MaterialTheme.colorScheme.primary
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )

                    OutlinedTextField(
                        value = viewModel.adminTargetDeviceId,
                        onValueChange = { viewModel.adminTargetDeviceId = it },
                        label = { Text("شناسه دستگاه خریدار") },
                        placeholder = { Text("QZ-XXXXX") },
                        singleLine = true,
                        shape = RoundedCornerShape(10.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = MaterialTheme.colorScheme.primary,
                            focusedLabelColor = MaterialTheme.colorScheme.primary
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )

                    Button(
                        onClick = { viewModel.adminGenerateKeyForDevice() },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary, contentColor = MaterialTheme.colorScheme.onSecondary),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(" محاسبه لایسنس فعالسازی")
                    }

                    if (viewModel.adminCalculatedKey.isNotBlank()) {
                        Card(
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                            modifier = Modifier
                                .fillMaxWidth()
                                .border(1.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(8.dp))
                        ) {
                            Row(
                                modifier = Modifier
                                    .padding(12.dp)
                                    .fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column {
                                    Text("لایسنس اختصاصی دستگاه:", fontSize = 11.sp, color = MaterialTheme.colorScheme.onPrimaryContainer)
                                    Text(
                                        text = viewModel.adminCalculatedKey,
                                        fontSize = 18.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                                Button(
                                    modifier = Modifier.height(36.dp),
                                    onClick = {
                                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                        val clip = ClipData.newPlainText("LicenseKey", viewModel.adminCalculatedKey)
                                        clipboard.setPrimaryClip(clip)
                                        Toast.makeText(context, "کد فعال‌سازی کپی شد", Toast.LENGTH_SHORT).show()
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary, contentColor = MaterialTheme.colorScheme.onPrimary)
                                ) {
                                    Text("کپی کد", fontSize = 12.sp)
                                }
                            }
                        }
                    }
                }
            }

            // Display Price Editor
            Card(
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(" تنظیم قیمت لایسنس نمایشی در قفل", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                    Text(
                        "قیمت برنامه را تغییر دهید (این مبلغ در قفل فعالساز خریدار نشان داده خواهد شد):",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    OutlinedTextField(
                        value = viewModel.licensePriceInput,
                        onValueChange = { viewModel.licensePriceInput = it },
                        label = { Text("مبلغ نمایشی فعال‌سازی") },
                        placeholder = { Text("۵,۰۰۰,۰۰۰ تومان") },
                        singleLine = true,
                        shape = RoundedCornerShape(10.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = MaterialTheme.colorScheme.primary,
                            focusedLabelColor = MaterialTheme.colorScheme.primary
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )

                    Button(
                        onClick = { viewModel.adminUpdatePrice(viewModel.licensePriceInput) },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary, contentColor = MaterialTheme.colorScheme.onPrimary),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("به‌روزرسانی قیمت")
                    }
                }
            }

            // 4. Activated Devices List Section
            Card(
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("لیست دستگاه‌های فعال شده (همکاران)", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                    Text(
                        "در این بخش می‌توانید لیست کامل همکارانی که کد لایسنس دریافت کرده‌اند را مشاهده کرده و در صورت نیاز دسترسی هر دستگاه را فوراً حذف کنید:",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    val devicesState by viewModel.activatedDevices.collectAsStateWithLifecycle()

                    if (devicesState.isEmpty()) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("هیچ دستگاه فعالی ثبت نشده است.", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f))
                        }
                    } else {
                        devicesState.forEach { device ->
                            Card(
                                shape = RoundedCornerShape(8.dp),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)),
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
                            ) {
                                Column(
                                    modifier = Modifier.padding(12.dp),
                                    verticalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column {
                                            if (device.userName.isNotBlank()) {
                                                Row(verticalAlignment = Alignment.CenterVertically) {
                                                    Icon(
                                                        imageVector = Icons.Default.Person,
                                                        contentDescription = null,
                                                        tint = MaterialTheme.colorScheme.secondary,
                                                        modifier = Modifier.size(14.dp)
                                                    )
                                                    Spacer(modifier = Modifier.width(4.dp))
                                                    Text(
                                                        text = "کاربر: ${device.userName}",
                                                        fontSize = 12.sp,
                                                        fontWeight = FontWeight.Bold,
                                                        color = MaterialTheme.colorScheme.onSurface
                                                    )
                                                }
                                                Spacer(modifier = Modifier.height(3.dp))
                                            }

                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Icon(
                                                    imageVector = Icons.Default.Info,
                                                    contentDescription = null,
                                                    tint = MaterialTheme.colorScheme.primary,
                                                    modifier = Modifier.size(14.dp)
                                                )
                                                Spacer(modifier = Modifier.width(4.dp))
                                                Text(
                                                    text = "شناسه: ${device.deviceId}",
                                                    fontSize = 12.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = MaterialTheme.colorScheme.onSurface
                                                )
                                            }
                                            Spacer(modifier = Modifier.height(2.dp))
                                            Text(
                                                text = "کلید فعال‌ساز: ${device.licenseKey}",
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = MaterialTheme.colorScheme.primary
                                            )
                                        }

                                        IconButton(
                                            onClick = { viewModel.deleteDeviceLicense(device.deviceId) },
                                            modifier = Modifier.size(36.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Delete,
                                                contentDescription = "حذف دسترسی",
                                                tint = MaterialTheme.colorScheme.error,
                                                modifier = Modifier.size(20.dp)
                                            )
                                        }
                                    }

                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = "تاریخ فعال‌سازی: ${device.activationDate}",
                                            fontSize = 10.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        Text(
                                            text = "وضعیت: فعال",
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Color(0xFF10B981)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Main Slate Workspace Form Editor for constructing Quartz Invoices.
 */
@Composable
fun EditorScreen(viewModel: InvoiceViewModel) {
    val context = LocalContext.current
    val suggestedPricesList by viewModel.suggestedPrices.collectAsStateWithLifecycle()
    var showSimpleAddDialog by remember { mutableStateOf(false) }
    var showPercentageAddDialog by remember { mutableStateOf(false) }
    var editingSimpleIndex by remember { mutableStateOf<Int?>(null) }
    var editingPercentageIndex by remember { mutableStateOf<Int?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("صدور فاکتور سنگ کوارتز", fontSize = 16.sp, fontWeight = FontWeight.Bold) },
                actions = {
                    IconButton(onClick = { viewModel.openImportDialog() }) {
                        Icon(imageVector = Icons.Default.Share, contentDescription = "بازیابی و ایمپورت فاکتور (PDF / Excel)")
                    }
                    IconButton(onClick = { viewModel.navigateTo("SETTINGS") }) {
                        Icon(imageVector = Icons.Default.Settings, contentDescription = "تنظیمات پیش‌فرض")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    titleContentColor = MaterialTheme.colorScheme.primary,
                    actionIconContentColor = MaterialTheme.colorScheme.primary
                ),
                modifier = Modifier.height(52.dp),
                windowInsets = WindowInsets(0, 0, 0, 0)
            )
        }
    ) { p ->
        Box(modifier = Modifier.padding(p)) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.background)
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                item { Spacer(modifier = Modifier.height(8.dp)) }

                // Quick Import Action Card
                item {
                    Card(
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.4f)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { viewModel.openImportDialog() }
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.weight(1f)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(38.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(MaterialTheme.colorScheme.primary),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Share,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onPrimary,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.width(10.dp))
                                Column {
                                    Text(
                                        text = "بازیابی و ورود فاکتور قدیمی (Excel / PDF)",
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                    Text(
                                        text = "استخراج ۱۰۰٪ دقیق بدون هوش مصنوعی از اکسل یا پی‌دی‌اف",
                                        fontSize = 11.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                            Button(
                                onClick = { viewModel.openImportDialog() },
                                shape = RoundedCornerShape(8.dp),
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                modifier = Modifier.height(34.dp)
                            ) {
                                Text("انتخاب فایل", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }

                // 0. Customizable Invoice Title and Subtitle Card
                item {
                    Card(
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                    ) {
                        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Info, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("تنظیمات تیتر و زیرتیتر فاکتور خروجی", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = MaterialTheme.colorScheme.primary)
                            }
                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                            OutlinedTextField(
                                value = viewModel.invoiceTitle,
                                onValueChange = { viewModel.invoiceTitle = it },
                                label = { Text("عنوان فاکتور") },
                                singleLine = true,
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                                    focusedLabelColor = MaterialTheme.colorScheme.primary
                                ),
                                modifier = Modifier.fillMaxWidth().testTag("custom_invoice_title_input")
                            )

                            OutlinedTextField(
                                value = viewModel.invoiceSubtitle,
                                onValueChange = { viewModel.invoiceSubtitle = it },
                                label = { Text("زیرعنوان فاکتور") },
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                                    focusedLabelColor = MaterialTheme.colorScheme.primary
                                ),
                                modifier = Modifier.fillMaxWidth().testTag("custom_invoice_subtitle_input")
                            )
                        }
                    }
                }

                // 1. Seller Info Collapsible / Card
                item {
                    Card(
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                    ) {
                        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Home, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("مشخصات فروشگاه / کارگاه", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = MaterialTheme.colorScheme.primary)
                            }
                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                OutlinedTextField(
                                    value = viewModel.sellerName,
                                    onValueChange = { viewModel.sellerName = it },
                                    label = { Text("نام فروشگاه") },
                                    singleLine = true,
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                                        focusedLabelColor = MaterialTheme.colorScheme.primary
                                    ),
                                    modifier = Modifier.weight(1f)
                                )
                                OutlinedTextField(
                                    value = viewModel.sellerPhone,
                                    onValueChange = { viewModel.sellerPhone = it },
                                    label = { Text("تلفن") },
                                    singleLine = true,
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                                        focusedLabelColor = MaterialTheme.colorScheme.primary
                                    ),
                                    modifier = Modifier.weight(1f)
                                )
                            }
                            OutlinedTextField(
                                value = viewModel.sellerAddress,
                                onValueChange = { viewModel.sellerAddress = it },
                                label = { Text("آدرس") },
                                maxLines = 2,
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                                    focusedLabelColor = MaterialTheme.colorScheme.primary
                                ),
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                }

                // 2. Buyer and Invoice Metadata Card
                item {
                    Card(
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                    ) {
                        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Person, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("مشخصات خریدار و فاکتور", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = MaterialTheme.colorScheme.primary)
                            }
                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                            OutlinedTextField(
                                value = viewModel.buyerName,
                                onValueChange = { viewModel.buyerName = it },
                                label = { Text("نام خریدار *") },
                                singleLine = true,
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                                    focusedLabelColor = MaterialTheme.colorScheme.primary
                                ),
                                modifier = Modifier.fillMaxWidth().testTag("buyer_name_input")
                            )

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                OutlinedTextField(
                                    value = viewModel.invoiceNo,
                                    onValueChange = { viewModel.invoiceNo = it },
                                    label = { Text("شماره فاکتور") },
                                    singleLine = true,
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                                        focusedLabelColor = MaterialTheme.colorScheme.primary
                                    ),
                                    modifier = Modifier.weight(1f)
                                )
                                OutlinedTextField(
                                    value = viewModel.invoiceDate,
                                    onValueChange = { viewModel.invoiceDate = it },
                                    label = { Text("تاریخ") },
                                    singleLine = true,
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                                        focusedLabelColor = MaterialTheme.colorScheme.primary
                                    ),
                                    modifier = Modifier.weight(1f)
                                )
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                OutlinedTextField(
                                    value = viewModel.stoneCode,
                                    onValueChange = { viewModel.stoneCode = it },
                                    label = { Text("کد سنگ") },
                                    singleLine = true,
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                                        focusedLabelColor = MaterialTheme.colorScheme.primary
                                    ),
                                    modifier = Modifier.weight(1f)
                                )
                                OutlinedTextField(
                                    value = viewModel.stoneType,
                                    onValueChange = { viewModel.stoneType = it },
                                    label = { Text("نوع سنگ") },
                                    singleLine = true,
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                                        focusedLabelColor = MaterialTheme.colorScheme.primary
                                    ),
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                    }
                }

                // Title label for items list
                item {
                    Text(
                        text = "اقلام و خدمات فاکتور",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onBackground,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }

                // 3. Normal / Quartz Items Cards
                itemsIndexed(viewModel.normalItems) { index, item ->
                    NormalItemRowCard(
                        index = index,
                        item = item,
                        suggestedPrices = suggestedPricesList,
                        onUpdate = { updated -> viewModel.updateNormalItem(index, updated) },
                        onDelete = { viewModel.removeNormalItemAt(index) }
                    )
                }

                // 4. Simple Items ListView Cards
                itemsIndexed(viewModel.simpleItems) { index, item ->
                    SimpleItemRowCard(
                        rowNum = viewModel.normalItems.size + index + 1,
                        description = item.description,
                        totalAmount = item.totalAmount.toString(),
                        quantityStr = item.quantityStr,
                        onEdit = { editingSimpleIndex = index },
                        onDelete = { viewModel.removeSimpleItemAt(index) }
                    )
                }

                // 4.1 Percentage Items ListView Cards
                val baseSumForPercentages = viewModel.normalItems.sumOf { it.totalAmount } + viewModel.simpleItems.sumOf { it.totalAmount }
                itemsIndexed(viewModel.percentageItems) { index, item ->
                    val pct = com.example.util.importer.TextNormalizer.parseNumber(item.percentageStr) ?: 0.0
                    val calculatedAmt = ((baseSumForPercentages * pct) / 100.0).let { kotlin.math.round(it) }
                    PercentageItemRowCard(
                        rowNum = viewModel.normalItems.size + viewModel.simpleItems.size + index + 1,
                        description = item.description,
                        percentageStr = item.percentageStr,
                        calculatedAmount = calculatedAmt,
                        onEdit = { editingPercentageIndex = index },
                        onDelete = { viewModel.removePercentageItemAt(index) }
                    )
                }

                // If nothing to list
                if (viewModel.normalItems.isEmpty() && viewModel.simpleItems.isEmpty() && viewModel.percentageItems.isEmpty()) {
                    item {
                        Card(
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(24.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text("هیچ قلم کالایی در فاکتور اضافه نشده است.", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
                            }
                        }
                    }
                }

                // 4.5 Non-floating addition controls (preventing overlap on bottom action buttons)
                item {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "بخش محاسبات و خدمات جدید فاکتور",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(horizontal = 4.dp)
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Button(
                                onClick = { viewModel.addNormalItemRow() },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                                ),
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier
                                    .weight(1f)
                                    .height(48.dp)
                                    .testTag("add_stone_row_button")
                            ) {
                                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("محاسبه سنگ کوارتز", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }

                            Button(
                                onClick = { showSimpleAddDialog = true },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.secondaryContainer,
                                    contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                                ),
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier
                                    .weight(1f)
                                    .height(48.dp)
                                    .testTag("add_service_row_button")
                            ) {
                                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("سطر ساده / خدمات", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }

                            Button(
                                onClick = { showPercentageAddDialog = true },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                                    contentColor = MaterialTheme.colorScheme.onTertiaryContainer
                                ),
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier
                                    .weight(1f)
                                    .height(48.dp)
                                    .testTag("add_percentage_row_button")
                            ) {
                                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("سطر درصدی", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }

                // 5. Executive Approver Signatures Card
                item {
                    Card(
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                    ) {
                        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Text("امضاکنندگان فاکتور", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = MaterialTheme.colorScheme.primary)
                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                OutlinedTextField(
                                    value = viewModel.managerSign,
                                    onValueChange = { viewModel.managerSign = it },
                                    label = { Text("سمت امضاکننده اول") },
                                    singleLine = true,
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                                        focusedLabelColor = MaterialTheme.colorScheme.primary
                                    ),
                                    modifier = Modifier.weight(1f)
                                )
                                OutlinedTextField(
                                    value = viewModel.salesSign,
                                    onValueChange = { viewModel.salesSign = it },
                                    label = { Text("سمت امضاکننده دوم") },
                                    singleLine = true,
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                                        focusedLabelColor = MaterialTheme.colorScheme.primary
                                    ),
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                    }
                }

                // 6. Visual Invoice Total Ledger Card
                item {
                    Card(
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .padding(20.dp)
                                .fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text("جمع کل فاکتور کوارتز", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = MaterialTheme.colorScheme.onPrimaryContainer)
                                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 4.dp)) {
                                    Text(
                                        text = formatMoney(viewModel.grandTotal),
                                        fontSize = 22.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("ریال", fontSize = 12.sp, color = MaterialTheme.colorScheme.onPrimaryContainer)
                                }
                            }

                            Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(36.dp)
                            )
                        }
                    }
                }

                // 7. Core Invoice Operation Buttons
                item {
                    Card(
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                    ) {
                        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Button(
                                    onClick = { viewModel.printCurrentInvoice(context) },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = MaterialTheme.colorScheme.primary,
                                        contentColor = MaterialTheme.colorScheme.onPrimary
                                    ),
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(48.dp)
                                        .testTag("print_invoice_hardware_button")
                                ) {
                                    Icon(Icons.Default.Send, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("چاپ مستقیم (پرینت)", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                }

                                Button(
                                    onClick = { viewModel.printCurrentInvoice(context) },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                                        contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                                    ),
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(48.dp)
                                        .testTag("print_invoice_pdf_button")
                                ) {
                                    Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("ذخیره / خروجی PDF", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                }
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                                   OutlinedButton(
                                    onClick = { viewModel.createNewInvoice() },
                                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                                    shape = RoundedCornerShape(8.dp),
                                    colors = ButtonDefaults.outlinedButtonColors(
                                        contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                                    ),
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(44.dp)
                                        .testTag("clear_editor_button")
                                ) {
                                    Icon(Icons.Default.Refresh, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("فاکتور جدید/خالی", fontSize = 12.sp)
                                }
                            }
                        }
                    }
                }

                item { Spacer(modifier = Modifier.height(30.dp)) }
            }
        }
    }

    if (showSimpleAddDialog) {
        var simpleDesc by remember { mutableStateOf("") }
        var simpleAmt by remember { mutableStateOf("") }
        var simpleQty by remember { mutableStateOf("") }

        Dialog(onDismissRequest = { showSimpleAddDialog = false }) {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                Column(
                    modifier = Modifier.padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Text("سند سطر هزینه ساده / خدمات جانبی", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = MaterialTheme.colorScheme.primary)
                    Text("مانند هزینه اختصاصی برش زدن، سینک کفتراش، ابزار تراش، هزینه حمل و نصب سنگ کوارتز:", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)

                    OutlinedTextField(
                        value = simpleDesc,
                        onValueChange = { simpleDesc = it },
                        label = { Text("عنوان خدمات (مثلا: سینک کفتراش)") },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = MaterialTheme.colorScheme.primary,
                            focusedLabelColor = MaterialTheme.colorScheme.primary
                        ),
                        modifier = Modifier.fillMaxWidth().testTag("simple_service_desc_input")
                    )

                    OutlinedTextField(
                        value = formatInputWithCommas(simpleAmt),
                        onValueChange = { simpleAmt = formatInputWithCommas(it) },
                        label = { Text("مبلغ کل خدمات") },
                        suffix = { Text("ریال", fontSize = 11.sp, color = MaterialTheme.colorScheme.primary) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = MaterialTheme.colorScheme.primary,
                            focusedLabelColor = MaterialTheme.colorScheme.primary
                        ),
                        modifier = Modifier.fillMaxWidth().testTag("simple_service_price_input")
                    )

                    OutlinedTextField(
                        value = simpleQty,
                        onValueChange = { simpleQty = it },
                        label = { Text("تعداد (اختیاری)") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = MaterialTheme.colorScheme.primary,
                            focusedLabelColor = MaterialTheme.colorScheme.primary
                        ),
                        modifier = Modifier.fillMaxWidth().testTag("simple_service_qty_input")
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        TextButton(onClick = { showSimpleAddDialog = false }) {
                            Text("انصراف", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Button(
                            onClick = {
                                if (simpleDesc.isNotBlank() && simpleAmt.isNotBlank()) {
                                    viewModel.addSimpleItemRow(simpleDesc, simpleAmt, simpleQty)
                                    showSimpleAddDialog = false
                                } else {
                                    Toast.makeText(context, "کادرهای الزامی را بنویسید", Toast.LENGTH_SHORT).show()
                                }
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.secondary,
                                contentColor = MaterialTheme.colorScheme.onSecondary
                            )
                        ) {
                            Text("افزودن سطر")
                        }
                    }
                }
            }
        }
    }

    if (showPercentageAddDialog) {
        var pctDesc by remember { mutableStateOf("") }
        var pctVal by remember { mutableStateOf("") }

        Dialog(onDismissRequest = { showPercentageAddDialog = false }) {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                Column(
                    modifier = Modifier.padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Text("افزودن سطر درصدی (مثال: اجرت نصب)", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = MaterialTheme.colorScheme.primary)
                    Text("این نوع سطر با محاسبه درصد تعیین شده از کل فاکتور ساخته می‌شود:", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)

                    OutlinedTextField(
                        value = pctDesc,
                        onValueChange = { pctDesc = it },
                        label = { Text("عنوان (مانند: اجرت نصب و کلاف)") },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = MaterialTheme.colorScheme.primary,
                            focusedLabelColor = MaterialTheme.colorScheme.primary
                        ),
                        modifier = Modifier.fillMaxWidth().testTag("percentage_desc_input")
                    )

                    OutlinedTextField(
                        value = pctVal,
                        onValueChange = { pctVal = it },
                        label = { Text("درصد (مثال: 10)") },
                        suffix = { Text("درصد %", fontSize = 11.sp, color = MaterialTheme.colorScheme.primary) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = MaterialTheme.colorScheme.primary,
                            focusedLabelColor = MaterialTheme.colorScheme.primary
                        ),
                        modifier = Modifier.fillMaxWidth().testTag("percentage_rate_input")
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        TextButton(onClick = { showPercentageAddDialog = false }) {
                            Text("انصراف", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Button(
                            onClick = {
                                if (pctDesc.isNotBlank() && pctVal.isNotBlank()) {
                                    viewModel.addPercentageItemRow(pctDesc, pctVal)
                                    showPercentageAddDialog = false
                                } else {
                                    Toast.makeText(context, "کادرهای الزامی را بنویسید", Toast.LENGTH_SHORT).show()
                                }
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primary,
                                contentColor = MaterialTheme.colorScheme.onPrimary
                            )
                        ) {
                            Text("افزودن سطر درصدی")
                        }
                    }
                }
            }
        }
    }

    if (editingSimpleIndex != null) {
        val index = editingSimpleIndex!!
        if (index in viewModel.simpleItems.indices) {
            val item = viewModel.simpleItems[index]
            var simpleDesc by remember(index) { mutableStateOf(item.description) }
            var simpleAmt by remember(index) { mutableStateOf(item.totalAmountStr) }
            var simpleQty by remember(index) { mutableStateOf(item.quantityStr) }

            Dialog(onDismissRequest = { editingSimpleIndex = null }) {
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(18.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        Text("ویرایش سطر هزینه ساده / خدمات جانبی", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = MaterialTheme.colorScheme.primary)

                        OutlinedTextField(
                            value = simpleDesc,
                            onValueChange = { simpleDesc = it },
                            label = { Text("عنوان خدمات (مثلا: سینک کفتراش)") },
                            singleLine = true,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = MaterialTheme.colorScheme.primary,
                                focusedLabelColor = MaterialTheme.colorScheme.primary
                            ),
                            modifier = Modifier.fillMaxWidth().testTag("edit_simple_service_desc_input")
                        )

                        OutlinedTextField(
                            value = formatInputWithCommas(simpleAmt),
                            onValueChange = { simpleAmt = formatInputWithCommas(it) },
                            label = { Text("مبلغ کل خدمات") },
                            suffix = { Text("ریال", fontSize = 11.sp, color = MaterialTheme.colorScheme.primary) },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = MaterialTheme.colorScheme.primary,
                                focusedLabelColor = MaterialTheme.colorScheme.primary
                            ),
                            modifier = Modifier.fillMaxWidth().testTag("edit_simple_service_price_input")
                        )

                        OutlinedTextField(
                            value = simpleQty,
                            onValueChange = { simpleQty = it },
                            label = { Text("تعداد (اختیاری)") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = MaterialTheme.colorScheme.primary,
                                focusedLabelColor = MaterialTheme.colorScheme.primary
                            ),
                            modifier = Modifier.fillMaxWidth().testTag("edit_simple_service_qty_input")
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            TextButton(onClick = { editingSimpleIndex = null }) {
                                Text("انصراف", color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            Button(
                                onClick = {
                                    if (simpleDesc.isNotBlank() && simpleAmt.isNotBlank()) {
                                        viewModel.updateSimpleItem(
                                            index,
                                            com.example.data.model.ComposeSimpleItem(
                                                description = simpleDesc,
                                                totalAmountStr = simpleAmt,
                                                quantityStr = simpleQty
                                            )
                                        )
                                        editingSimpleIndex = null
                                    } else {
                                        Toast.makeText(context, "کادرهای الزامی را بنویسید", Toast.LENGTH_SHORT).show()
                                    }
                                },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.secondary,
                                    contentColor = MaterialTheme.colorScheme.onSecondary
                                )
                            ) {
                                Text("ثبت تغییرات")
                            }
                        }
                    }
                }
            }
        }
    }

    if (editingPercentageIndex != null) {
        val index = editingPercentageIndex!!
        if (index in viewModel.percentageItems.indices) {
            val item = viewModel.percentageItems[index]
            var pctDesc by remember(index) { mutableStateOf(item.description) }
            var pctVal by remember(index) { mutableStateOf(item.percentageStr) }

            Dialog(onDismissRequest = { editingPercentageIndex = null }) {
                Card(
                     shape = RoundedCornerShape(16.dp),
                     colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                     border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                     modifier = Modifier
                         .fillMaxWidth()
                         .padding(16.dp)
                ) {
                     Column(
                         modifier = Modifier.padding(18.dp),
                         verticalArrangement = Arrangement.spacedBy(14.dp)
                     ) {
                         Text("ویرایش سطر درصدی (مثال: اجرت نصب)", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = MaterialTheme.colorScheme.primary)

                         OutlinedTextField(
                             value = pctDesc,
                             onValueChange = { pctDesc = it },
                             label = { Text("عنوان (مانند: اجرت نصب و کلاف)") },
                             singleLine = true,
                             colors = OutlinedTextFieldDefaults.colors(
                                 focusedBorderColor = MaterialTheme.colorScheme.primary,
                                 focusedLabelColor = MaterialTheme.colorScheme.primary
                             ),
                             modifier = Modifier.fillMaxWidth().testTag("edit_percentage_desc_input")
                         )

                         OutlinedTextField(
                             value = pctVal,
                             onValueChange = { pctVal = it },
                             label = { Text("درصد (مثال: 10)") },
                             suffix = { Text("درصد %", fontSize = 11.sp, color = MaterialTheme.colorScheme.primary) },
                             singleLine = true,
                             keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                             colors = OutlinedTextFieldDefaults.colors(
                                 focusedBorderColor = MaterialTheme.colorScheme.primary,
                                 focusedLabelColor = MaterialTheme.colorScheme.primary
                             ),
                             modifier = Modifier.fillMaxWidth().testTag("edit_percentage_rate_input")
                         )

                         Row(
                             modifier = Modifier.fillMaxWidth(),
                             horizontalArrangement = Arrangement.End,
                             verticalAlignment = Alignment.CenterVertically
                         ) {
                             TextButton(onClick = { editingPercentageIndex = null }) {
                                 Text("انصراف", color = MaterialTheme.colorScheme.onSurfaceVariant)
                             }
                             Spacer(modifier = Modifier.width(8.dp))
                             Button(
                                 onClick = {
                                     if (pctDesc.isNotBlank() && pctVal.isNotBlank()) {
                                         viewModel.updatePercentageItem(
                                             index,
                                             com.example.data.model.ComposePercentageItem(
                                                 description = pctDesc,
                                                 percentageStr = pctVal
                                             )
                                         )
                                         editingPercentageIndex = null
                                     } else {
                                         Toast.makeText(context, "کادرهای الزامی را بنویسید", Toast.LENGTH_SHORT).show()
                                     }
                                 },
                                 colors = ButtonDefaults.buttonColors(
                                     containerColor = MaterialTheme.colorScheme.primary,
                                     contentColor = MaterialTheme.colorScheme.onPrimary
                                 )
                             ) {
                                 Text("ثبت تغییرات")
                             }
                         }
                     }
                }
            }
        }
    }
}

/**
 * Table Component representing a Normal Quartz Stone Invoice Row
 */
@Composable
fun NormalItemRowCard(
    index: Int,
    item: ComposeInvoiceItem,
    suggestedPrices: List<String> = emptyList(),
    onUpdate: (ComposeInvoiceItem) -> Unit,
    onDelete: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Row metadata
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(24.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primaryContainer),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("${index + 1}", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("برآورد ابعاد ورق سنگ", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                }

                IconButton(
                    onClick = onDelete,
                    modifier = Modifier.size(28.dp)
                ) {
                    Icon(imageVector = Icons.Default.Delete, contentDescription = "حذف سطر", tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(18.dp))
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

            // Quick preset description buttons: صفحه، دیوارکوب، جزیره
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "شرح سریع:",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                listOf("صفحه", "دیوارکوب", "جزیره").forEach { word ->
                    FilledTonalButton(
                        onClick = { onUpdate(item.copy(description = word)) },
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.height(30.dp),
                        colors = ButtonDefaults.filledTonalButtonColors(
                            containerColor = if (item.description == word) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
                            contentColor = if (item.description == word) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface
                        )
                    ) {
                        Text(word, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }

            OutlinedTextField(
                value = item.description,
                onValueChange = { onUpdate(item.copy(description = it)) },
                label = { Text("شرح کالا (مثال: ابعاد پیشخوان آشپزخانه)") },
                singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    focusedLabelColor = MaterialTheme.colorScheme.primary
                ),
                modifier = Modifier.fillMaxWidth()
            )

            // Quick Button for Width 60
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("ابعاد و فی واحد سنگ:", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant)
                FilledTonalButton(
                    onClick = { onUpdate(item.copy(width = "60")) },
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.height(28.dp),
                    colors = ButtonDefaults.filledTonalButtonColors(
                        containerColor = if (item.width == "60") MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.7f),
                        contentColor = if (item.width == "60") MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSecondaryContainer
                    )
                ) {
                    Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(13.dp))
                    Spacer(modifier = Modifier.width(3.dp))
                    Text("عرض ۶۰ (پیش‌فرض)", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = item.width,
                    onValueChange = { onUpdate(item.copy(width = it)) },
                    label = { Text("عرض (cm)") },
                    placeholder = { Text("60") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        focusedLabelColor = MaterialTheme.colorScheme.primary
                    ),
                    modifier = Modifier.weight(1f)
                )

                OutlinedTextField(
                    value = item.length,
                    onValueChange = { onUpdate(item.copy(length = it)) },
                    label = { Text("طول (m)") },
                    placeholder = { Text("3.2") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        focusedLabelColor = MaterialTheme.colorScheme.primary
                    ),
                    modifier = Modifier.weight(1f)
                )

                OutlinedTextField(
                    value = formatInputWithCommas(item.price60cm),
                    onValueChange = { onUpdate(item.copy(price60cm = formatInputWithCommas(it))) },
                    label = { Text("فی عرض 60cm") },
                    suffix = { Text("ریال", fontSize = 11.sp, color = MaterialTheme.colorScheme.primary) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        focusedLabelColor = MaterialTheme.colorScheme.primary
                    ),
                    modifier = Modifier.weight(1.5f)
                )
            }

            // Quick Frequent/Recent Price Chips
            if (suggestedPrices.isNotEmpty()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("فی‌های پرکاربرد/اخیر:", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    suggestedPrices.forEach { price ->
                        FilterChip(
                            selected = item.price60cm.replace(",", "").trim() == price.replace(",", "").trim(),
                            onClick = { onUpdate(item.copy(price60cm = formatInputWithCommas(price))) },
                            label = { Text(price, fontSize = 10.sp, fontWeight = FontWeight.Medium) },
                            modifier = Modifier.height(28.dp)
                        )
                    }
                }
            }

            // Real-time Calculative Feedback Panels
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .padding(8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text("فی عرض ساخته شده:", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(
                        text = "${formatMoney(item.finalPrice)} ریال",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }

                Column(horizontalAlignment = Alignment.End) {
                    Text("مبلغ کل ردیف:", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(
                        text = "${formatMoney(item.totalAmount)} ریال",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }
    }
}

/**
 * Simple Service Row Card
 */
@Composable
fun SimpleItemRowCard(
    rowNum: Int,
    description: String,
    totalAmount: String,
    quantityStr: String = "",
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                Box(
                    modifier = Modifier
                        .size(24.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.secondaryContainer),
                    contentAlignment = Alignment.Center
                ) {
                    Text("$rowNum", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.secondary)
                }
                Spacer(modifier = Modifier.width(10.dp))
                Column {
                    Text(
                        text = if (quantityStr.isNotBlank()) "خدمات جانبی و فرعی (تعداد: $quantityStr)" else "خدمات جانبی و فرعی",
                        fontSize = 10.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(text = description, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                }
            }

            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Column(horizontalAlignment = Alignment.End) {
                    Text(text = "مبلغ کل ردیف:", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(text = "${formatMoney(totalAmount)} ریال", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                }

                IconButton(
                    onClick = onEdit,
                    modifier = Modifier.size(28.dp)
                ) {
                    Icon(imageVector = Icons.Default.Edit, contentDescription = "ویرایش", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
                }

                IconButton(
                    onClick = onDelete,
                    modifier = Modifier.size(28.dp)
                ) {
                    Icon(imageVector = Icons.Default.Delete, contentDescription = "حذف", tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(16.dp))
                }
            }
        }
    }
}

/**
 * Percentage Fee Row Card
 */
@Composable
fun PercentageItemRowCard(
    rowNum: Int,
    description: String,
    percentageStr: String,
    calculatedAmount: Double,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.3f)
        ),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.tertiary.copy(alpha = 0.2f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                Box(
                    modifier = Modifier
                        .size(24.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.tertiaryContainer),
                    contentAlignment = Alignment.Center
                ) {
                    Text("$rowNum", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.tertiary)
                }
                Spacer(modifier = Modifier.width(10.dp))
                Column {
                    Text(text = "خدمات درصدی ($percentageStr درصد)", fontSize = 10.sp, color = MaterialTheme.colorScheme.tertiary, fontWeight = FontWeight.Bold)
                    Text(text = description, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                }
            }

            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Column(horizontalAlignment = Alignment.End) {
                    Text(text = "مبلغ محاسبه شده:", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(text = "${formatMoney(calculatedAmount)} ریال", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                }

                IconButton(
                    onClick = onEdit,
                    modifier = Modifier.size(28.dp)
                ) {
                    Icon(imageVector = Icons.Default.Edit, contentDescription = "ویرایش", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
                }

                IconButton(
                    onClick = onDelete,
                    modifier = Modifier.size(28.dp)
                ) {
                    Icon(imageVector = Icons.Default.Delete, contentDescription = "حذف", tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(16.dp))
                }
            }
        }
    }
}

/**
 * Saved Records History Listing screen
 */
@Composable
fun HistoryScreen(viewModel: InvoiceViewModel) {
    val context = LocalContext.current
    val savedList by viewModel.savedInvoices.collectAsStateWithLifecycle()
    val totalRevenueFlow by viewModel.totalRevenueFlow.collectAsStateWithLifecycle()
    val totalCountFlow by viewModel.totalCountFlow.collectAsStateWithLifecycle()
    var searchQuery by remember { mutableStateOf("") }

    val coworkerPackagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            viewModel.restoreBackupFromUri(context, uri)
        }
    }

    val multiQzbPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenMultipleDocuments()
    ) { uris: List<Uri> ->
        if (uris.isNotEmpty()) {
            viewModel.importQzbFiles(context, uris)
        }
    }

    val filteredList = remember(savedList, searchQuery) {
        if (searchQuery.isBlank()) {
            savedList
        } else {
            savedList.filter { invoice ->
                invoice.buyerName.contains(searchQuery, ignoreCase = true) ||
                invoice.invoiceNo.contains(searchQuery, ignoreCase = true) ||
                invoice.stoneCode.contains(searchQuery, ignoreCase = true) ||
                invoice.stoneType.contains(searchQuery, ignoreCase = true)
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("فاکتورهای ذخیره شده (${totalCountFlow})", fontSize = 15.sp, fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = { viewModel.navigateTo("EDITOR") }) {
                        Icon(imageVector = Icons.Default.Close, contentDescription = "بازگشت")
                    }
                },
                actions = {
                    IconButton(onClick = { viewModel.createAndShareFullBackup(context) }) {
                        Icon(imageVector = Icons.Default.Share, contentDescription = "ارسال یکجای تمام فاکتورها به همکار")
                    }
                    IconButton(onClick = {
                        coworkerPackagePickerLauncher.launch(
                            arrayOf("*/*", "application/json", "application/octet-stream")
                        )
                    }) {
                        Icon(imageVector = Icons.Default.Add, contentDescription = "دریافت بسته فاکتور از همکار")
                    }
                    IconButton(onClick = { viewModel.openImportDialog() }) {
                        Icon(imageVector = Icons.Default.Edit, contentDescription = "استخراج از PDF / Excel")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    titleContentColor = MaterialTheme.colorScheme.onSurface,
                    navigationIconContentColor = MaterialTheme.colorScheme.onSurface
                ),
                modifier = Modifier.height(52.dp),
                windowInsets = WindowInsets(0, 0, 0, 0)
            )
        }
    ) { p ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(p)
                .background(MaterialTheme.colorScheme.background)
        ) {
            // Live Stats & Fast Actions Card
            Card(
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "مجموع کل کارکرد فاکتورها:",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = formatMoney(totalRevenueFlow),
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("ریال", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }

                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Button(
                            onClick = { viewModel.createAndShareFullBackup(context) },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0xFF10B981),
                                contentColor = Color.White
                            ),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.weight(1f).height(38.dp)
                        ) {
                            Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(13.dp))
                            Spacer(modifier = Modifier.width(3.dp))
                            Text("ارسال همکار", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                        }

                        Button(
                            onClick = {
                                multiQzbPickerLauncher.launch(
                                    arrayOf("*/*", "application/json", "application/octet-stream")
                                )
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.secondary,
                                contentColor = MaterialTheme.colorScheme.onSecondary
                            ),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.weight(1.2f).height(38.dp)
                        ) {
                            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(13.dp))
                            Spacer(modifier = Modifier.width(3.dp))
                            Text("افزودن فاکتور (.qzb)", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                        }

                        Button(
                            onClick = {
                                coworkerPackagePickerLauncher.launch(
                                    arrayOf("*/*", "application/json", "application/octet-stream")
                                )
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primary,
                                contentColor = MaterialTheme.colorScheme.onPrimary
                            ),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.weight(1.1f).height(38.dp)
                        ) {
                            Icon(Icons.Default.AddCircle, contentDescription = null, modifier = Modifier.size(13.dp))
                            Spacer(modifier = Modifier.width(3.dp))
                            Text("بسته همکار", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            // Search Input Block
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = { Text("جستجو در نام خریدار، شماره فاکتور، کد یا نوع سنگ...", fontSize = 12.sp) },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = "جستجو", modifier = Modifier.size(18.dp)) },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { searchQuery = "" }) {
                            Icon(Icons.Default.Clear, contentDescription = "پاک کردن", modifier = Modifier.size(18.dp))
                        }
                    }
                },
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp)
            )

            if (filteredList.isEmpty()) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .weight(1f)
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.List,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                        modifier = Modifier.size(64.dp)
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        if (searchQuery.isNotBlank()) "هیچ فاکتوری با مشخصات جستجو شده یافت نشد." else "هنوز فاکتوری ثبت نشده است.",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (searchQuery.isBlank()) {
                        Text(
                            "به محض ذخیره فاکتور در صفحه اصلی، فاکتورها به صورت خودکار در این بخش نمایش داده می‌شوند.",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                            textAlign = TextAlign.Center,
                            lineHeight = 18.sp,
                            modifier = Modifier.padding(start = 16.dp, top = 8.dp, end = 16.dp)
                        )

                        Spacer(modifier = Modifier.height(16.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(0.9f),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Button(
                                onClick = {
                                    coworkerPackagePickerLauncher.launch(
                                        arrayOf("*/*", "application/json", "application/octet-stream")
                                    )
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier.weight(1f).height(44.dp)
                            ) {
                                Icon(imageVector = Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("دریافت بسته همکار", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }

                            Button(
                                onClick = { viewModel.openImportDialog() },
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary),
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier.weight(1f).height(44.dp)
                            ) {
                                Icon(imageVector = Icons.Default.Share, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("ایمپورت از PDF", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .weight(1f)
                        .padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    itemsIndexed(filteredList) { _, invoice ->
                        HistoryInvoiceCard(
                            invoice = invoice,
                            onLoad = { viewModel.loadInvoice(invoice) },
                            onDelete = { viewModel.deleteInvoice(invoice) },
                            onShare = { viewModel.shareInvoice(context, invoice) }
                        )
                    }
                }
            }
        }
    }

    // Loading Dialog
    if (viewModel.isBackupOperationLoading) {
        AlertDialog(
            onDismissRequest = {},
            title = { Text("در حال بازیابی فاکتورها...", fontWeight = FontWeight.Bold) },
            text = {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                    Text("لطفاً شکیبا باشید، در حال خواندن فایل‌های .qzb و ثبت در دیتابیس...", fontSize = 12.sp)
                }
            },
            confirmButton = {}
        )
    }

    // Result Dialog
    viewModel.backupOperationMessage?.let { msg ->
        AlertDialog(
            onDismissRequest = { viewModel.backupOperationMessage = null },
            icon = { Icon(Icons.Default.CheckCircle, contentDescription = null, tint = Color(0xFF10B981)) },
            title = { Text("نتیجه بازیابی فاکتورها", fontWeight = FontWeight.Bold) },
            text = { Text(text = msg, fontSize = 12.sp, lineHeight = 20.sp) },
            confirmButton = {
                Button(
                    onClick = { viewModel.backupOperationMessage = null },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                ) {
                    Text("متوجه شدم", fontWeight = FontWeight.Bold)
                }
            }
        )
    }

    if (viewModel.showShareConfirmDialog) {
        AlertDialog(
            onDismissRequest = { viewModel.dismissShareConfirmDialog() },
            title = {
                Text(
                    text = "فاکتور پیدا نشد",
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp,
                    color = MaterialTheme.colorScheme.error
                )
            },
            text = {
                Text(
                    text = "ابتدا ذخیره کنید و بعد ارسال کنید. آیا مایلید فاکتور به صورت خودکار ذخیره شده و منوی اشتراک‌گذاری باز شود؟",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurface
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.pendingShareInvoice?.let { inv ->
                            viewModel.autoSavePdfAndShare(context, inv)
                        }
                        viewModel.dismissShareConfirmDialog()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text("ذخیره و اشتراک‌گذاری خودکار", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.dismissShareConfirmDialog() }) {
                    Text("انصراف", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        )
    }
}

/**
 * Individual Invoice Record Element in history listing
 */
@Composable
fun HistoryInvoiceCard(
    invoice: InvoiceEntity,
    onLoad: () -> Unit,
    onDelete: () -> Unit,
    onShare: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            // Title Header with number
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "خریدار: ${invoice.buyerName}",
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "شماره فاکتور: ${invoice.invoiceNo}",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 2.dp)
                    )
                }

                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(MaterialTheme.colorScheme.primaryContainer)
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text(text = invoice.invoiceDate, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onPrimaryContainer)
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Bottom
            ) {
                // Info badges
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(6.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primary)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("نوع سنگ: ${invoice.stoneType.ifBlank { "پیش‌فرص" }}", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurface)
                    }
                    if (invoice.stoneCode.isNotBlank()) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(6.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.secondary)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("کد سنگ: ${invoice.stoneCode}", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurface)
                        }
                    }
                }

                // Balance Cash Ledger
                Column(horizontalAlignment = Alignment.End) {
                    Text("جمع کل فاکتور:", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Row(verticalAlignment = Alignment.Bottom) {
                        Text(
                            text = formatMoney(invoice.totalAmount),
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.width(3.dp))
                        Text("ریال", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = onShare,
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(imageVector = Icons.Default.Share, contentDescription = "اشتراک فاکتور", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                }

                Spacer(modifier = Modifier.width(8.dp))

                IconButton(
                    onClick = onDelete,
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(imageVector = Icons.Default.Delete, contentDescription = "حذف رکورد", tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(18.dp))
                }

                Spacer(modifier = Modifier.width(12.dp))

                Button(
                    onClick = onLoad,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.secondary,
                        contentColor = MaterialTheme.colorScheme.onSecondary
                    ),
                    shape = RoundedCornerShape(8.dp),
                    contentPadding = PaddingValues(start = 16.dp, top = 6.dp, end = 16.dp, bottom = 6.dp),
                    modifier = Modifier.height(32.dp)
                ) {
                    Icon(imageVector = Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(14.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("بارگذاری و ویرایش", fontSize = 11.sp)
                }
            }
        }
    }
}

/**
 * Sleek User Profile and Invoice Statistics Screen
 */
/**
 * Sleek User Profile and Invoice Statistics Screen
 */
@Composable
fun AccountScreen(viewModel: InvoiceViewModel) {
    val context = LocalContext.current
    val savedList by viewModel.savedInvoices.collectAsStateWithLifecycle()
    val configState by viewModel.appConfig.collectAsStateWithLifecycle()
    val deletedList by viewModel.deletedInvoices.collectAsStateWithLifecycle()

    var startDateInput by remember { mutableStateOf("") }
    var endDateInput by remember { mutableStateOf("") }
    var summarySearchQuery by remember { mutableStateOf("") }
    
    // Set default values for date inputs based on current year/months
    LaunchedEffect(Unit) {
        if (startDateInput.isBlank()) {
            val today = JalaliCalendar.getTodayJalali() // e.g. "1405/03/12"
            val parts = today.split("/")
            if (parts.size >= 3) {
                startDateInput = "${parts[0]}/${parts[1]}/01"
                endDateInput = today
            } else {
                startDateInput = "1405/01/01"
                endDateInput = "1405/12/29"
            }
        }
    }

    // List of invoices within the date range
    val periodInvoices = remember(savedList, startDateInput, endDateInput, summarySearchQuery) {
        savedList.filter { invoice ->
            val date = invoice.invoiceDate
            val dateOk = date >= startDateInput && date <= endDateInput
            val queryOk = if (summarySearchQuery.isBlank()) {
                true
            } else {
                invoice.buyerName.contains(summarySearchQuery, ignoreCase = true) ||
                invoice.invoiceNo.contains(summarySearchQuery, ignoreCase = true) ||
                invoice.stoneCode.contains(summarySearchQuery, ignoreCase = true)
            }
            dateOk && queryOk
        }
    }

    // Checking which ones are checked by default
    val selectedInvoicesMap = remember { mutableStateMapOf<Int, Boolean>() }
    val availableBackups by viewModel.availableBackups.collectAsStateWithLifecycle()
    var backupToRestoreConfirm by remember { mutableStateOf<com.example.util.BackupFileInfo?>(null) }

    val backupPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            viewModel.restoreBackupFromUri(context, uri)
        }
    }
    
    // Sync checkboxes when periodInvoices changes
    LaunchedEffect(periodInvoices) {
        periodInvoices.forEach { inv ->
            if (!selectedInvoicesMap.containsKey(inv.id)) {
                selectedInvoicesMap[inv.id] = true
            }
        }
    }

    val totalCount by viewModel.totalCountFlow.collectAsStateWithLifecycle()
    val totalRevenue by viewModel.totalRevenueFlow.collectAsStateWithLifecycle()
    val averageValue = if (totalCount > 0) totalRevenue / totalCount else 0.0

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("پیشخوان کاربری و آمار فاکتورها", fontSize = 15.sp, fontWeight = FontWeight.Bold) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    titleContentColor = MaterialTheme.colorScheme.onSurface
                ),
                modifier = Modifier.height(52.dp),
                windowInsets = WindowInsets(0, 0, 0, 0)
            )
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .background(MaterialTheme.colorScheme.background)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item { Spacer(modifier = Modifier.height(8.dp)) }

            // 1. Sleek Account Welcome Card
            item {
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                    ),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.2f))
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(18.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(54.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primary),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.Person, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(28.dp))
                        }
                        Spacer(modifier = Modifier.width(14.dp))
                        Column {
                            val isAdmin = configState?.licenseKey == com.example.util.LicenseManager.MASTER_ADMIN_PASSCODE
                            Text(
                                text = if (isAdmin) "ادمین گرامی خوش آمدید!" else "همکار گرامی خوش آمدید!",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                            val titleText = configState?.defaultInvoiceTitle?.ifBlank { "پیشخوان فاکتورهای کوارتز" } ?: "مدیریت صنایع سنگ"
                            Text(
                                text = titleText,
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                            )
                        }
                    }
                }
            }

            // 2. Navigation Settings shortcut card
            item {
                Button(
                    onClick = { viewModel.navigateTo("SETTINGS") },
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.inverseOnSurface,
                        contentColor = MaterialTheme.colorScheme.primary
                    ),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)),
                    modifier = Modifier.fillMaxWidth().height(50.dp)
                ) {
                    Icon(Icons.Default.Settings, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("ویرایش اطلاعات پیش‌فرض و تصاویر امضا", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            }

            // 3. Grid representation of Summary Metrics
            item {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        // Metric 1: Total Invoices
                        Card(
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(12.dp),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                        ) {
                            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text("تعداد فاکتورها", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text("$totalCount عدد", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                            }
                        }

                        // Metric 2: Average Basket Value
                        Card(
                            modifier = Modifier.weight(1.1f),
                            shape = RoundedCornerShape(12.dp),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                        ) {
                            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text("میانگین فاکتور", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text("${formatMoney(averageValue)} ریال", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.secondary)
                            }
                        }
                    }

                    // Metric 3: Total Sales Revenue
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f)),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.3f))
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(18.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("مجموع کل مبالغ فاکتورهای ذخیره شده", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onPrimaryContainer)
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(MaterialTheme.colorScheme.primary)
                                        .padding(horizontal = 8.dp, vertical = 3.dp)
                                ) {
                                    Text("$totalCount فاکتور", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onPrimary)
                                }
                            }

                            Row(verticalAlignment = Alignment.Bottom) {
                                Text(
                                    text = formatMoney(totalRevenue),
                                    fontSize = 22.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("ریال", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onPrimaryContainer)
                            }

                            Text(
                                text = "معادل: ${formatMoney(totalRevenue / 10.0)} تومان",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.85f)
                            )
                        }
                    }
                }
            }

            // 3.5. Full Offline Backup & Restore Section (ذخیره و بازیابی کامل دیتای برنامه)
            item {
                Text(
                    text = "پشتیبان‌گیری و بازیابی کل اطلاعات (آفلاین)",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }

            item {
                Card(
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(MaterialTheme.colorScheme.primaryContainer),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Default.Share, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                            }
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Text("مدیریت پشتیبان‌گیری آفلاین", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                                Text("ذخیره تمام فاکتورها، تنظیمات سربرگ و امضاها در قالب فایل امن (.qzb)", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }

                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))

                        // Backup Action Buttons
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Button(
                                onClick = { viewModel.createAndShareFullBackup(context) },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = Color(0xFF10B981),
                                    contentColor = Color.White
                                ),
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier.weight(1f).height(46.dp)
                            ) {
                                Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("ایجاد فایل پشتیبان کامل", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }

                            Button(
                                onClick = {
                                    backupPickerLauncher.launch(
                                        arrayOf(
                                            "*/*",
                                            "application/json",
                                            "application/octet-stream"
                                        )
                                    )
                                },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.primary,
                                    contentColor = MaterialTheme.colorScheme.onPrimary
                                ),
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier.weight(1f).height(46.dp)
                            ) {
                                Icon(Icons.Default.AddCircle, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("بازیابی از فایل", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                        }

                        // Available Backup Files on Device
                        if (availableBackups.isNotEmpty()) {
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "فایل‌های پشتیبان ذخیره‌شده روی گوشی:",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )

                            availableBackups.take(5).forEach { backupInfo ->
                                Card(
                                    shape = RoundedCornerShape(8.dp),
                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)),
                                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(
                                        modifier = Modifier.padding(10.dp),
                                        verticalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                                                Icon(
                                                    imageVector = Icons.Default.Info,
                                                    contentDescription = null,
                                                    tint = MaterialTheme.colorScheme.primary,
                                                    modifier = Modifier.size(16.dp)
                                                )
                                                Spacer(modifier = Modifier.width(6.dp))
                                                Text(
                                                    text = backupInfo.name,
                                                    fontSize = 11.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    maxLines = 1
                                                )
                                            }
                                            Text(
                                                text = "${backupInfo.invoiceCount} فاکتور",
                                                fontSize = 10.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = MaterialTheme.colorScheme.primary
                                            )
                                        }

                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(
                                                text = "تاریخ: ${backupInfo.lastModifiedFormatted} | حجم: ${backupInfo.sizeFormatted}",
                                                fontSize = 10.sp,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )

                                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                                Button(
                                                    onClick = { backupToRestoreConfirm = backupInfo },
                                                    shape = RoundedCornerShape(6.dp),
                                                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary),
                                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                                    modifier = Modifier.height(28.dp)
                                                ) {
                                                    Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(12.dp))
                                                    Spacer(modifier = Modifier.width(3.dp))
                                                    Text("بازیابی", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                                }

                                                IconButton(
                                                    onClick = { viewModel.startShareIntent(context, backupInfo.file) },
                                                    modifier = Modifier.size(28.dp)
                                                ) {
                                                    Icon(Icons.Default.Share, contentDescription = "اشتراک", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(14.dp))
                                                }

                                                IconButton(
                                                    onClick = { viewModel.deleteBackupFile(backupInfo.file) },
                                                    modifier = Modifier.size(28.dp)
                                                ) {
                                                    Icon(Icons.Default.Delete, contentDescription = "حذف", tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(14.dp))
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // 4. Date range summary PDF report section
            item {
                Text(
                    text = "خروجی PDF خلاصه کمیسیون و کارکرد دوره‌ای",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }

            item {
                Card(
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            text = "برای دریافت فایل خلاصه کارکرد با فرمت مجلل A4، تاریخ ابتدا و انتهای دوره را مشخص بفرمایید:",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            lineHeight = 18.sp
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedTextField(
                                value = startDateInput,
                                onValueChange = { startDateInput = it },
                                label = { Text("از تاریخ") },
                                placeholder = { Text("مثال: 1405/01/01") },
                                singleLine = true,
                                modifier = Modifier.weight(1f)
                            )

                            OutlinedTextField(
                                value = endDateInput,
                                onValueChange = { endDateInput = it },
                                label = { Text("تا تاریخ") },
                                placeholder = { Text("مثال: 1405/03/15") },
                                singleLine = true,
                                modifier = Modifier.weight(1f)
                            )
                        }

                        // Search and Filter candidate invoices inside selected date query
                        OutlinedTextField(
                            value = summarySearchQuery,
                            onValueChange = { summarySearchQuery = it },
                            label = { Text("جستجوی نام خریدار یا شماره فاکتور فیلتر شده") },
                            placeholder = { Text("برای گزینش دقیق‌تر...") },
                            singleLine = true,
                            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                            modifier = Modifier.fillMaxWidth()
                        )

                        // Candidate invoices display list
                        if (periodInvoices.isEmpty()) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(
                                        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                                        RoundedCornerShape(8.dp)
                                    )
                                    .padding(12.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text("هیچ فاکتوری در این بازه یافت نگردید.", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        } else {
                            Text("انتخاب فاکتورهای مجاز برای گزارش دوره:", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            
                            // Let's draw active items
                            periodInvoices.forEach { invoice ->
                                val isChecked = selectedInvoicesMap[invoice.id] ?: true
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(
                                            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f),
                                            RoundedCornerShape(6.dp)
                                        )
                                        .padding(6.dp)
                                        .clickable {
                                            selectedInvoicesMap[invoice.id] = !isChecked
                                        },
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Checkbox(
                                        checked = isChecked,
                                        onCheckedChange = { selectedInvoicesMap[invoice.id] = it }
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text("${invoice.buyerName} - ${invoice.invoiceNo}", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                        Text("تاریخ: ${invoice.invoiceDate} | مبلغ: ${formatMoney(invoice.totalAmount)} ریال", fontSize = 9.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                }
                            }
                        }

                        Button(
                            onClick = {
                                val printInvoices = periodInvoices.filter { selectedInvoicesMap[it.id] ?: true }
                                if (printInvoices.isEmpty()) {
                                    Toast.makeText(context, "هیچ فاکتوری علامت‌گذاری نشده است", Toast.LENGTH_SHORT).show()
                                } else {
                                    viewModel.printSummaryReport(
                                        printContext = context,
                                        startDate = startDateInput.ifBlank { "آغاز" },
                                        endDate = endDateInput.ifBlank { "پایان" },
                                        selectedInvoices = printInvoices
                                    )
                                }
                            },
                            shape = RoundedCornerShape(8.dp),
                            enabled = periodInvoices.isNotEmpty(),
                            modifier = Modifier.fillMaxWidth().height(48.dp)
                        ) {
                            Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("ساخت خروجی سمی و زیبا خلاصه کارکرد PDF", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            // 5. soft-delete Recycle Bin
            item {
                Text(
                    text = "سطل زباله فاکتورها (بازیابی)",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }

            if (deletedList.isEmpty()) {
                item {
                    Card(
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Box(modifier = Modifier.padding(16.dp).fillMaxWidth(), contentAlignment = Alignment.Center) {
                            Text("سطل زباله خالی است. آیتمی وجود ندارد.", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            } else {
                itemsIndexed(deletedList) { _, dInv ->
                    Card(
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.2f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column {
                                    Text(dInv.buyerName.ifBlank { "بدون نام" }, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                    Text("شماره: ${dInv.invoiceNo} | تاریخ: ${dInv.invoiceDate}", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                Text("${formatMoney(dInv.totalAmount)} ریال", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.error)
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Button(
                                    onClick = { viewModel.restoreInvoice(dInv) },
                                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondaryContainer, contentColor = MaterialTheme.colorScheme.onSecondaryContainer),
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier.weight(1f).height(36.dp)
                                ) {
                                    Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(14.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("بازیابی فاکتور", fontSize = 11.sp)
                                }

                                OutlinedButton(
                                    onClick = { viewModel.permanentDeleteInvoice(dInv) },
                                    colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.error),
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier.weight(1f).height(36.dp)
                                ) {
                                    Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(14.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("حذف برای همیشه", fontSize = 11.sp)
                                }
                            }
                        }
                    }
                }
            }

            // 6. Quick Config & Contact Support Information Card
            item {
                Text(
                    text = "ارتباط با پشتیبانی و همکاران",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }

            item {
                Card(
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                ) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Phone, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("شماره تماس پشتیبانی:", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            Spacer(modifier = Modifier.weight(1f))
                            Text("09102378055", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                        }

                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Email, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("ایمیل پشتیبانی:", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            Spacer(modifier = Modifier.weight(1f))
                            Text("salarybusines@gmail.com", fontSize = 12.sp, color = MaterialTheme.colorScheme.primary)
                        }

                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Info, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("کد شناسایی دستگاه شما:", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            Spacer(modifier = Modifier.weight(1f))
                            Text(configState?.deviceId ?: "نامشخص", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }

            // 5. Developer Admin Entrance Button - Only show if current user is Admin
            val isAdmin = configState?.licenseKey == com.example.util.LicenseManager.MASTER_ADMIN_PASSCODE
            if (isAdmin) {
                item {
                    OutlinedButton(
                        onClick = { viewModel.navigateTo("ADMIN_PANEL") },
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth().height(48.dp)
                    ) {
                        Icon(Icons.Default.Menu, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("ورود به پنل مدیریت همکاران (تنظیمات)", fontSize = 12.sp)
                    }
                }
            }

            item { Spacer(modifier = Modifier.height(30.dp)) }
        }

        // 1. Confirm Restore Dialog
        backupToRestoreConfirm?.let { bInfo ->
            AlertDialog(
                onDismissRequest = { backupToRestoreConfirm = null },
                icon = { Icon(Icons.Default.Refresh, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
                title = { Text("تأیید بازیابی اطلاعات", fontWeight = FontWeight.Bold) },
                text = {
                    Text(
                        text = "آیا مطمئن هستید که می‌خواهید فایل «${bInfo.name}» شامل ${bInfo.invoiceCount} فاکتور و اطلاعات فروشگاه را بازیابی کنید؟\n\nاین اطلاعات به پایگاه داده برنامه اضافه و اطلاعات سربرگ جای‌گذاری خواهند شد.",
                        fontSize = 12.sp,
                        lineHeight = 18.sp
                    )
                },
                confirmButton = {
                    Button(
                        onClick = {
                            val fileToRestore = bInfo.file
                            backupToRestoreConfirm = null
                            viewModel.restoreBackupFromFile(context, fileToRestore)
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                    ) {
                        Text("بله، بازیابی شود", fontWeight = FontWeight.Bold)
                    }
                },
                dismissButton = {
                    OutlinedButton(onClick = { backupToRestoreConfirm = null }) {
                        Text("انصراف")
                    }
                }
            )
        }

        // 2. Loading State Dialog
        if (viewModel.isBackupOperationLoading) {
            AlertDialog(
                onDismissRequest = {},
                title = { Text("در حال پردازش...", fontWeight = FontWeight.Bold) },
                text = {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                        Text("لطفاً شکیبا باشید، در حال انجام عملیات پشتیبان‌گیری / بازیابی...", fontSize = 12.sp)
                    }
                },
                confirmButton = {}
            )
        }

        // 3. Result / Info Dialog
        viewModel.backupOperationMessage?.let { msg ->
            AlertDialog(
                onDismissRequest = { viewModel.backupOperationMessage = null },
                icon = { Icon(Icons.Default.CheckCircle, contentDescription = null, tint = Color(0xFF10B981)) },
                title = { Text("عملیات پشتیبان‌گیری / بازیابی", fontWeight = FontWeight.Bold) },
                text = { Text(text = msg, fontSize = 12.sp, lineHeight = 20.sp) },
                confirmButton = {
                    Button(
                        onClick = { viewModel.backupOperationMessage = null },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                    ) {
                        Text("متوجه شدم", fontWeight = FontWeight.Bold)
                    }
                }
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(viewModel: InvoiceViewModel) {
    val context = LocalContext.current
    val configState by viewModel.appConfig.collectAsStateWithLifecycle()

    var sName by remember { mutableStateOf("") }
    var sPhone by remember { mutableStateOf("") }
    var sAddress by remember { mutableStateOf("") }
    var title by remember { mutableStateOf("") }
    var sub by remember { mutableStateOf("") }
    var mSign by remember { mutableStateOf("") }
    var sSign by remember { mutableStateOf("") }
    var mSignImgBase64 by remember { mutableStateOf("") }
    var sSignImgBase64 by remember { mutableStateOf("") }

    // Synchronize states with the saved entity when loaded
    LaunchedEffect(configState) {
        configState?.let {
            sName = it.defaultSellerName
            sPhone = it.defaultSellerPhone
            sAddress = it.defaultSellerAddress
            title = it.defaultInvoiceTitle
            sub = it.defaultInvoiceSubtitle
            mSign = it.defaultManagerSign
            sSign = it.defaultSalesSign
            mSignImgBase64 = it.defaultManagerSignImg
            sSignImgBase64 = it.defaultSalesSignImg
        }
    }

    val managerSignLauncher = rememberLauncherForActivityResult(
        contract = androidx.activity.result.contract.ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            try {
                val inputStream = context.contentResolver.openInputStream(uri)
                val bytes = inputStream?.readBytes()
                inputStream?.close()
                if (bytes != null) {
                    val base64 = android.util.Base64.encodeToString(bytes, android.util.Base64.DEFAULT)
                    val mime = context.contentResolver.getType(uri) ?: "image/png"
                    mSignImgBase64 = "data:$mime;base64,$base64"
                }
            } catch (e: Exception) {
                Toast.makeText(context, "خطا در خواندن تصویر: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    val salesSignLauncher = rememberLauncherForActivityResult(
        contract = androidx.activity.result.contract.ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            try {
                val inputStream = context.contentResolver.openInputStream(uri)
                val bytes = inputStream?.readBytes()
                inputStream?.close()
                if (bytes != null) {
                    val base64 = android.util.Base64.encodeToString(bytes, android.util.Base64.DEFAULT)
                    val mime = context.contentResolver.getType(uri) ?: "image/png"
                    sSignImgBase64 = "data:$mime;base64,$base64"
                }
            } catch (e: Exception) {
                Toast.makeText(context, "خطا در خواندن تصویر: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("تنظیمات فاکتور پیش‌فرض", fontSize = 16.sp, fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = { viewModel.navigateTo("EDITOR") }) {
                        Icon(imageVector = Icons.AutoMirrored.Default.ArrowBack, contentDescription = "بازگشت")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    titleContentColor = MaterialTheme.colorScheme.primary,
                    navigationIconContentColor = MaterialTheme.colorScheme.primary
                ),
                modifier = Modifier.height(52.dp),
                windowInsets = WindowInsets(0, 0, 0, 0)
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(MaterialTheme.colorScheme.background)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // General Info Text card
            item {
                Card(
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Info, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            "مشخصاتی که در این بخش وارد می‌کنید به صورت پیش‌فرض و همیشگی روی تمامی فاکتورهای جدید اعمال خواهد شد.",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            lineHeight = 18.sp
                        )
                    }
                }
            }

            // Shop / Seller card
            item {
                Card(
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                ) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text("مشخصات پیش‌فرض فروشگاه", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = MaterialTheme.colorScheme.primary)
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                        OutlinedTextField(
                            value = sName,
                            onValueChange = { sName = it },
                            label = { Text("نام فروشگاه / کارگاه") },
                            modifier = Modifier.fillMaxWidth()
                        )

                        OutlinedTextField(
                            value = sPhone,
                            onValueChange = { sPhone = it },
                            label = { Text("شماره تماس") },
                            modifier = Modifier.fillMaxWidth()
                        )

                        OutlinedTextField(
                            value = sAddress,
                            onValueChange = { sAddress = it },
                            label = { Text("آدرس پیش‌فرض") },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }

            // Headers card
            item {
                Card(
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                ) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text("تیتر‌های پیش‌فرض فاکتور", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = MaterialTheme.colorScheme.primary)
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                        OutlinedTextField(
                            value = title,
                            onValueChange = { title = it },
                            label = { Text("عنوان اصلی فاکتور") },
                            modifier = Modifier.fillMaxWidth()
                        )

                        OutlinedTextField(
                            value = sub,
                            onValueChange = { sub = it },
                            label = { Text("زیرعنوان و شعار فاکتور") },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }

            // Signatures and uploads card
            item {
                Card(
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                ) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        Text("مهر و امضای پیش‌فرض", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = MaterialTheme.colorScheme.primary)
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                        // Manager Sign
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedTextField(
                                value = mSign,
                                onValueChange = { mSign = it },
                                label = { Text("سمت / نام امضاء‌کننده اول (مثال: مهر و امضا مدیریت)") },
                                modifier = Modifier.fillMaxWidth()
                            )

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Button(
                                    onClick = { managerSignLauncher.launch("image/*") },
                                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondaryContainer, contentColor = MaterialTheme.colorScheme.onSecondaryContainer),
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier.height(36.dp)
                                ) {
                                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("بارگذاری عکس امضاء اول", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                }

                                if (mSignImgBase64.isNotBlank()) {
                                    OutlinedButton(
                                        onClick = { mSignImgBase64 = "" },
                                        colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.error),
                                        shape = RoundedCornerShape(8.dp),
                                        modifier = Modifier.height(36.dp)
                                    ) {
                                        Text("حذف تصویر", fontSize = 11.sp)
                                    }
                                }
                            }

                            if (mSignImgBase64.isNotBlank()) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(100.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(Color.White)
                                        .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(8.dp)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Base64Image(base64Str = mSignImgBase64, modifier = Modifier.fillMaxSize().padding(8.dp))
                                }
                            }
                        }

                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                        // Sales Sign
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedTextField(
                                value = sSign,
                                onValueChange = { sSign = it },
                                label = { Text("سمت / نام امضاء‌کننده دوم (مثال: مسئول واحد حسابداری)") },
                                modifier = Modifier.fillMaxWidth()
                            )

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Button(
                                    onClick = { salesSignLauncher.launch("image/*") },
                                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondaryContainer, contentColor = MaterialTheme.colorScheme.onSecondaryContainer),
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier.height(36.dp)
                                ) {
                                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("بارگذاری عکس امضاء دوم", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                }

                                if (sSignImgBase64.isNotBlank()) {
                                    OutlinedButton(
                                        onClick = { sSignImgBase64 = "" },
                                        colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.error),
                                        shape = RoundedCornerShape(8.dp),
                                        modifier = Modifier.height(36.dp)
                                    ) {
                                        Text("حذف تصویر", fontSize = 11.sp)
                                    }
                                }
                            }

                            if (sSignImgBase64.isNotBlank()) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(100.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(Color.White)
                                        .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(8.dp)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Base64Image(base64Str = sSignImgBase64, modifier = Modifier.fillMaxSize().padding(8.dp))
                                }
                            }
                        }
                    }
                }
            }

            // Action save button
            item {
                Button(
                    onClick = {
                        viewModel.updateDefaultSettings(
                            sName = sName,
                            sPhone = sPhone,
                            sAddress = sAddress,
                            title = title,
                            sub = sub,
                            mSign = mSign,
                            sSign = sSign,
                            mSignImg = mSignImgBase64,
                            sSignImg = sSignImgBase64
                        )
                    },
                    modifier = Modifier.fillMaxWidth().height(48.dp),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Default.CheckCircle, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("ذخیره نهایی تنظیمات پیش‌فرض", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            }

            // Quick Backup & Restore Card
            item {
                Card(
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Share, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("پشتیبان‌گیری آفلاین از کل اطلاعات", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                        Text(
                            "برای ایجاد فایل پشتیبان کامل یا بازیابی دیتای ذخیره‌شده، به بخش پشتیبان‌گیری در پیشخوان کاربری مراجعه نمایید:",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Button(
                                onClick = { viewModel.createAndShareFullBackup(context) },
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF10B981), contentColor = Color.White),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.weight(1f).height(38.dp)
                            ) {
                                Text("ایجاد فایل بکاپ (.qzb)", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }

                            OutlinedButton(
                                onClick = { viewModel.navigateTo("ACCOUNT") },
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.weight(1f).height(38.dp)
                            ) {
                                Text("مدیریت بکاپ‌ها", fontSize = 11.sp)
                            }
                        }
                    }
                }
            }

            item { Spacer(modifier = Modifier.height(20.dp)) }
        }
    }
}

@Composable
fun Base64Image(base64Str: String, modifier: Modifier = Modifier) {
    val bitmap = remember(base64Str) {
        try {
            val cleanStr = if (base64Str.contains("base64,")) {
                base64Str.substringAfter("base64,")
            } else {
                base64Str
            }
            val decodedBytes = android.util.Base64.decode(cleanStr, android.util.Base64.DEFAULT)
            android.graphics.BitmapFactory.decodeByteArray(decodedBytes, 0, decodedBytes.size)
        } catch (e: Exception) {
            null
        }
    }
    if (bitmap != null) {
        androidx.compose.foundation.Image(
            bitmap = bitmap.asImageBitmap(),
            contentDescription = "تصویر امضاء",
            modifier = modifier,
            contentScale = androidx.compose.ui.layout.ContentScale.Fit
        )
    } else {
        Text("خطا در بارگذاری تصویر", color = MaterialTheme.colorScheme.error)
    }
}
