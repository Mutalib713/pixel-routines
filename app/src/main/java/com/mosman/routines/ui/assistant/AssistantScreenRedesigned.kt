package com.mosman.routines.ui.assistant

import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateContentSize
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
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.AddComment
import androidx.compose.material.icons.outlined.ArrowDropDown
import androidx.compose.material.icons.outlined.AutoMode
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Cloud
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.PauseCircle
import androidx.compose.material.icons.outlined.PhoneAndroid
import androidx.compose.material.icons.outlined.PhotoCamera
import androidx.compose.material.icons.outlined.PlayCircle
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import com.mosman.routines.Ic
import com.mosman.routines.Routine
import com.mosman.routines.Store
import com.mosman.routines.ai.AssistantMode
import com.mosman.routines.ai.AssistantPrefs
import com.mosman.routines.ai.GeminiAssistant
import com.mosman.routines.ai.GeminiProposalMapper
import com.mosman.routines.drafts.ApprovalResult
import com.mosman.routines.drafts.DraftOperation
import com.mosman.routines.drafts.RoutineConflictAnalyzer
import com.mosman.routines.drafts.RoutineDraft
import com.mosman.routines.ui.lens.RoutineLensProcessor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

data class ChatMessage(
    val id: String = UUID.randomUUID().toString(),
    val isUser: Boolean,
    val text: String,
    val imageBitmap: Bitmap? = null,
    val draft: RoutineDraft? = null,
    val timestamp: Long = System.currentTimeMillis(),
    /** Who answered: shown under assistant messages so it's always clear what left the phone. */
    val source: String? = null,
    /** A non-image attachment the user sent (shown as a file chip). */
    val attachmentName: String? = null,
)

const val SOURCE_OFFLINE = "On this phone"
const val SOURCE_ONLINE = "Gemini · online"

/** The current conversation, held for as long as the app process lives. */
object ChatSession {
    val messages = mutableStateListOf<ChatMessage>()
}

/**
 * Layout B (picked by Mutalib 2026-11-20 from six structures): your messages are the only
 * bubbles, answers are plain text, proposals are compact cards that open on tap, and the
 * composer is one rounded box at the bottom holding attach (+), the mode chip and send.
 * Colours come only from the Material You scheme; spacing/shape only from these tokens.
 */
private object Sp {
    val xs = 4.dp; val s = 8.dp; val m = 12.dp; val l = 16.dp; val xl = 24.dp; val xxl = 32.dp
}
private object Shp {
    val bubble = RoundedCornerShape(20.dp, 20.dp, 6.dp, 20.dp)
    val composer = RoundedCornerShape(28.dp)
    val card = RoundedCornerShape(18.dp)
    val tile = RoundedCornerShape(12.dp)
    val chip = RoundedCornerShape(18.dp)
}

/** An attachment picked in the composer, before it's sent. */
private class Picked(val uri: Uri, val name: String, val kind: RoutineLensProcessor.Kind, val preview: Bitmap?)

/** A sent attachment waiting for the per-file "send this to Gemini?" answer. */
private class PendingFile(
    val uri: Uri, val name: String, val kind: RoutineLensProcessor.Kind, val text: String,
    val offline: ParsedAIResponse, val saved: List<Routine>,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AssistantScreenRedesigned(
    drafts: List<RoutineDraft>,
    onApproveDraft: (RoutineDraft) -> ApprovalResult,
    onRejectDraft: (RoutineDraft) -> Unit,
    onEditDraft: (RoutineDraft) -> Unit,
    onOpenLens: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()

    var input by remember { mutableStateOf("") }
    var picked by remember { mutableStateOf<Picked?>(null) }
    var cameraTempUri by remember { mutableStateOf<Uri?>(null) }
    var showAttachSheet by remember { mutableStateOf(false) }
    var showModeSheet by remember { mutableStateOf(false) }
    var thinking by remember { mutableStateOf<String?>(null) }
    var mode by remember { mutableStateOf(AssistantPrefs.mode(context)) }
    // A typed message waiting for the one-time "send to Gemini?" answer.
    var pendingOnline by remember { mutableStateOf<Triple<String, ParsedAIResponse, List<Routine>>?>(null) }
    // An attachment waiting for its own "send this to Gemini?" answer (asked every time).
    var pendingFile by remember { mutableStateOf<PendingFile?>(null) }

    val messages = ChatSession.messages

    val suggestions = listOf(
        "Silence my phone in lectures at 8am on weekdays",
        "Bedtime mode at 10:30 PM",
        "Turn on battery saver below 20%",
        "When I plug in my charger, turn on dark mode",
    )

    fun preview(uri: Uri): Bitmap? = runCatching {
        ImageDecoder.decodeBitmap(ImageDecoder.createSource(context.contentResolver, uri)) { d, info, _ ->
            val f = 320f / maxOf(info.size.width, info.size.height).coerceAtLeast(1)
            if (f < 1f) d.setTargetSize((info.size.width * f).toInt().coerceAtLeast(1), (info.size.height * f).toInt().coerceAtLeast(1))
        }
    }.getOrNull()

    fun attach(uri: Uri) {
        scope.launch {
            val kind = RoutineLensProcessor.kindOf(context, uri)
            val name = withContext(Dispatchers.IO) { RoutineLensProcessor.displayName(context, uri) }
            val bmp = if (kind == RoutineLensProcessor.Kind.IMAGE) withContext(Dispatchers.IO) { preview(uri) } else null
            picked = Picked(uri, name, kind, bmp)
        }
    }

    val photoPicker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { it?.let(::attach) }
    val filePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { it?.let(::attach) }
    val camera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        if (ok) cameraTempUri?.let(::attach)
    }

    fun launchCamera() {
        runCatching {
            val file = File.createTempFile("chat_snap_", ".jpg", context.cacheDir)
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
            cameraTempUri = uri
            camera.launch(uri)
        }
    }

    suspend fun scrollToEnd() { if (messages.isNotEmpty()) listState.animateScrollToItem(messages.size - 1) }

    // Shows an answer, with conflict warnings against saved routines; extra proposals get their own card.
    suspend fun post(parsed: ParsedAIResponse, saved: List<Routine>, source: String) {
        fun withConflicts(d: RoutineDraft?) = d?.let { draft ->
            val c = draft.proposedRoutine?.let { RoutineConflictAnalyzer.analyze(it, saved) }.orEmpty()
            draft.copy(warnings = draft.warnings + c.map { it.message })
        }
        messages.add(ChatMessage(isUser = false, text = parsed.explanation, draft = withConflicts(parsed.draft), source = source))
        parsed.extraDrafts.forEach { messages.add(ChatMessage(isUser = false, text = "", draft = withConflicts(it), source = source)) }
        scrollToEnd()
    }

    suspend fun askGeminiNow(text: String, offline: ParsedAIResponse, saved: List<Routine>) {
        thinking = "Asking Gemini…"
        val result = runCatching { GeminiAssistant.ask(text, saved) }
        thinking = null
        result.onSuccess { post(GeminiProposalMapper.map(it, saved), saved, SOURCE_ONLINE) }
            .onFailure { e ->
                messages.add(ChatMessage(isUser = false, source = SOURCE_OFFLINE,
                    text = "The online assistant isn't reachable right now (${GeminiAssistant.describeError(e)}). Answering on the phone instead:"))
                post(offline, saved, SOURCE_OFFLINE)
            }
    }

    suspend fun askGeminiAboutFile(p: PendingFile, userText: String) {
        thinking = "Gemini is reading it…"
        val result = runCatching {
            val att = RoutineLensProcessor.loadForGemini(context, p.uri)
            GeminiAssistant.readAttachment(att, userText)
        }
        thinking = null
        result.onSuccess { post(GeminiProposalMapper.mapAttachment(it, p.saved), p.saved, SOURCE_ONLINE) }
            .onFailure { e ->
                messages.add(ChatMessage(isUser = false, source = SOURCE_OFFLINE,
                    text = "Gemini couldn't read it (${GeminiAssistant.describeError(e)}). The phone found this instead:"))
                post(p.offline, p.saved, SOURCE_OFFLINE)
            }
    }

    fun send(userText: String) {
        val p = picked
        if (userText.isBlank() && p == null) return
        val text = userText.trim()
        messages.add(ChatMessage(
            isUser = true,
            text = text,
            imageBitmap = p?.preview,
            attachmentName = p?.takeIf { it.kind != RoutineLensProcessor.Kind.IMAGE }?.name,
        ))
        input = ""
        picked = null
        thinking = if (p != null) "Reading it on your phone…" else "Thinking…"

        scope.launch {
            scrollToEnd()
            val saved = Store.load(context)

            if (p == null) {
                val parsed = RoutineAIParser.parse(text, null, saved)
                when {
                    !AssistantPrefs.wantsOnline(mode, parsed.handled, hasPhoto = false) -> { thinking = null; post(parsed, saved, SOURCE_OFFLINE) }
                    AssistantPrefs.hasConsent(context) -> askGeminiNow(text, parsed, saved)
                    else -> { thinking = null; pendingOnline = Triple(text, parsed, saved) }
                }
                return@launch
            }

            // An attachment: read it on the phone first (same rules as typed text).
            val read = RoutineLensProcessor.recognizeUri(context, p.uri)
            val found = read.getOrNull().orEmpty()
            val offline = when {
                read.isFailure -> ParsedAIResponse("I couldn't read it: ${read.exceptionOrNull()?.message ?: "unknown error"}.", null, handled = false)
                found.isBlank() -> ParsedAIResponse("I didn't find any text in it.", null, handled = false)
                else -> RoutineAIParser.parse(text, found, saved).let { r ->
                    if (r.draft == null && !r.explanation.startsWith("I read "))
                        r.copy(explanation = com.mosman.routines.ui.lens.lensNoRoutineMessage(r.explanation, found))
                    else r
                }
            }
            thinking = null
            if (offline.draft != null || mode == AssistantMode.OFFLINE_ONLY) {
                post(offline, saved, SOURCE_OFFLINE)
            } else {
                // The phone couldn't make a routine from it: offer Gemini, asked for this file.
                pendingFile = PendingFile(p.uri, p.name, p.kind, text, offline, saved)
            }
        }
    }

    // ---- Dialogs and sheets ------------------------------------------------------------

    pendingOnline?.let { (text, offline, saved) ->
        AlertDialog(
            onDismissRequest = { },
            title = { Text("Send this to Gemini?") },
            text = {
                Text("The phone can't handle this one by itself. To answer, your message and the names of your " +
                    "routines go to Google's Gemini. On the free plan, Google may use them to improve its products " +
                    "and people may read them. Routine details stay on your phone.")
            },
            confirmButton = {
                TextButton(onClick = {
                    AssistantPrefs.setConsent(context, true)
                    pendingOnline = null
                    scope.launch { askGeminiNow(text, offline, saved) }
                }) { Text("Allow") }
            },
            dismissButton = {
                TextButton(onClick = {
                    pendingOnline = null
                    mode = AssistantMode.OFFLINE_ONLY
                    AssistantPrefs.setMode(context, AssistantMode.OFFLINE_ONLY)
                    scope.launch { post(offline, saved, SOURCE_OFFLINE) }
                }) { Text("Keep it offline") }
            },
        )
    }

    pendingFile?.let { p ->
        val what = if (p.kind == RoutineLensProcessor.Kind.IMAGE) "picture" else "file"
        AlertDialog(
            onDismissRequest = { },
            title = { Text("Send this $what to Gemini?") },
            text = {
                Text("The phone couldn't find a routine in it. Gemini can read timetables and posters better. " +
                    "The whole $what goes to Google: on the free plan Google may use it to improve its products " +
                    "and people may see it. I'll ask again for every $what.")
            },
            confirmButton = {
                TextButton(onClick = {
                    pendingFile = null
                    scope.launch { askGeminiAboutFile(p, p.text) }
                }) { Text("Send $what") }
            },
            dismissButton = {
                TextButton(onClick = {
                    pendingFile = null
                    scope.launch { post(p.offline, p.saved, SOURCE_OFFLINE) }
                }) { Text("Keep it on the phone") }
            },
        )
    }

    if (showModeSheet) {
        ModalBottomSheet(onDismissRequest = { showModeSheet = false }) {
            Column(Modifier.padding(horizontal = Sp.xl).padding(bottom = Sp.xxl)) {
                Text("Who answers?", style = MaterialTheme.typography.titleLarge)
                Spacer(Modifier.height(Sp.s))
                Text(
                    "Online answers go to Google's Gemini with your routine names. Pictures and files go only when you say yes each time.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(Sp.l))
                AssistantMode.entries.forEach { m ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clip(Shp.card)
                            .selectable(selected = m == mode, role = Role.RadioButton) {
                                mode = m
                                AssistantPrefs.setMode(context, m)
                                showModeSheet = false
                            }
                            .background(if (m == mode) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surface)
                            .padding(horizontal = Sp.m, vertical = Sp.m),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = m == mode, onClick = null)
                        Spacer(Modifier.width(Sp.m))
                        Column {
                            Text(m.label(), style = MaterialTheme.typography.titleSmall)
                            Text(m.detail(), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    Spacer(Modifier.height(Sp.xs))
                }
            }
        }
    }

    if (showAttachSheet) {
        ModalBottomSheet(onDismissRequest = { showAttachSheet = false }) {
            Column(Modifier.padding(horizontal = Sp.xl).padding(bottom = Sp.xxl)) {
                Text("Add to your message", style = MaterialTheme.typography.titleLarge)
                Spacer(Modifier.height(Sp.s))
                Text(
                    "A photo, image or file with a time in it: a timetable, a shift roster, an event poster.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(Sp.m))
                AttachRow(Icons.Outlined.PhotoCamera, "Take a photo") { showAttachSheet = false; launchCamera() }
                AttachRow(Icons.Outlined.Image, "Choose an image") {
                    showAttachSheet = false
                    photoPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                }
                AttachRow(Icons.Outlined.Description, "Choose a file", "PDF or text") {
                    showAttachSheet = false
                    filePicker.launch(arrayOf("application/pdf", "text/plain", "image/*"))
                }
            }
        }
    }

    // ---- Screen ------------------------------------------------------------------------

    Scaffold(
        modifier = modifier,
        // The app's main scaffold already makes room for the status bar and the tabs; adding
        // them again here is what left an empty band at the top and under the composer.
        contentWindowInsets = WindowInsets(0),
        topBar = {
            CenterAlignedTopAppBar(
                windowInsets = WindowInsets(0),
                title = {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("Routine AI", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                        Text(mode.label(), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                },
                actions = {
                    if (messages.isNotEmpty()) {
                        IconButton(onClick = { messages.clear() }) {
                            Icon(Icons.Outlined.AddComment, contentDescription = "New chat")
                        }
                    }
                },
            )
        },
        bottomBar = {
            Composer(
                input = input,
                onInput = { input = it },
                picked = picked,
                onRemovePicked = { picked = null },
                mode = mode,
                onMode = { showModeSheet = true },
                onAttach = { showAttachSheet = true },
                onSend = { send(input) },
                modifier = Modifier.imePadding(),
            )
        },
    ) { pad ->
        if (messages.isEmpty()) {
            EmptyChat(suggestions, onPick = { send(it) }, modifier = Modifier.padding(pad))
        } else {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize().padding(pad),
                contentPadding = PaddingValues(horizontal = Sp.l, vertical = Sp.m),
                verticalArrangement = Arrangement.spacedBy(Sp.m),
            ) {
                items(messages, key = { it.id }) { msg ->
                    if (msg.isUser) UserMessage(msg)
                    else AssistantMessage(
                        msg,
                        onApprove = { d ->
                            val reply = when (val r = onApproveDraft(d)) {
                                is ApprovalResult.Success -> r.message
                                is ApprovalResult.Error -> "I couldn't save that: " + r.errors.joinToString("; ")
                                is ApprovalResult.Rejected -> r.reason
                            }
                            val i = messages.indexOfFirst { it.id == msg.id }
                            if (i >= 0) messages[i] = messages[i].copy(draft = null)
                            messages.add(ChatMessage(isUser = false, text = reply))
                            scope.launch { scrollToEnd() }
                        },
                        onReject = { d ->
                            onRejectDraft(d)
                            val i = messages.indexOfFirst { it.id == msg.id }
                            if (i >= 0) messages[i] = messages[i].copy(draft = null)
                            messages.add(ChatMessage(isUser = false, text = "Okay, I've dropped that. Nothing was changed."))
                            scope.launch { scrollToEnd() }
                        },
                        onEdit = onEditDraft,
                    )
                }
                thinking?.let { label ->
                    item(key = "thinking") {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                            Spacer(Modifier.width(Sp.s))
                            Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }
    }
}

// ---- Pieces ------------------------------------------------------------------------------

@Composable
private fun EmptyChat(suggestions: List<String>, onPick: (String) -> Unit, modifier: Modifier = Modifier) {
    // Scrolls, so the tiles stay whole when the keyboard takes half the screen (seen on the Pixel).
    Column(
        modifier.fillMaxSize().verticalScroll(androidx.compose.foundation.rememberScrollState())
            .padding(horizontal = Sp.xl, vertical = Sp.xl),
    ) {
        Text("What should your phone do?", style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(Sp.s))
        Text(
            "Say it in your own words, or add a picture or file with a time in it. You'll see the routine before anything changes.",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(Sp.xl))
        suggestions.chunked(2).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(Sp.s)) {
                row.forEach { s ->
                    Surface(
                        onClick = { onPick(s) },
                        shape = Shp.card,
                        color = MaterialTheme.colorScheme.surfaceContainer,
                        modifier = Modifier.weight(1f).heightIn(min = 88.dp),
                    ) {
                        Text(s, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(Sp.m))
                    }
                }
            }
            Spacer(Modifier.height(Sp.s))
        }
    }
}

@Composable
private fun UserMessage(msg: ChatMessage) {
    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.End) {
        msg.imageBitmap?.let { bmp ->
            Image(
                bmp.asImageBitmap(), contentDescription = "Attached picture",
                contentScale = ContentScale.Crop,
                modifier = Modifier.widthIn(max = 220.dp).heightIn(max = 220.dp).clip(Shp.card),
            )
            Spacer(Modifier.height(Sp.xs))
        }
        msg.attachmentName?.let { name ->
            FileChip(name)
            Spacer(Modifier.height(Sp.xs))
        }
        if (msg.text.isNotBlank()) {
            Text(
                msg.text,
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier
                    .widthIn(max = 300.dp)
                    .clip(Shp.bubble)
                    .background(MaterialTheme.colorScheme.surfaceContainerHighest)
                    .padding(horizontal = Sp.l, vertical = Sp.m),
            )
        }
    }
}

@Composable
private fun AssistantMessage(
    msg: ChatMessage,
    onApprove: (RoutineDraft) -> Unit,
    onReject: (RoutineDraft) -> Unit,
    onEdit: (RoutineDraft) -> Unit,
) {
    Column(Modifier.fillMaxWidth()) {
        if (msg.text.isNotBlank()) {
            Text(msg.text, style = MaterialTheme.typography.bodyLarge)
        }
        msg.draft?.let { d ->
            Spacer(Modifier.height(Sp.s))
            ProposalCard(d, onApprove, onReject, onEdit)
        }
        msg.source?.let {
            Spacer(Modifier.height(Sp.xs))
            Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
        }
    }
}

/** One line for a routine: its first trigger, then what it does. */
private fun oneLine(r: Routine): String {
    val first = r.triggers.firstOrNull()?.describe() ?: "No trigger"
    val more = if (r.triggers.size > 1) " (+${r.triggers.size - 1} more)" else ""
    return "$first$more → " + r.actions.joinToString(", ") { it.describe() }
}

/** Compact result card: icon, name, one line, primary action. Tap to see every detail. */
@Composable
private fun ProposalCard(
    d: RoutineDraft,
    onApprove: (RoutineDraft) -> Unit,
    onReject: (RoutineDraft) -> Unit,
    onEdit: (RoutineDraft) -> Unit,
) {
    var open by remember(d.draftId) { mutableStateOf(false) }
    val r = d.proposedRoutine
    val (icon, title, primary) = when (d.operation) {
        DraftOperation.ENABLE -> Triple(Icons.Outlined.PlayCircle, "Switch back on", "Switch on")
        DraftOperation.DISABLE -> Triple(Icons.Outlined.PauseCircle, "Pause", "Pause")
        DraftOperation.DELETE -> Triple(Icons.Outlined.DeleteOutline, "Delete", "Delete")
        else -> Triple(Ic.of(r?.icon ?: "star"), r?.name ?: "New routine", "Save")
    }
    val subtitle = if (r != null && d.operation == DraftOperation.CREATE) oneLine(r) else d.explanation

    Surface(
        shape = Shp.card,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, Shp.card)
            .animateContentSize(),
    ) {
        Column(Modifier.padding(Sp.m)) {
            Row(
                Modifier.fillMaxWidth().clip(Shp.tile).clickable { open = !open },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    Modifier.size(40.dp).clip(Shp.tile).background(MaterialTheme.colorScheme.secondaryContainer),
                    contentAlignment = Alignment.Center,
                ) { Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSecondaryContainer) }
                Spacer(Modifier.width(Sp.m))
                Column(Modifier.weight(1f)) {
                    Text(title, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(
                        subtitle, style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = if (open) Int.MAX_VALUE else 2, overflow = TextOverflow.Ellipsis,
                    )
                }
                Icon(
                    if (open) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore,
                    contentDescription = if (open) "Hide details" else "Show details",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            if (open) {
                Spacer(Modifier.height(Sp.m))
                if (r != null && d.operation == DraftOperation.CREATE) {
                    if (d.explanation.contains('\n')) {
                        Text(d.explanation, style = MaterialTheme.typography.bodySmall)
                        Spacer(Modifier.height(Sp.s))
                    }
                    DetailRow("WHEN", r.ifSummary())
                    DetailRow("THEN", r.thenSummary())
                    r.endSummary()?.let { DetailRow("UNTIL", it) }
                }
                (d.questions + d.warnings).forEach {
                    Spacer(Modifier.height(Sp.xs))
                    Text("• $it", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }

            Spacer(Modifier.height(Sp.m))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = { onReject(d) }) { Text("Not now") }
                if (r != null) {
                    Spacer(Modifier.width(Sp.xs))
                    OutlinedButton(onClick = { onEdit(d) }) { Text("Edit") }
                }
                Spacer(Modifier.width(Sp.s))
                Button(
                    onClick = { onApprove(d) },
                    colors = if (d.operation == DraftOperation.DELETE) ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error, contentColor = MaterialTheme.colorScheme.onError,
                    ) else ButtonDefaults.buttonColors(),
                ) { Text(primary) }
            }
        }
    }
}

@Composable
private fun DetailRow(label: String, value: String) {
    Row(Modifier.padding(vertical = Sp.xs)) {
        Text(
            label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline,
            modifier = Modifier.width(52.dp).padding(top = 2.dp),
        )
        Text(value, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun FileChip(name: String, onRemove: (() -> Unit)? = null) {
    Row(
        Modifier.clip(Shp.chip).background(MaterialTheme.colorScheme.surfaceContainerHighest)
            .padding(start = Sp.m, end = if (onRemove != null) Sp.xs else Sp.m, top = Sp.s, bottom = Sp.s),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Outlined.Description, contentDescription = null, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(Sp.s))
        Text(name, style = MaterialTheme.typography.labelLarge, maxLines = 1, overflow = TextOverflow.Ellipsis,
            modifier = Modifier.widthIn(max = 200.dp))
        onRemove?.let {
            IconButton(onClick = it, modifier = Modifier.size(32.dp)) {
                Icon(Icons.Outlined.Close, contentDescription = "Remove $name", modifier = Modifier.size(16.dp))
            }
        }
    }
}

@Composable
private fun Composer(
    input: String,
    onInput: (String) -> Unit,
    picked: Picked?,
    onRemovePicked: () -> Unit,
    mode: AssistantMode,
    onMode: () -> Unit,
    onAttach: () -> Unit,
    onSend: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val canSend = input.isNotBlank() || picked != null
    Surface(
        shape = Shp.composer,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = modifier.fillMaxWidth().padding(horizontal = Sp.m, vertical = Sp.s),
    ) {
        Column(Modifier.padding(horizontal = Sp.s, vertical = Sp.s)) {
            picked?.let { p ->
                Box(Modifier.padding(start = Sp.s, top = Sp.xs, bottom = Sp.xs)) {
                    if (p.preview != null) {
                        Box {
                            Image(
                                p.preview.asImageBitmap(), contentDescription = "Picture to send",
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.size(64.dp).clip(Shp.tile),
                            )
                            Box(
                                Modifier.align(Alignment.TopEnd).padding(Sp.xs).size(24.dp).clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.inverseSurface).clickable(onClick = onRemovePicked),
                                contentAlignment = Alignment.Center,
                            ) {
                                Icon(Icons.Outlined.Close, contentDescription = "Remove picture",
                                    tint = MaterialTheme.colorScheme.inverseOnSurface, modifier = Modifier.size(14.dp))
                            }
                        }
                    } else FileChip(p.name, onRemove = onRemovePicked)
                }
            }
            TextField(
                value = input,
                onValueChange = onInput,
                placeholder = { Text("Ask Routine AI") },
                maxLines = 5,
                textStyle = MaterialTheme.typography.bodyLarge,
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                    focusedIndicatorColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                    unfocusedIndicatorColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                ),
                modifier = Modifier.fillMaxWidth(),
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onAttach) {
                    Icon(Icons.Outlined.Add, contentDescription = "Add a photo, image or file")
                }
                Surface(
                    onClick = onMode,
                    shape = Shp.chip,
                    color = MaterialTheme.colorScheme.surfaceContainerHighest,
                    modifier = Modifier.heightIn(min = 40.dp),
                ) {
                    Row(Modifier.padding(start = Sp.m, end = Sp.s, top = Sp.s, bottom = Sp.s), verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            when (mode) {
                                AssistantMode.AUTOMATIC -> Icons.Outlined.AutoMode
                                AssistantMode.OFFLINE_ONLY -> Icons.Outlined.PhoneAndroid
                                AssistantMode.ONLINE -> Icons.Outlined.Cloud
                            },
                            contentDescription = null, modifier = Modifier.size(18.dp),
                        )
                        Spacer(Modifier.width(Sp.s))
                        Text(mode.label(), style = MaterialTheme.typography.labelLarge)
                        Icon(Icons.Outlined.ArrowDropDown, contentDescription = "Change who answers")
                    }
                }
                Spacer(Modifier.weight(1f))
                FilledIconButton(onClick = onSend, enabled = canSend) {
                    Icon(Icons.Filled.ArrowUpward, contentDescription = "Send")
                }
            }
        }
    }
}

@Composable
private fun AttachRow(icon: ImageVector, label: String, detail: String? = null, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(Shp.card).clickable(onClick = onClick).padding(vertical = Sp.m, horizontal = Sp.xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(44.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceContainerHighest),
            contentAlignment = Alignment.Center,
        ) { Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant) }
        Spacer(Modifier.width(Sp.l))
        Column {
            Text(label, style = MaterialTheme.typography.titleSmall)
            detail?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
    }
}

