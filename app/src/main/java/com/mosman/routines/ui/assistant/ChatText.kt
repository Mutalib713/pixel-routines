package com.mosman.routines.ui.assistant

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp

/**
 * A tiny, safe subset of formatting for chat answers, so replies come out in sections instead of
 * one block of text:
 *
 *   ## Heading        a small section title
 *   - item / • item   a bullet line (also "* item")
 *   **bold**          bold words inside any line
 *   blank line        a gap between paragraphs
 *
 * Anything else is shown as plain text. It never runs links or HTML: it only changes how the
 * words look.
 */
object ChatFormat {
    sealed interface Block {
        data class Heading(val text: String) : Block
        data class Bullet(val text: String) : Block
        data class Para(val text: String) : Block
    }

    private val BULLET = Regex("^\\s*(?:[-•*])\\s+(.*)$")
    private val HEADING = Regex("^\\s*#{1,3}\\s+(.*)$")

    fun blocks(text: String): List<Block> {
        val out = mutableListOf<Block>()
        val para = StringBuilder()
        fun flush() {
            if (para.isNotBlank()) out += Block.Para(para.toString().trim())
            para.clear()
        }
        text.lines().forEach { line ->
            val h = HEADING.find(line)
            val b = BULLET.find(line)
            when {
                line.isBlank() -> flush()
                h != null -> { flush(); out += Block.Heading(h.groupValues[1].trim().trimEnd(':')) }
                b != null -> { flush(); out += Block.Bullet(b.groupValues[1].trim()) }
                else -> { if (para.isNotEmpty()) para.append('\n'); para.append(line.trim()) }
            }
        }
        flush()
        return out
    }

    /** Turns **bold** into bold spans; a lone "**" is left as written. */
    fun inline(text: String): AnnotatedString = buildAnnotatedString {
        val parts = text.split("**")
        if (parts.size % 2 == 0) { append(text); return@buildAnnotatedString }
        parts.forEachIndexed { i, p ->
            if (i % 2 == 1) withStyle(SpanStyle(fontWeight = FontWeight.SemiBold)) { append(p) } else append(p)
        }
    }
}

@Composable
fun ChatText(text: String, modifier: Modifier = Modifier, style: TextStyle = MaterialTheme.typography.bodyLarge) {
    val blocks = ChatFormat.blocks(text)
    Column(modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        blocks.forEachIndexed { i, b ->
            when (b) {
                is ChatFormat.Block.Heading -> Text(
                    ChatFormat.inline(b.text),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(top = if (i == 0) 0.dp else 8.dp),
                )
                is ChatFormat.Block.Bullet -> Row(Modifier.padding(start = 4.dp)) {
                    Text("•", style = style, color = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(10.dp))
                    Text(ChatFormat.inline(b.text), style = style)
                }
                is ChatFormat.Block.Para -> Text(ChatFormat.inline(b.text), style = style)
            }
        }
    }
}
