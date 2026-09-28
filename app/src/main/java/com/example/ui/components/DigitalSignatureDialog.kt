package com.example.ui.components

import android.graphics.Bitmap
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import java.io.ByteArrayOutputStream

data class SignaturePoint(val x: Float, val y: Float)

data class SignatureStroke(
    val points: List<SignaturePoint>,
    val color: Color,
    val strokeWidth: Float
)

/**
 * High-precision drawing canvas for digital signatures.
 * Supports smooth Bezier curves, anti-aliased output, custom stroke widths, and colors.
 */
@Composable
fun DigitalSignaturePad(
    modifier: Modifier = Modifier,
    selectedColor: Color,
    selectedStrokeWidth: Float,
    strokes: List<SignatureStroke>,
    onAddStroke: (SignatureStroke) -> Unit,
    onCanvasSizeChanged: (IntSize) -> Unit = {}
) {
    var currentPoints = remember { mutableStateListOf<SignaturePoint>() }

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(Color.White)
            .border(2.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(12.dp))
            .onSizeChanged { onCanvasSizeChanged(it) }
            .pointerInput(selectedColor, selectedStrokeWidth) {
                detectDragGestures(
                    onDragStart = { offset ->
                        currentPoints.clear()
                        currentPoints.add(SignaturePoint(offset.x, offset.y))
                    },
                    onDrag = { change, _ ->
                        change.consume()
                        currentPoints.add(SignaturePoint(change.position.x, change.position.y))
                    },
                    onDragEnd = {
                        if (currentPoints.isNotEmpty()) {
                            onAddStroke(
                                SignatureStroke(
                                    points = currentPoints.toList(),
                                    color = selectedColor,
                                    strokeWidth = selectedStrokeWidth
                                )
                            )
                            currentPoints.clear()
                        }
                    },
                    onDragCancel = {
                        if (currentPoints.isNotEmpty()) {
                            onAddStroke(
                                SignatureStroke(
                                    points = currentPoints.toList(),
                                    color = selectedColor,
                                    strokeWidth = selectedStrokeWidth
                                )
                            )
                            currentPoints.clear()
                        }
                    }
                )
            }
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            // Draw subtle watermark / baseline guide
            val baselineY = size.height * 0.78f
            drawLine(
                color = Color(0x301E3A8A),
                start = Offset(24f, baselineY),
                end = Offset(size.width - 24f, baselineY),
                strokeWidth = 2f,
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(12f, 10f), 0f)
            )

            fun renderPoints(points: List<SignaturePoint>, strokeColor: Color, sWidth: Float) {
                if (points.isEmpty()) return
                if (points.size == 1) {
                    drawCircle(
                        color = strokeColor,
                        radius = sWidth / 2f,
                        center = Offset(points[0].x, points[0].y)
                    )
                    return
                }

                val path = Path()
                path.moveTo(points[0].x, points[0].y)
                for (i in 1 until points.size) {
                    val p0 = points[i - 1]
                    val p1 = points[i]
                    val midX = (p0.x + p1.x) / 2f
                    val midY = (p0.y + p1.y) / 2f
                    path.quadraticTo(p0.x, p0.y, midX, midY)
                }
                path.lineTo(points.last().x, points.last().y)

                drawPath(
                    path = path,
                    color = strokeColor,
                    style = Stroke(
                        width = sWidth,
                        cap = StrokeCap.Round,
                        join = StrokeJoin.Round
                    )
                )
            }

            strokes.forEach { stroke ->
                renderPoints(stroke.points, stroke.color, stroke.strokeWidth)
            }

            if (currentPoints.isNotEmpty()) {
                renderPoints(currentPoints, selectedColor, selectedStrokeWidth)
            }
        }

        if (strokes.isEmpty() && currentPoints.isEmpty()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Edit,
                    contentDescription = null,
                    tint = Color(0xFF94A3B8),
                    modifier = Modifier.size(36.dp)
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "با انگشت یا قلم در این کادر امضا کنید",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    color = Color(0xFF64748B)
                )
                Text(
                    text = "محل خط‌چین خط پایه امضا می‌باشد",
                    fontSize = 10.sp,
                    color = Color(0xFF94A3B8)
                )
            }
        }
    }
}

/**
 * Converts signature strokes to a crisp transparent PNG Base64 string.
 */
fun exportSignatureStrokesToBase64(
    strokes: List<SignatureStroke>,
    canvasWidth: Int,
    canvasHeight: Int
): String? {
    if (strokes.isEmpty()) return null

    val w = if (canvasWidth > 100) canvasWidth else 900
    val h = if (canvasHeight > 100) canvasHeight else 450

    val bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
    val canvas = android.graphics.Canvas(bitmap)
    canvas.drawColor(android.graphics.Color.TRANSPARENT, android.graphics.PorterDuff.Mode.CLEAR)

    val paint = android.graphics.Paint().apply {
        isAntiAlias = true
        isDither = true
        style = android.graphics.Paint.Style.STROKE
        strokeJoin = android.graphics.Paint.Join.ROUND
        strokeCap = android.graphics.Paint.Cap.ROUND
    }

    var minX = w.toFloat()
    var minY = h.toFloat()
    var maxX = 0f
    var maxY = 0f

    for (stroke in strokes) {
        paint.color = stroke.color.toArgb()
        paint.strokeWidth = stroke.strokeWidth

        val points = stroke.points
        if (points.isEmpty()) continue

        for (pt in points) {
            if (pt.x < minX) minX = pt.x
            if (pt.y < minY) minY = pt.y
            if (pt.x > maxX) maxX = pt.x
            if (pt.y > maxY) maxY = pt.y
        }

        if (points.size == 1) {
            val fillPaint = android.graphics.Paint(paint).apply { style = android.graphics.Paint.Style.FILL }
            canvas.drawCircle(points[0].x, points[0].y, stroke.strokeWidth / 2f, fillPaint)
            continue
        }

        val path = android.graphics.Path()
        path.moveTo(points[0].x, points[0].y)
        for (i in 1 until points.size) {
            val p0 = points[i - 1]
            val p1 = points[i]
            val midX = (p0.x + p1.x) / 2f
            val midY = (p0.y + p1.y) / 2f
            path.quadTo(p0.x, p0.y, midX, midY)
        }
        path.lineTo(points.last().x, points.last().y)
        canvas.drawPath(path, paint)
    }

    // Crop bounding box with aesthetic padding
    val padding = 24
    val cropX = (minX.toInt() - padding).coerceAtLeast(0)
    val cropY = (minY.toInt() - padding).coerceAtLeast(0)
    val cropW = ((maxX - minX).toInt() + padding * 2).coerceAtMost(w - cropX)
    val cropH = ((maxY - minY).toInt() + padding * 2).coerceAtMost(h - cropY)

    val finalBitmap = if (cropW > 20 && cropH > 20) {
        try {
            Bitmap.createBitmap(bitmap, cropX, cropY, cropW, cropH)
        } catch (e: Exception) {
            bitmap
        }
    } else {
        bitmap
    }

    val outputStream = ByteArrayOutputStream()
    finalBitmap.compress(Bitmap.CompressFormat.PNG, 100, outputStream)
    val bytes = outputStream.toByteArray()
    val base64 = android.util.Base64.encodeToString(bytes, android.util.Base64.NO_WRAP)
    return "data:image/png;base64,$base64"
}

/**
 * Large, dedicated full-featured dialog for drawing digital signatures.
 * Includes pen color palette, pen thickness controls, undo/clear, and option to save as default.
 */
@Composable
fun LargeDigitalSignatureDialog(
    title: String = "امضای دیجیتال دستی",
    initialSaveAsDefault: Boolean = false,
    showSaveAsDefaultCheckbox: Boolean = true,
    onDismiss: () -> Unit,
    onConfirm: (signatureBase64: String, saveAsDefault: Boolean) -> Unit
) {
    val strokes = remember { mutableStateListOf<SignatureStroke>() }
    var selectedColor by remember { mutableStateOf(Color(0xFF0D47A1)) } // Navy Blue by default
    var selectedStrokeWidth by remember { mutableStateOf(6f) }
    var saveAsDefault by remember { mutableStateOf(initialSaveAsDefault) }
    var canvasSize by remember { mutableStateOf(IntSize.Zero) }

    val colorOptions = listOf(
        Pair("سرمه‌ای", Color(0xFF0D47A1)),
        Pair("مشکی", Color(0xFF000000)),
        Pair("قرمز مهر", Color(0xFFC62828))
    )

    val strokeWidthOptions = listOf(
        Pair("باریک", 4f),
        Pair("استاندارد", 6f),
        Pair("ضخیم", 10f)
    )

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnBackPress = true,
            dismissOnClickOutside = false
        )
    ) {
        Card(
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 10.dp),
            modifier = Modifier
                .fillMaxWidth(0.95f)
                .fillMaxHeight(0.85f)
                .padding(8.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Header
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
                                imageVector = Icons.Default.Edit,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = title,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "امضای باکیفیت و شفاف جهت درج روی فاکتور و PDF",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "بستن",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                // Toolbar: Color selection, stroke width, and Undo/Clear
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Color Palette
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("رنگ قلم:", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        colorOptions.forEach { (name, color) ->
                            val isSelected = selectedColor == color
                            Box(
                                modifier = Modifier
                                    .size(28.dp)
                                    .clip(CircleShape)
                                    .background(color)
                                    .border(
                                        width = if (isSelected) 3.dp else 1.dp,
                                        color = if (isSelected) MaterialTheme.colorScheme.primary else Color.Gray.copy(alpha = 0.5f),
                                        shape = CircleShape
                                    )
                                    .clickable { selectedColor = color },
                                contentAlignment = Alignment.Center
                            ) {
                                if (isSelected) {
                                    Icon(
                                        imageVector = Icons.Default.Check,
                                        contentDescription = name,
                                        tint = Color.White,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                        }
                    }

                    // Undo and Clear Actions
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedButton(
                            onClick = {
                                if (strokes.isNotEmpty()) {
                                    strokes.removeAt(strokes.size - 1)
                                }
                            },
                            enabled = strokes.isNotEmpty(),
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                            modifier = Modifier.height(32.dp)
                        ) {
                            Icon(Icons.Default.Refresh, contentDescription = "واگرد", modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("واگرد", fontSize = 11.sp)
                        }

                        OutlinedButton(
                            onClick = { strokes.clear() },
                            enabled = strokes.isNotEmpty(),
                            shape = RoundedCornerShape(8.dp),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                            border = BorderStroke(1.dp, if (strokes.isNotEmpty()) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.outlineVariant),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                            modifier = Modifier.height(32.dp)
                        ) {
                            Icon(Icons.Default.Delete, contentDescription = "پاک کردن", modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("پاک کردن", fontSize = 11.sp)
                        }
                    }
                }

                // Stroke Width Chips
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("ضخامت قلم:", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    strokeWidthOptions.forEach { (label, widthVal) ->
                        FilterChip(
                            selected = selectedStrokeWidth == widthVal,
                            onClick = { selectedStrokeWidth = widthVal },
                            label = { Text(label, fontSize = 10.sp) },
                            modifier = Modifier.height(28.dp)
                        )
                    }
                }

                // Large Canvas Pad Area
                DigitalSignaturePad(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    selectedColor = selectedColor,
                    selectedStrokeWidth = selectedStrokeWidth,
                    strokes = strokes,
                    onAddStroke = { strokes.add(it) },
                    onCanvasSizeChanged = { canvasSize = it }
                )

                // Save as Default Checkbox
                if (showSaveAsDefaultCheckbox) {
                    Card(
                        shape = RoundedCornerShape(10.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
                        ),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { saveAsDefault = !saveAsDefault }
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Checkbox(
                                checked = saveAsDefault,
                                onCheckedChange = { saveAsDefault = it },
                                colors = CheckboxDefaults.colors(checkedColor = MaterialTheme.colorScheme.primary)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Column {
                                Text(
                                    text = "ذخیره به عنوان امضای پیش‌فرض در تنظیمات",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = "این امضا به طور خودکار روی تمامی فاکتورهای جدید قرار خواهد گرفت.",
                                    fontSize = 10.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }

                // Bottom Action Buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedButton(
                        onClick = onDismiss,
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier
                            .weight(1f)
                            .height(46.dp)
                    ) {
                        Text("انصراف", fontSize = 12.sp)
                    }

                    Button(
                        onClick = {
                            if (strokes.isNotEmpty()) {
                                val base64 = exportSignatureStrokesToBase64(
                                    strokes = strokes,
                                    canvasWidth = canvasSize.width,
                                    canvasHeight = canvasSize.height
                                )
                                if (base64 != null) {
                                    onConfirm(base64, saveAsDefault)
                                }
                            }
                        },
                        enabled = strokes.isNotEmpty(),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary
                        ),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier
                            .weight(2f)
                            .height(46.dp)
                    ) {
                        Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("تأیید و ثبت امضای دیجیتال", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}
