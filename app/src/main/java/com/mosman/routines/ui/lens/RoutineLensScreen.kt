package com.mosman.routines.ui.lens

import android.content.Context
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import com.mosman.routines.drafts.RoutineDraft
import com.mosman.routines.ui.components.RoutineProposalCard
import kotlinx.coroutines.launch
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RoutineLensScreen(
    onBack: () -> Unit,
    onDraftCreated: (RoutineDraft) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var detectedDraft by remember { mutableStateOf<RoutineDraft?>(null) }
    var selectedImageUri by remember { mutableStateOf<Uri?>(null) }
    var previewBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var isProcessing by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    var cameraTempUri by remember { mutableStateOf<Uri?>(null) }

    fun loadBitmapFromUri(uri: Uri): Bitmap? {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                val source = ImageDecoder.createSource(context.contentResolver, uri)
                ImageDecoder.decodeBitmap(source) { decoder, _, _ ->
                    decoder.isMutableRequired = true
                }
            } else {
                @Suppress("DEPRECATION")
                MediaStore.Images.Media.getBitmap(context.contentResolver, uri)
            }
        } catch (_: Exception) {
            null
        }
    }

    fun processUri(uri: Uri) {
        selectedImageUri = uri
        previewBitmap = loadBitmapFromUri(uri)
        isProcessing = true
        errorMessage = null

        scope.launch {
            val result = RoutineLensProcessor.processImageUri(context, uri)
            isProcessing = false
            result.onSuccess { draft ->
                detectedDraft = draft
            }.onFailure { err ->
                errorMessage = err.localizedMessage ?: "Failed to read timetable text."
            }
        }
    }

    // Modern Photo Picker launcher
    val galleryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            processUri(uri)
        }
    }

    // Camera launcher
    val cameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture()
    ) { success ->
        if (success) {
            cameraTempUri?.let { uri ->
                processUri(uri)
            }
        }
    }

    fun launchCamera() {
        try {
            val file = File.createTempFile("lens_snap_", ".jpg", context.cacheDir)
            val uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                file
            )
            cameraTempUri = uri
            cameraLauncher.launch(uri)
        } catch (e: Exception) {
            errorMessage = "Could not start camera: ${e.localizedMessage}"
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Routine Lens", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { pad ->
        Column(
            modifier = modifier
                .fillMaxSize()
                .padding(pad)
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            // Image Preview (if image selected or captured)
            if (previewBitmap != null) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(200.dp)
                        .clip(RoundedCornerShape(20.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant),
                    contentAlignment = Alignment.Center,
                ) {
                    Image(
                        bitmap = previewBitmap!!.asImageBitmap(),
                        contentDescription = "Captured image",
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop,
                    )

                    // Retake / Clear button
                    IconButton(
                        onClick = {
                            previewBitmap = null
                            selectedImageUri = null
                            detectedDraft = null
                            errorMessage = null
                        },
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(8.dp)
                            .clip(CircleShape)
                            .background(Color.Black.copy(alpha = 0.6f)),
                    ) {
                        Icon(Icons.Filled.Close, contentDescription = "Clear", tint = Color.White)
                    }
                }
            }

            // Processing indicator
            if (isProcessing) {
                Card(
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Row(
                        modifier = Modifier.padding(20.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(24.dp),
                            color = MaterialTheme.colorScheme.primary,
                            strokeWidth = 2.5.dp,
                        )
                        Column {
                            Text(
                                "Scanning timetable…",
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp,
                            )
                            Text(
                                "On-device text recognition reading schedule",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }

            // Error Message Banner
            if (errorMessage != null && !isProcessing) {
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f)),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(Modifier.padding(16.dp)) {
                        Text(
                            text = "Could not detect schedule",
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            fontSize = 14.sp,
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = errorMessage ?: "",
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            fontSize = 12.sp,
                        )
                    }
                }
            }

            // Detected Routine Proposal
            if (detectedDraft != null && !isProcessing) {
                Text(
                    text = "Routine Proposed",
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.fillMaxWidth(),
                )
                RoutineProposalCard(
                    draft = detectedDraft!!,
                    onApprove = { draft ->
                        onDraftCreated(draft)
                        onBack()
                    },
                    onReject = {
                        detectedDraft = null
                    },
                    onEdit = { draft ->
                        onDraftCreated(draft)
                        onBack()
                    },
                )
            } else if (previewBitmap == null) {
                // Initial State: Hero Card
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(240.dp)
                        .clip(RoundedCornerShape(24.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                        .border(
                            width = 2.dp,
                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.35f),
                            shape = RoundedCornerShape(24.dp),
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.padding(24.dp),
                    ) {
                        Box(
                            modifier = Modifier
                                .size(56.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primaryContainer),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                Icons.Filled.CameraAlt,
                                contentDescription = null,
                                modifier = Modifier.size(28.dp),
                                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                            )
                        }
                        Spacer(Modifier.height(14.dp))
                        Text(
                            text = "Scan or Import Timetable",
                            fontWeight = FontWeight.Bold,
                            fontSize = 17.sp,
                        )
                        Spacer(Modifier.height(6.dp))
                        Text(
                            text = "Take a photo of your lecture schedule, shift roster, or exam timetable to create an automated routine.",
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                        )
                    }
                }
            }

            // Action Buttons: Real Camera & Gallery
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Button(
                    onClick = { launchCamera() },
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.weight(1f),
                ) {
                    Icon(Icons.Filled.CameraAlt, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Take Photo")
                }

                OutlinedButton(
                    onClick = {
                        galleryLauncher.launch(
                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                        )
                    },
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.weight(1f),
                ) {
                    Icon(Icons.Filled.PhotoLibrary, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Upload Image")
                }
            }
        }
    }
}
