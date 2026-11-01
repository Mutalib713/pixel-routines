package com.mosman.routines.ui.assistant

import android.content.Context
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.PhotoLibrary
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
import com.mosman.routines.Store
import com.mosman.routines.drafts.RoutineConflictAnalyzer
import com.mosman.routines.drafts.RoutineDraft
import com.mosman.routines.ui.components.RoutineProposalCard
import com.mosman.routines.ui.lens.RoutineLensProcessor
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File
import java.util.UUID

data class ChatMessage(
    val id: String = UUID.randomUUID().toString(),
    val isUser: Boolean,
    val text: String,
    val imageBitmap: Bitmap? = null,
    val draft: RoutineDraft? = null,
    val timestamp: Long = System.currentTimeMillis(),
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AssistantScreenRedesigned(
    drafts: List<RoutineDraft>,
    onApproveDraft: (RoutineDraft) -> Unit,
    onRejectDraft: (RoutineDraft) -> Unit,
    onEditDraft: (RoutineDraft) -> Unit,
    onOpenLens: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()

    var input by remember { mutableStateOf("") }
    var attachedUri by remember { mutableStateOf<Uri?>(null) }
    var attachedBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var cameraTempUri by remember { mutableStateOf<Uri?>(null) }
    var showAttachmentSheet by remember { mutableStateOf(false) }
    var isThinking by remember { mutableStateOf(false) }

    val messages = remember {
        mutableStateListOf<ChatMessage>()
    }

    val suggestedPrompts = listOf(
        "Silence phone in lectures at KNUST",
        "Bedtime mode at 10:30 PM",
        "Turn on battery saver below 20%",
        "Study focus: Mute phone from 2pm to 5pm",
    )

    fun loadBitmap(uri: Uri): Bitmap? {
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

    // Photo picker launcher
    val galleryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            attachedUri = uri
            attachedBitmap = loadBitmap(uri)
        }
    }

    // Camera capture launcher
    val cameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture()
    ) { success ->
        if (success && cameraTempUri != null) {
            attachedUri = cameraTempUri
            attachedBitmap = loadBitmap(cameraTempUri!!)
        }
    }

    fun launchCamera() {
        try {
            val file = File.createTempFile("chat_snap_", ".jpg", context.cacheDir)
            val uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                file
            )
            cameraTempUri = uri
            cameraLauncher.launch(uri)
        } catch (_: Exception) {
        }
    }

    fun sendMessage(userText: String, imageUri: Uri?, bitmap: Bitmap?) {
        if (userText.isBlank() && imageUri == null) return

        val trimmedText = userText.trim()
        val userMsg = ChatMessage(
            isUser = true,
            text = if (trimmedText.isBlank() && imageUri != null) "Scanned schedule photo" else trimmedText,
            imageBitmap = bitmap,
        )
        messages.add(userMsg)

        input = ""
        attachedUri = null
        attachedBitmap = null
        isThinking = true

        scope.launch {
            listState.animateScrollToItem(messages.size - 1)

            // If image is attached, extract text with on-device OCR
            var ocrText: String? = null
            if (imageUri != null) {
                val ocrResult = RoutineLensProcessor.processImageUri(context, imageUri)
                ocrResult.onSuccess { draft ->
                    ocrText = draft.explanation
                }
            }

            // Parse intent using offline natural language parser
            val parsed = RoutineAIParser.parse(trimmedText, ocrText)

            // Check conflicts against existing saved routines
            val savedRoutines = Store.load(context)
            val conflicts = parsed.draft?.proposedRoutine?.let {
                RoutineConflictAnalyzer.analyze(it, savedRoutines)
            } ?: emptyList()

            val finalDraft = parsed.draft?.copy(
                warnings = conflicts.map { it.message }
            )

            delay(400) // Brief natural pause for thinking effect
            isThinking = false

            messages.add(
                ChatMessage(
                    isUser = false,
                    text = parsed.explanation,
                    draft = finalDraft,
                )
            )

            listState.animateScrollToItem(messages.size - 1)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primaryContainer),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                Icons.Filled.AutoAwesome,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp),
                            )
                        }
                        Spacer(Modifier.width(10.dp))
                        Column {
                            Text("Routine AI", fontSize = 17.sp, fontWeight = FontWeight.Bold)
                            Text(
                                "Local Intelligence · Offline",
                                fontSize = 11.sp,
                                color = Color(0xFF10B981),
                                fontWeight = FontWeight.Medium,
                            )
                        }
                    }
                },
                actions = {
                    if (messages.isNotEmpty()) {
                        IconButton(onClick = { messages.clear() }) {
                            Icon(
                                Icons.Filled.DeleteOutline,
                                contentDescription = "Clear conversation",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                },
            )
        },
        bottomBar = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 80.dp) // Leave room for 4-tab bottom navigation
                    .imePadding()
            ) {
                // Attached image thumbnail preview
                AnimatedVisibility(visible = attachedBitmap != null) {
                    Box(
                        modifier = Modifier
                            .padding(horizontal = 16.dp, vertical = 6.dp)
                            .size(72.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant),
                    ) {
                        attachedBitmap?.let { bmp ->
                            Image(
                                bitmap = bmp.asImageBitmap(),
                                contentDescription = "Attached photo",
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Crop,
                            )
                        }
                        IconButton(
                            onClick = {
                                attachedUri = null
                                attachedBitmap = null
                            },
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .size(22.dp)
                                .background(Color.Black.copy(alpha = 0.7f), CircleShape),
                        ) {
                            Icon(
                                Icons.Filled.Close,
                                contentDescription = "Remove attachment",
                                tint = Color.White,
                                modifier = Modifier.size(14.dp),
                            )
                        }
                    }
                }

                // Chat Input Bar (Rounded Pill Container)
                Surface(
                    color = MaterialTheme.colorScheme.surface,
                    tonalElevation = 4.dp,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        // Attachment Button (Camera / Gallery)
                        IconButton(
                            onClick = { showAttachmentSheet = true },
                            modifier = Modifier.size(42.dp),
                        ) {
                            Icon(
                                Icons.Filled.AddPhotoAlternate,
                                contentDescription = "Attach schedule or photo",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(24.dp),
                            )
                        }

                        // Text Field (Auto expanding)
                        OutlinedTextField(
                            value = input,
                            onValueChange = { input = it },
                            placeholder = {
                                Text(
                                    "Message Routine AI…",
                                    fontSize = 14.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                                )
                            },
                            shape = RoundedCornerShape(26.dp),
                            maxLines = 4,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = MaterialTheme.colorScheme.primary,
                                unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
                            ),
                            modifier = Modifier
                                .weight(1f)
                                .padding(horizontal = 6.dp),
                        )

                        // Send Button
                        val canSend = input.isNotBlank() || attachedUri != null
                        IconButton(
                            onClick = {
                                val textToSend = input
                                val uriToSend = attachedUri
                                val bmpToSend = attachedBitmap
                                sendMessage(textToSend, uriToSend, bmpToSend)
                            },
                            enabled = canSend,
                            modifier = Modifier
                                .size(42.dp)
                                .clip(CircleShape)
                                .background(
                                    if (canSend) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.surfaceVariant
                                ),
                        ) {
                            Icon(
                                Icons.AutoMirrored.Filled.Send,
                                contentDescription = "Send",
                                tint = if (canSend) MaterialTheme.colorScheme.onPrimary
                                else MaterialTheme.colorScheme.outline,
                                modifier = Modifier.size(18.dp),
                            )
                        }
                    }
                }
            }
        },
    ) { pad ->
        // Attachment Bottom Sheet
        if (showAttachmentSheet) {
            ModalBottomSheet(
                onDismissRequest = { showAttachmentSheet = false },
                containerColor = MaterialTheme.colorScheme.surface,
                shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 12.dp)
                        .padding(bottom = 24.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Text(
                        "Attach Schedule or Image",
                        fontWeight = FontWeight.Bold,
                        fontSize = 17.sp,
                    )
                    Text(
                        "Take a photo or choose an image of your timetable to turn it into an automated routine.",
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(4.dp))

                    ListItem(
                        headlineContent = { Text("Take photo with Camera", fontWeight = FontWeight.Medium) },
                        leadingContent = {
                            Box(
                                Modifier
                                    .size(40.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.primaryContainer),
                                contentAlignment = Alignment.Center,
                            ) {
                                Icon(Icons.Filled.CameraAlt, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            }
                        },
                        modifier = Modifier
                            .clip(RoundedCornerShape(16.dp))
                            .clickable {
                                showAttachmentSheet = false
                                launchCamera()
                            },
                    )

                    ListItem(
                        headlineContent = { Text("Choose from Gallery / Files", fontWeight = FontWeight.Medium) },
                        leadingContent = {
                            Box(
                                Modifier
                                    .size(40.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.secondaryContainer),
                                contentAlignment = Alignment.Center,
                            ) {
                                Icon(Icons.Filled.PhotoLibrary, contentDescription = null, tint = MaterialTheme.colorScheme.secondary)
                            }
                        },
                        modifier = Modifier
                            .clip(RoundedCornerShape(16.dp))
                            .clickable {
                                showAttachmentSheet = false
                                galleryLauncher.launch(
                                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                )
                            },
                    )
                }
            }
        }

        // Chat Message Thread or Empty State
        if (messages.isEmpty() && !isThinking) {
            // Modern AI Empty State
            Box(
                modifier = modifier
                    .fillMaxSize()
                    .padding(pad)
                    .padding(24.dp),
                contentAlignment = Alignment.Center,
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Box(
                        modifier = Modifier
                            .size(64.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primaryContainer),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            Icons.Filled.AutoAwesome,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(32.dp),
                        )
                    }
                    Spacer(Modifier.height(18.dp))
                    Text(
                        text = "How can I help you automate?",
                        fontWeight = FontWeight.Bold,
                        fontSize = 20.sp,
                        textAlign = TextAlign.Center,
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = "Type in plain words, or attach a photo of your schedule or lecture timetable.",
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        lineHeight = 18.sp,
                    )

                    Spacer(Modifier.height(28.dp))
                    Text(
                        text = "Suggestions to try:",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.align(Alignment.Start),
                    )
                    Spacer(Modifier.height(10.dp))

                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        suggestedPrompts.forEach { prompt ->
                            Surface(
                                shape = RoundedCornerShape(16.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                                border = androidx.compose.foundation.BorderStroke(
                                    1.dp,
                                    MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                                ),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        sendMessage(prompt, null, null)
                                    },
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Text(
                                        text = prompt,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Medium,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        modifier = Modifier.weight(1f),
                                    )
                                    Icon(
                                        Icons.AutoMirrored.Filled.Send,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(14.dp),
                                    )
                                }
                            }
                        }
                    }
                }
            }
        } else {
            // LazyColumn with messages
            LazyColumn(
                state = listState,
                modifier = modifier
                    .fillMaxSize()
                    .padding(pad),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                items(messages, key = { it.id }) { msg ->
                    if (msg.isUser) {
                        UserBubble(text = msg.text, bitmap = msg.imageBitmap)
                    } else {
                        AssistantBubble(
                            text = msg.text,
                            draft = msg.draft,
                            onApprove = { draft ->
                                onApproveDraft(draft)
                                messages.add(
                                    ChatMessage(
                                        isUser = false,
                                        text = "Done! “${draft.proposedRoutine?.name}” is now active and scheduled.",
                                    )
                                )
                                scope.launch {
                                    listState.animateScrollToItem(messages.size - 1)
                                }
                            },
                            onReject = { draft ->
                                onRejectDraft(draft)
                                messages.add(
                                    ChatMessage(
                                        isUser = false,
                                        text = "Proposal discarded. Let me know if you would like to adjust the settings.",
                                    )
                                )
                                scope.launch {
                                    listState.animateScrollToItem(messages.size - 1)
                                }
                            },
                            onEdit = onEditDraft,
                        )
                    }
                }

                // Thinking / Typing indicator
                if (isThinking) {
                    item {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(vertical = 4.dp),
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(28.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.primaryContainer),
                                contentAlignment = Alignment.Center,
                            ) {
                                Icon(
                                    Icons.Filled.AutoAwesome,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(16.dp),
                                )
                            }
                            Spacer(Modifier.width(10.dp))
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(16.dp))
                                    .background(MaterialTheme.colorScheme.surfaceVariant)
                                    .padding(horizontal = 14.dp, vertical = 8.dp),
                            ) {
                                Text(
                                    "Analyzing automation rules…",
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun UserBubble(text: String, bitmap: Bitmap?) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.End,
    ) {
        Column(
            horizontalAlignment = Alignment.End,
            modifier = Modifier.widthIn(max = 290.dp),
        ) {
            if (bitmap != null) {
                Box(
                    modifier = Modifier
                        .size(160.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant),
                ) {
                    Image(
                        bitmap = bitmap.asImageBitmap(),
                        contentDescription = "Uploaded image",
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop,
                    )
                }
                Spacer(Modifier.height(6.dp))
            }

            if (text.isNotBlank()) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(topStart = 18.dp, topEnd = 4.dp, bottomStart = 18.dp, bottomEnd = 18.dp))
                        .background(MaterialTheme.colorScheme.primary)
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                ) {
                    Text(
                        text = text,
                        color = MaterialTheme.colorScheme.onPrimary,
                        fontSize = 14.sp,
                        lineHeight = 20.sp,
                    )
                }
            }
        }
    }
}

@Composable
private fun AssistantBubble(
    text: String,
    draft: RoutineDraft?,
    onApprove: (RoutineDraft) -> Unit,
    onReject: (RoutineDraft) -> Unit,
    onEdit: (RoutineDraft) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Start,
        verticalAlignment = Alignment.Top,
    ) {
        Box(
            modifier = Modifier
                .padding(top = 4.dp)
                .size(28.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Filled.AutoAwesome,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(16.dp),
            )
        }
        Spacer(Modifier.width(10.dp))
        Column(
            modifier = Modifier.weight(1f),
            horizontalAlignment = Alignment.Start,
        ) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(topStart = 4.dp, topEnd = 18.dp, bottomStart = 18.dp, bottomEnd = 18.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .padding(horizontal = 14.dp, vertical = 10.dp),
            ) {
                Text(
                    text = text,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 14.sp,
                    lineHeight = 20.sp,
                )
            }

            if (draft != null) {
                Spacer(Modifier.height(10.dp))
                RoutineProposalCard(
                    draft = draft,
                    onApprove = onApprove,
                    onReject = onReject,
                    onEdit = onEdit,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}
