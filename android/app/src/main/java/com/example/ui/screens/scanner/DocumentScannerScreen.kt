package com.example.ui.screens.scanner

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.view.ViewGroup
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import coil.compose.AsyncImage
import com.example.data.local.entities.LocalScannedDocumentEntity
import com.example.data.model.MatterDto
import com.example.ui.theme.AmiriFontFamily
import com.example.ui.theme.ArabicSansFontFamily
import com.example.ui.theme.LocalHoyaamColors
import java.io.File
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.Executors

val SCAN_DOC_TYPES = listOf(
    "صحيفة دعوى" to "claim",
    "مذكرة دفاع" to "pleading",
    "تقرير خبير" to "expert_report",
    "حكم قضائي" to "judgment",
    "إعلان قضائي" to "summons",
    "عقد أو اتفاقية" to "contract",
    "توكيل رسمي" to "power_of_attorney",
    "أخرى" to "other"
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DocumentScannerScreen(
    matters: List<MatterDto> = emptyList(),
    preselectedMatterId: String? = null,
    onBackClick: () -> Unit,
    onSaveScannedDocument: (LocalScannedDocumentEntity, ByteArray?) -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val colors = LocalHoyaamColors.current

    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.CAMERA
            ) == PackageManager.PERMISSION_GRANTED
        )
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        hasCameraPermission = isGranted
    }

    var imageCapture: ImageCapture? by remember { mutableStateOf(null) }
    var cameraControl: CameraControl? by remember { mutableStateOf(null) }
    var isFlashOn by remember { mutableStateOf(false) }
    var lensFacing by remember { mutableStateOf(CameraSelector.LENS_FACING_BACK) }
    var capturedImageFile by remember { mutableStateOf<File?>(null) }
    var isCapturing by remember { mutableStateOf(false) }

    // Metadata for captured scan
    var docTitle by remember { mutableStateOf("") }
    var selectedDocType by remember { mutableStateOf(SCAN_DOC_TYPES[0].first) }
    var selectedMatterId by remember { mutableStateOf(preselectedMatterId ?: matters.firstOrNull()?.id) }
    var showMatterPicker by remember { mutableStateOf(false) }
    var isSaving by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        if (!hasCameraPermission) {
            permissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    Scaffold(
        modifier = Modifier
            .fillMaxSize()
            .testTag("document_scanner_screen"),
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = if (capturedImageFile == null) "الماسح الضوئي للمستندات" else "مراجعة المستند الممسوح",
                        fontFamily = AmiriFontFamily,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                },
                navigationIcon = {
                    IconButton(
                        onClick = {
                            if (capturedImageFile != null) {
                                capturedImageFile?.delete()
                                capturedImageFile = null
                            } else {
                                onBackClick()
                            }
                        }
                    ) {
                        Icon(
                            imageVector = Icons.Default.ArrowForward,
                            contentDescription = "رجوع",
                            tint = Color.White
                        )
                    }
                },
                actions = {
                    if (capturedImageFile == null && hasCameraPermission) {
                        IconButton(
                            onClick = {
                                isFlashOn = !isFlashOn
                                cameraControl?.enableTorch(isFlashOn)
                            }
                        ) {
                            Icon(
                                imageVector = if (isFlashOn) Icons.Default.FlashOn else Icons.Default.FlashOff,
                                contentDescription = "فلاش",
                                tint = if (isFlashOn) Color(0xFFFBBF24) else Color.White
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color(0xFF1E1E1E)
                )
            )
        },
        containerColor = Color.Black
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            if (!hasCameraPermission) {
                // Permission rationale view
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Box(
                        modifier = Modifier
                            .size(72.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF2E2E2E)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.CameraAlt,
                            contentDescription = null,
                            tint = colors.accent,
                            modifier = Modifier.size(36.dp)
                        )
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "يتطلب مسح المستندات إذن الكاميرا",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        fontFamily = AmiriFontFamily,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "يسمح هذا الإجراء للمحامي بالتقاط صور أوراق القضايا وحوافظ المستندات وحفظها رقمياً ومباشرة استخراج الوقائع منها.",
                        fontSize = 13.sp,
                        color = Color.LightGray,
                        textAlign = TextAlign.Center,
                        fontFamily = ArabicSansFontFamily
                    )
                    Spacer(modifier = Modifier.height(24.dp))
                    Button(
                        onClick = { permissionLauncher.launch(Manifest.permission.CAMERA) },
                        shape = RoundedCornerShape(100.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = colors.accent)
                    ) {
                        Text("منح الإذن الآن", fontFamily = ArabicSansFontFamily, fontWeight = FontWeight.Bold)
                    }
                }
            } else if (capturedImageFile == null) {
                // Live Camera Preview
                Box(modifier = Modifier.fillMaxSize()) {
                    AndroidView(
                        factory = { ctx ->
                            val previewView = PreviewView(ctx).apply {
                                layoutParams = ViewGroup.LayoutParams(
                                    ViewGroup.LayoutParams.MATCH_PARENT,
                                    ViewGroup.LayoutParams.MATCH_PARENT
                                )
                            }
                            val cameraProviderFuture = ProcessCameraProvider.getInstance(ctx)
                            cameraProviderFuture.addListener({
                                val cameraProvider = cameraProviderFuture.get()
                                val preview = Preview.Builder().build().also {
                                    it.setSurfaceProvider(previewView.surfaceProvider)
                                }
                                val capture = ImageCapture.Builder()
                                    .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                                    .build()
                                imageCapture = capture

                                val selector = CameraSelector.Builder()
                                    .requireLensFacing(lensFacing)
                                    .build()

                                try {
                                    cameraProvider.unbindAll()
                                    val cam = cameraProvider.bindToLifecycle(
                                        lifecycleOwner,
                                        selector,
                                        preview,
                                        capture
                                    )
                                    cameraControl = cam.cameraControl
                                } catch (e: Exception) {
                                    e.printStackTrace()
                                }
                            }, ContextCompat.getMainExecutor(ctx))
                            previewView
                        },
                        modifier = Modifier.fillMaxSize()
                    )

                    // Document Frame Bounding Overlay
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        val frameWidth = size.width * 0.85f
                        val frameHeight = frameWidth * 1.414f // A4 proportion
                        val left = (size.width - frameWidth) / 2f
                        val top = (size.height - frameHeight) / 2f

                        // Draw darkened exterior
                        drawRect(
                            color = Color.Black.copy(alpha = 0.45f)
                        )

                        // Clear viewfinder
                        drawRoundRect(
                            color = Color.Transparent,
                            topLeft = Offset(left, top),
                            size = Size(frameWidth, frameHeight),
                            cornerRadius = CornerRadius(24f, 24f),
                            blendMode = androidx.compose.ui.graphics.BlendMode.Clear
                        )

                        // Outline frame
                        drawRoundRect(
                            color = Color.White.copy(alpha = 0.8f),
                            topLeft = Offset(left, top),
                            size = Size(frameWidth, frameHeight),
                            cornerRadius = CornerRadius(24f, 24f),
                            style = Stroke(width = 4f)
                        )
                    }

                    // Scanner Instructions & Tips
                    Column(
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .padding(top = 24.dp, start = 16.dp, end = 16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Surface(
                            shape = RoundedCornerShape(100.dp),
                            color = Color.Black.copy(alpha = 0.65f),
                            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.2f))
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.CropFree,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "ضع حدود المستند أو الصحيفة داخل الإطار",
                                    color = Color.White,
                                    fontSize = 12.sp,
                                    fontFamily = ArabicSansFontFamily
                                )
                            }
                        }
                    }

                    // Bottom Shutter Bar
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .align(Alignment.BottomCenter)
                            .background(Color.Black.copy(alpha = 0.6f))
                            .padding(vertical = 24.dp, horizontal = 32.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Switch Camera
                        IconButton(
                            onClick = {
                                lensFacing = if (lensFacing == CameraSelector.LENS_FACING_BACK) {
                                    CameraSelector.LENS_FACING_FRONT
                                } else {
                                    CameraSelector.LENS_FACING_BACK
                                }
                            },
                            modifier = Modifier
                                .size(48.dp)
                                .clip(CircleShape)
                                .background(Color(0xFF2A2A2A))
                        ) {
                            Icon(
                                imageVector = Icons.Default.Cameraswitch,
                                contentDescription = "تبديل الكاميرا",
                                tint = Color.White
                            )
                        }

                        // Shutter Button
                        Box(
                            modifier = Modifier
                                .size(76.dp)
                                .clip(CircleShape)
                                .border(4.dp, Color.White, CircleShape)
                                .padding(6.dp)
                                .clip(CircleShape)
                                .background(if (isCapturing) Color.Gray else Color.White)
                                .clickable(enabled = !isCapturing) {
                                    val capture = imageCapture ?: return@clickable
                                    isCapturing = true
                                    val photoFile = File(
                                        context.cacheDir,
                                        "scan_${SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())}.jpg"
                                    )
                                    val outputOptions = ImageCapture.OutputFileOptions.Builder(photoFile).build()
                                    capture.takePicture(
                                        outputOptions,
                                        Executors.newSingleThreadExecutor(),
                                        object : ImageCapture.OnImageSavedCallback {
                                            override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
                                                isCapturing = false
                                                capturedImageFile = photoFile
                                                if (docTitle.isBlank()) {
                                                    docTitle = "مستند ماسح ${SimpleDateFormat("yyyy/MM/dd HH:mm", Locale.US).format(Date())}"
                                                }
                                            }

                                            override fun onError(exception: ImageCaptureException) {
                                                isCapturing = false
                                                exception.printStackTrace()
                                            }
                                        }
                                    )
                                }
                                .testTag("shutter_button"),
                            contentAlignment = Alignment.Center
                        ) {
                            if (isCapturing) {
                                CircularProgressIndicator(color = Color.Black, modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                            } else {
                                Icon(
                                    imageVector = Icons.Default.CameraAlt,
                                    contentDescription = "التقاط",
                                    tint = Color.Black,
                                    modifier = Modifier.size(28.dp)
                                )
                            }
                        }

                        // Cancel / Close
                        IconButton(
                            onClick = onBackClick,
                            modifier = Modifier
                                .size(48.dp)
                                .clip(CircleShape)
                                .background(Color(0xFF2A2A2A))
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "إلغاء",
                                tint = Color.White
                            )
                        }
                    }
                }
            } else {
                // Post Capture Review & Save
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(colors.bg)
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    // Preview Card
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        shape = RoundedCornerShape(20.dp),
                        colors = CardDefaults.cardColors(containerColor = colors.card),
                        border = BorderStroke(1.dp, colors.border)
                    ) {
                        Box(modifier = Modifier.fillMaxSize()) {
                            AsyncImage(
                                model = capturedImageFile,
                                contentDescription = "معاينة المستند الممسوح",
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(8.dp)
                                    .clip(RoundedCornerShape(14.dp))
                            )

                            Surface(
                                modifier = Modifier
                                    .align(Alignment.TopEnd)
                                    .padding(14.dp),
                                shape = RoundedCornerShape(100.dp),
                                color = colors.good.copy(alpha = 0.9f),
                                contentColor = Color.White
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(Icons.Default.CheckCircle, contentDescription = null, modifier = Modifier.size(14.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("تم المسح بدقة عالية", fontSize = 11.sp, fontFamily = ArabicSansFontFamily)
                                }
                            }
                        }
                    }

                    // Metadata Inputs
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(20.dp),
                        colors = CardDefaults.cardColors(containerColor = colors.card),
                        border = BorderStroke(1.dp, colors.border)
                    ) {
                        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            // Title
                            OutlinedTextField(
                                value = docTitle,
                                onValueChange = { docTitle = it },
                                label = { Text("عنوان أو تسمية المستند") },
                                singleLine = true,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("scan_title_input"),
                                shape = RoundedCornerShape(12.dp),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = colors.accent,
                                    focusedLabelColor = colors.accent
                                )
                            )

                            // Document Type Chips
                            Text(
                                text = "تصنيف الوثيقة:",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = colors.text,
                                fontFamily = ArabicSansFontFamily
                            )

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                SCAN_DOC_TYPES.take(3).forEach { (label, _) ->
                                    val isSelected = selectedDocType == label
                                    Surface(
                                        shape = RoundedCornerShape(100.dp),
                                        color = if (isSelected) colors.heroBg else colors.inset,
                                        border = BorderStroke(1.dp, if (isSelected) colors.accent else colors.border),
                                        modifier = Modifier.clickable { selectedDocType = label }
                                    ) {
                                        Text(
                                            text = label,
                                            fontSize = 11.sp,
                                            color = if (isSelected) colors.heroText else colors.text2,
                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                            fontFamily = ArabicSansFontFamily
                                        )
                                    }
                                }
                            }

                            // Matter Target Picker
                            if (matters.isNotEmpty()) {
                                val selectedMatter = matters.find { it.id == selectedMatterId }
                                OutlinedCard(
                                    onClick = { showMatterPicker = true },
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(12.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column {
                                            Text("القضية المستهدفة:", fontSize = 10.sp, color = colors.textDim)
                                            Text(
                                                text = selectedMatter?.matterLabel ?: "اختر قضية لحفظ المستند بها",
                                                fontSize = 13.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = colors.text,
                                                fontFamily = ArabicSansFontFamily
                                            )
                                        }
                                        Icon(Icons.Default.ArrowDropDown, contentDescription = null, tint = colors.text2)
                                    }
                                }
                            }
                        }
                    }

                    // Action Buttons
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        OutlinedButton(
                            onClick = {
                                capturedImageFile?.delete()
                                capturedImageFile = null
                            },
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp),
                            shape = RoundedCornerShape(100.dp),
                            border = BorderStroke(1.dp, colors.border)
                        ) {
                            Text("إعادة المسح", color = colors.text2, fontFamily = ArabicSansFontFamily)
                        }

                        Button(
                            onClick = {
                                val file = capturedImageFile ?: return@Button
                                isSaving = true
                                val scanEntity = LocalScannedDocumentEntity(
                                    id = UUID.randomUUID().toString(),
                                    matterId = selectedMatterId,
                                    title = if (docTitle.isNotBlank()) docTitle else "مستند ممسوح",
                                    docType = selectedDocType,
                                    imagePath = file.absolutePath,
                                    ocrPreviewText = "تم مسح المستند «$docTitle» بنجاح ويجري التحقق من النص والبيانات الإجرائية.",
                                    createdAt = System.currentTimeMillis()
                                )
                                val bytes = try { file.readBytes() } catch (e: Exception) { null }
                                onSaveScannedDocument(scanEntity, bytes)
                                isSaving = false
                            },
                            modifier = Modifier
                                .weight(1.5f)
                                .height(48.dp)
                                .testTag("save_scanned_doc_button"),
                            shape = RoundedCornerShape(100.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = colors.accent)
                        ) {
                            if (isSaving) {
                                CircularProgressIndicator(color = Color.White, modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                            } else {
                                Icon(Icons.Default.Save, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("حفظ بالملف وقاعدة البيانات", fontFamily = ArabicSansFontFamily, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }
    }

    // Matter Picker Dialog
    if (showMatterPicker) {
        AlertDialog(
            onDismissRequest = { showMatterPicker = false },
            title = { Text("اختر القضية لربط المستند", fontFamily = AmiriFontFamily, fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    matters.forEach { m ->
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    selectedMatterId = m.id
                                    showMatterPicker = false
                                },
                            shape = RoundedCornerShape(8.dp),
                            color = if (selectedMatterId == m.id) colors.heroBg else colors.inset
                        ) {
                            Text(
                                text = m.matterLabel,
                                fontSize = 13.sp,
                                modifier = Modifier.padding(10.dp),
                                fontFamily = ArabicSansFontFamily
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showMatterPicker = false }) {
                    Text("إغلاق")
                }
            }
        )
    }
}
