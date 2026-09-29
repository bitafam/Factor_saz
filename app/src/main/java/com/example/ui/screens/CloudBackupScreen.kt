package com.example.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
fun CloudBackupScreen(viewModel: InvoiceViewModel) {
    val context = LocalContext.current
    var selectedTab by remember { mutableIntStateOf(0) } // 0: Single Backups, 1: Full Backups

    BackHandler {
        viewModel.navigateTo("SETTINGS")
    }

    LaunchedEffect(Unit) {
        viewModel.loadCloudBackups()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("مدیریت پشتیبان‌های ابری آروان", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                        Text("ذخیره و مدیریت بکاپ‌های تکی و کلی فاکتورها در صندوقچه", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                },
                navigationIcon = {
                    IconButton(onClick = { viewModel.navigateTo("SETTINGS") }) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "بازگشت")
                    }
                },
                actions = {
                    IconButton(onClick = { viewModel.loadCloudBackups() }) {
                        Icon(Icons.Default.Refresh, contentDescription = "بارگذاری مجدد")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface)
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = {
                    viewModel.createAndUploadFullCloudBackup()
                },
                icon = { Icon(Icons.Default.Send, contentDescription = null) },
                text = { Text("تهیه بکاپ کلی جدید در ابر", fontWeight = FontWeight.Bold, fontSize = 12.sp) },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(MaterialTheme.colorScheme.background)
        ) {
            // Tabs Row
            TabRow(
                selectedTabIndex = selectedTab,
                containerColor = MaterialTheme.colorScheme.surface,
                contentColor = MaterialTheme.colorScheme.primary
            ) {
                Tab(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    text = { Text("بکاپ‌های تکی فاکتورها", fontWeight = FontWeight.Bold, fontSize = 12.sp) },
                    icon = { Icon(Icons.Default.Info, contentDescription = null, modifier = Modifier.size(18.dp)) }
                )
                Tab(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    text = { Text("بکاپ‌های کلی دیتابیس", fontWeight = FontWeight.Bold, fontSize = 12.sp) },
                    icon = { Icon(Icons.Default.List, contentDescription = null, modifier = Modifier.size(18.dp)) }
                )
            }

            if (viewModel.isCloudBackupsLoading) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                        Text("در حال بارگذاری فایل‌های پشتیبان از صندوقچه ابری...", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            } else {
                val allBackups = viewModel.cloudBackupsList
                val singleBackups = allBackups.filter { it.key.startsWith("backups/single/") }
                val fullBackups = allBackups.filter { it.key.startsWith("backups/full/") }
                val currentList = if (selectedTab == 0) singleBackups else fullBackups

                if (currentList.isEmpty()) {
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
                                imageVector = Icons.Default.Info,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                                modifier = Modifier.size(54.dp)
                            )
                            Text(
                                text = if (selectedTab == 0) "هیچ بکاپ تکی در صندوقچه ثبت نشده است" else "هیچ بکاپ کلی در صندوقچه ثبت نشده است",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "با ذخیره فاکتورها یا کلیک روی دکمه تهیه بکاپ کلی، فایل‌های پشتیبان به صورت خودکار در صندوقچه ابری آروان ذخیره می‌شوند.",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        items(currentList, key = { it.key }) { item ->
                            CloudBackupCard(
                                item = item,
                                onRestore = {
                                    viewModel.restoreCloudBackup(item)
                                },
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
                                    val clip = ClipData.newPlainText("Cloud Backup Link", url)
                                    clipboard.setPrimaryClip(clip)
                                    Toast.makeText(context, "لینک در کلیپ‌بورد کپی شد", Toast.LENGTH_SHORT).show()
                                },
                                onDelete = {
                                    viewModel.deleteCloudBackup(item)
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun CloudBackupCard(
    item: CloudFileItem,
    onRestore: () -> Unit,
    onOpenUrl: (String) -> Unit,
    onCopyLink: (String) -> Unit,
    onDelete: () -> Unit
) {
    var showDeleteConfirm by remember { mutableStateOf(false) }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("حذف فایل پشتیبان ابری", fontWeight = FontWeight.Bold, fontSize = 14.sp) },
            text = { Text("آیا از حذف این فایل پشتیبان از صندوقچه ابری اطمینان دارید؟", fontSize = 12.sp) },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDeleteConfirm = false
                        onDelete()
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("حذف")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) {
                    Text("انصراف")
                }
            }
        )
    }

    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                    Box(
                        modifier = Modifier
                            .size(34.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primaryContainer),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Place,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            text = item.fileName,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        val sizeKb = (item.sizeBytes / 1024L).coerceAtLeast(1L)
                        Text(
                            text = "حجم: $sizeKb کیلوبایت • تاریخ: ${item.lastModified.ifBlank { "ثبت شده" }}",
                            fontSize = 10.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Button(
                    onClick = onRestore,
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primaryContainer, contentColor = MaterialTheme.colorScheme.onPrimaryContainer),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp),
                    modifier = Modifier.height(32.dp)
                ) {
                    Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(14.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("بازیابی در برنامه", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }

                Row(horizontalArrangement = Arrangement.spacedBy(2.dp), verticalAlignment = Alignment.CenterVertically) {
                    FilledTonalButton(
                        onClick = { onOpenUrl(item.url) },
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                        modifier = Modifier.height(32.dp)
                    ) {
                        Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(13.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("مشاهده", fontSize = 10.sp)
                    }

                    IconButton(
                        onClick = { onCopyLink(item.url) },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(Icons.Default.Share, contentDescription = "کپی لینک", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(15.dp))
                    }

                    IconButton(
                        onClick = { showDeleteConfirm = true },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(Icons.Default.Delete, contentDescription = "حذف فایل", tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(15.dp))
                    }
                }
            }
        }
    }
}
