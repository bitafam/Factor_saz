package com.example.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.CloudFileItem
import com.example.ui.viewmodel.InvoiceViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CloudFileManagerScreen(viewModel: InvoiceViewModel) {
    val context = LocalContext.current
    BackHandler {
        viewModel.navigateTo("SETTINGS")
    }

    LaunchedEffect(Unit) {
        viewModel.loadCloudFiles()
    }

    var selectedFolderForUpload by remember { mutableStateOf<String?>(null) }
    var fileToDelete by remember { mutableStateOf<CloudFileItem?>(null) }
    var expandedFolders by remember { mutableStateOf(setOf<String>()) }

    val uploadToFolderLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        val folder = selectedFolderForUpload
        if (uri != null && folder != null) {
            // Extract buyer and invoiceNo from folder name (invoices/خریدار_شماره)
            val parts = folder.removePrefix("invoices/").split("_")
            val buyer = parts.firstOrNull() ?: "مشتری"
            val invoiceNo = parts.drop(1).joinToString("_").ifBlank { "0" }
            viewModel.uploadAttachmentToInvoice(context, uri, "پیوست جدید")
            Toast.makeText(context, "در حال بارگذاری فایل جدید در پوشه $folder...", Toast.LENGTH_SHORT).show()
        }
        selectedFolderForUpload = null
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "مدیریت فایل‌ها و صندوقچه ابری",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimary
                        )
                        Text(
                            text = "آروان کلود (ArvanCloud Object Storage)",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.8f)
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = { viewModel.navigateTo("SETTINGS") }) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "بازگشت",
                            tint = MaterialTheme.colorScheme.onPrimary
                        )
                    }
                },
                actions = {
                    IconButton(onClick = { viewModel.loadCloudFiles() }) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "بروزرسانی لیست",
                            tint = MaterialTheme.colorScheme.onPrimary
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary
                )
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .background(MaterialTheme.colorScheme.background)
        ) {
            // Cloud Overview Header Card
            Card(
                shape = RoundedCornerShape(0.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.primaryContainer),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Place,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Text(
                                    text = "صندوقچه: ${viewModel.arvanBucket.ifBlank { "نامشخص" }}",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = viewModel.arvanEndpoint,
                                    fontSize = 10.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        // Status Badge
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = if (viewModel.arvanBucket.isNotBlank() && viewModel.arvanAccessKey.isNotBlank())
                                Color(0xFF10B981).copy(alpha = 0.15f)
                            else MaterialTheme.colorScheme.errorContainer
                        ) {
                            Text(
                                text = if (viewModel.arvanBucket.isNotBlank()) "متصل" else "پیکربندی نشده",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (viewModel.arvanBucket.isNotBlank()) Color(0xFF047857) else MaterialTheme.colorScheme.error,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                            )
                        }
                    }

                    // Total Count & Size
                    val totalFiles = viewModel.cloudFiles.size
                    val totalSizeBytes = viewModel.cloudFiles.sumOf { it.sizeBytes }
                    val totalSizeFormatted = if (totalSizeBytes > 1024 * 1024) {
                        String.format(java.util.Locale.US, "%.1f مگابایت", totalSizeBytes / (1024.0 * 1024.0))
                    } else {
                        String.format(java.util.Locale.US, "%.0f کیلوبایت", totalSizeBytes / 1024.0)
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "مجموع فایل‌ها: $totalFiles عدد ($totalSizeFormatted)",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        TextButton(
                            onClick = { viewModel.syncAllInvoicesToCloud(forceReupload = false) },
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)
                        ) {
                            Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("همگام‌سازی کل فاکتورها", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            // Search Bar
            OutlinedTextField(
                value = viewModel.cloudFileSearchQuery,
                onValueChange = { viewModel.cloudFileSearchQuery = it },
                placeholder = { Text("جستجو در پوشه‌ها، نام مشتری و فایل‌ها...") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
                trailingIcon = {
                    if (viewModel.cloudFileSearchQuery.isNotBlank()) {
                        IconButton(onClick = { viewModel.cloudFileSearchQuery = "" }) {
                            Icon(Icons.Default.Clear, contentDescription = "پاک کردن", modifier = Modifier.size(18.dp))
                        }
                    }
                },
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 10.dp)
            )

            // Content Area
            when {
                viewModel.isLoadingCloudFiles -> {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                            Text(
                                text = "در حال دریافت فهرست پوشه‌ها و فایل‌ها از صندوقچه ابری...",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                viewModel.cloudFilesError != null -> {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Card(
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                            shape = RoundedCornerShape(16.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier.padding(20.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Warning,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.error,
                                    modifier = Modifier.size(36.dp)
                                )
                                Text(
                                    text = "عدم امکان دریافت فایل‌ها از صندوقچه",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp,
                                    color = MaterialTheme.colorScheme.onErrorContainer
                                )
                                Text(
                                    text = viewModel.cloudFilesError ?: "",
                                    fontSize = 12.sp,
                                    textAlign = TextAlign.Center,
                                    color = MaterialTheme.colorScheme.onErrorContainer
                                )
                                Spacer(modifier = Modifier.height(6.dp))
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Button(
                                        onClick = { viewModel.loadCloudFiles() },
                                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                                    ) {
                                        Text("تلاش مجدد")
                                    }
                                    OutlinedButton(onClick = { viewModel.navigateTo("SETTINGS") }) {
                                        Text("تنظیمات صندوقچه")
                                    }
                                }
                            }
                        }
                    }
                }

                viewModel.cloudFiles.isEmpty() -> {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.List,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                                modifier = Modifier.size(64.dp)
                            )
                            Text(
                                text = "صندوقچه ابری شما خالی است",
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "با ذخیره فاکتورها یا کلیک روی دکمه زیر، فایل‌های HTML و تصاویر به صورت خودکار در صندوقچه بارگذاری خواهند شد.",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.padding(horizontal = 16.dp)
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Button(
                                onClick = { viewModel.syncAllInvoicesToCloud(forceReupload = false) },
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Icon(Icons.Default.Send, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("بارگذاری صف فاکتورها در صندوقچه", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }

                else -> {
                    // Filter and Group files by folder
                    val query = viewModel.cloudFileSearchQuery.trim()
                    val filteredFiles = if (query.isBlank()) {
                        viewModel.cloudFiles
                    } else {
                        viewModel.cloudFiles.filter {
                            it.fileName.contains(query, ignoreCase = true) ||
                            it.folderName.contains(query, ignoreCase = true) ||
                            it.key.contains(query, ignoreCase = true)
                        }
                    }

                    val groupedFolders = filteredFiles.groupBy { it.folderName.ifBlank { "پوشه اصلی" } }

                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(14.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        items(groupedFolders.keys.toList(), key = { it }) { folderName ->
                            val filesInFolder = groupedFolders[folderName] ?: emptyList()
                            val isExpanded = expandedFolders.contains(folderName) || query.isNotBlank()

                            Card(
                                shape = RoundedCornerShape(14.dp),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.padding(12.dp)) {
                                    // Folder Header
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable {
                                                expandedFolders = if (isExpanded) {
                                                    expandedFolders - folderName
                                                } else {
                                                    expandedFolders + folderName
                                                }
                                            }
                                            .padding(vertical = 4.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            modifier = Modifier.weight(1f)
                                        ) {
                                            Box(
                                                modifier = Modifier
                                                    .size(36.dp)
                                                    .clip(RoundedCornerShape(8.dp))
                                                    .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f)),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.List,
                                                    contentDescription = null,
                                                    tint = MaterialTheme.colorScheme.primary,
                                                    modifier = Modifier.size(20.dp)
                                                )
                                            }
                                            Spacer(modifier = Modifier.width(10.dp))
                                            Column {
                                                val cleanDisplayFolder = folderName.removePrefix("invoices/").replace("_", " ")
                                                Text(
                                                    text = cleanDisplayFolder,
                                                    fontSize = 13.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = MaterialTheme.colorScheme.onSurface,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                                Text(
                                                    text = "${filesInFolder.size} فایل موجود",
                                                    fontSize = 11.sp,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }
                                        }

                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            IconButton(
                                                onClick = {
                                                    selectedFolderForUpload = folderName
                                                    uploadToFolderLauncher.launch("image/*")
                                                },
                                                modifier = Modifier.size(32.dp)
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.Add,
                                                    contentDescription = "افزودن عکس به این پوشه",
                                                    tint = MaterialTheme.colorScheme.primary,
                                                    modifier = Modifier.size(18.dp)
                                                )
                                            }

                                            Icon(
                                                imageVector = if (isExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }

                                    // Folder Contents
                                    AnimatedVisibility(visible = isExpanded) {
                                        Column(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(top = 10.dp),
                                            verticalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))

                                            filesInFolder.forEach { fileItem ->
                                                CloudFileRow(
                                                    item = fileItem,
                                                    onOpenUrl = { url ->
                                                        try {
                                                            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
                                                            context.startActivity(intent)
                                                        } catch (e: Exception) {
                                                            Toast.makeText(context, "خطا در باز کردن لینک: ${e.message}", Toast.LENGTH_SHORT).show()
                                                        }
                                                    },
                                                    onCopyLink = { url ->
                                                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                                        clipboard.setPrimaryClip(ClipData.newPlainText("لینک فایل", url))
                                                        Toast.makeText(context, "لینک با موفقیت کپی شد", Toast.LENGTH_SHORT).show()
                                                    },
                                                    onDelete = {
                                                        fileToDelete = fileItem
                                                    }
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
    }

    // Delete confirmation dialog
    fileToDelete?.let { item ->
        AlertDialog(
            onDismissRequest = { fileToDelete = null },
            icon = { Icon(Icons.Default.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
            title = { Text("حذف فایل از صندوقچه ابری", fontSize = 14.sp, fontWeight = FontWeight.Bold) },
            text = {
                Text(
                    text = "آیا از حذف فایل «${item.fileName}» از صندوقچه ابری آروان کلود اطمینان دارید؟ این عملیات غیرقابل بازگشت است.",
                    fontSize = 12.sp,
                    lineHeight = 18.sp
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.deleteCloudFile(item)
                        fileToDelete = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("حذف قطعی", fontSize = 12.sp)
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { fileToDelete = null }) {
                    Text("انصراف", fontSize = 12.sp)
                }
            }
        )
    }
}

@Composable
fun CloudFileRow(
    item: CloudFileItem,
    onOpenUrl: (String) -> Unit,
    onCopyLink: (String) -> Unit,
    onDelete: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                // Icon based on file type
                val isHtml = item.isHtmlInvoice
                Box(
                    modifier = Modifier
                        .size(34.dp)
                        .clip(CircleShape)
                        .background(
                            if (isHtml) Color(0xFF00796B).copy(alpha = 0.15f)
                            else Color(0xFF0284C7).copy(alpha = 0.15f)
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Info,
                        contentDescription = null,
                        tint = if (isHtml) Color(0xFF00796B) else Color(0xFF0284C7),
                        modifier = Modifier.size(18.dp)
                    )
                }

                Spacer(modifier = Modifier.width(10.dp))

                Column {
                    Text(
                        text = if (item.isHtmlInvoice) "صفحه فاکتور آنلاین (HTML)" else item.fileName,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    val sizeKb = (item.sizeBytes / 1024L).coerceAtLeast(1L)
                    Text(
                        text = "$sizeKb کیلوبایت ${if (item.isWebpAttachment) "• تصویر فشرده WebP" else ""}",
                        fontSize = 10.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Row(
                horizontalArrangement = Arrangement.spacedBy(2.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Open Web Page / Image
                FilledTonalButton(
                    onClick = { onOpenUrl(item.url) },
                    shape = RoundedCornerShape(8.dp),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                    modifier = Modifier.height(30.dp)
                ) {
                    Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(13.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("مشاهده", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                }

                // Copy Link
                IconButton(
                    onClick = { onCopyLink(item.url) },
                    modifier = Modifier.size(30.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Share,
                        contentDescription = "کپی لینک",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(15.dp)
                    )
                }

                // Delete File
                IconButton(
                    onClick = onDelete,
                    modifier = Modifier.size(30.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = "حذف فایل",
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(15.dp)
                    )
                }
            }
        }
    }
}
