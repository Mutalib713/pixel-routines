package com.mosman.routines

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.nfc.NfcAdapter
import android.nfc.Tag
import android.os.Bundle
import android.widget.Toast

/** Turns a tag's hardware id into a stable hex string. */
fun Tag.idHex(): String = id.joinToString("") { "%02X".format(it) }

/**
 * Invisible activity that Android launches when an NFC tag is tapped. Finds routines
 * bound to that tag and runs them. Stick a tag by your bed → tap → Bedtime.
 */
class NfcActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        handle(intent)
        finish()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handle(intent)
        finish()
    }

    private fun handle(intent: Intent?) {
        val tag = intent?.let { readTag(it) } ?: return
        val id = tag.idHex()
        val matches = Store.load(this).filter { r ->
            r.enabled && r.triggers.any { it is Trigger.NfcTag && it.id == id }
        }
        if (matches.isEmpty()) {
            Toast.makeText(this, "No routine uses this tag", Toast.LENGTH_SHORT).show()
            return
        }
        matches.forEach { Engine.fireRoutine(applicationContext, it) }
        Toast.makeText(this, "Running ${matches.joinToString { it.name }}", Toast.LENGTH_SHORT).show()
    }

    companion object {
        @Suppress("DEPRECATION")
        fun readTag(intent: Intent): Tag? =
            intent.getParcelableExtra(NfcAdapter.EXTRA_TAG)

        fun available(ctx: Context): Boolean =
            NfcAdapter.getDefaultAdapter(ctx)?.isEnabled == true
    }
}
