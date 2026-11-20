package com.mosman.routines.ui.assistant

import org.junit.Assert.assertEquals
import org.junit.Test

class ChatFormatTest {
    @Test fun headingsBulletsAndParagraphs() {
        val b = ChatFormat.blocks("You have 2 routines.\n\n## On\n- Class mode\n• Lecture Mode\n\n## Paused:\n* Wake up\nlast line")
        assertEquals(
            listOf(
                ChatFormat.Block.Para("You have 2 routines."),
                ChatFormat.Block.Heading("On"),
                ChatFormat.Block.Bullet("Class mode"),
                ChatFormat.Block.Bullet("Lecture Mode"),
                ChatFormat.Block.Heading("Paused"),
                ChatFormat.Block.Bullet("Wake up"),
                ChatFormat.Block.Para("last line"),
            ),
            b,
        )
    }

    @Test fun boldNeedsAPair() {
        assertEquals("When: x", ChatFormat.inline("**When:** x").text)
        assertEquals("a ** b", ChatFormat.inline("a ** b").text)
    }
}
