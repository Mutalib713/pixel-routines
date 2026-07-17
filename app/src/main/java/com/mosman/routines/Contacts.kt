package com.mosman.routines

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.ContactsContract

data class PickedContact(val name: String, val number: String)

/**
 * Contact picking via the system picker. Deliberately uses ACTION_PICK rather than
 * READ_CONTACTS: the picker hands back one row with a temporary read grant, so the app
 * never gets access to the whole address book.
 */
object Contacts {

    fun pickIntent(): Intent =
        Intent(Intent.ACTION_PICK, ContactsContract.CommonDataKinds.Phone.CONTENT_URI)

    fun read(ctx: Context, uri: Uri): PickedContact? = runCatching {
        ctx.contentResolver.query(uri, arrayOf(
            ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
            ContactsContract.CommonDataKinds.Phone.NUMBER), null, null, null)?.use { c ->
            if (!c.moveToFirst()) return null
            PickedContact(c.getString(0) ?: "", (c.getString(1) ?: "").replace(" ", ""))
        }
    }.getOrNull()
}
