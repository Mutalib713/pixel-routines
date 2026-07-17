package com.mosman.routines

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.ShortcutInfo
import android.content.pm.ShortcutManager
import android.graphics.drawable.Icon
import android.os.Bundle
import android.widget.Toast

/**
 * Invisible activity that runs one routine and closes — the target for home-screen
 * shortcuts, so a routine is one tap away without opening the app.
 */
class RunActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val id = intent?.getLongExtra(EXTRA_ID, -1L) ?: -1L
        val r = if (id > 0) Store.get(this, id) else null
        if (r == null) {
            Toast.makeText(this, "That routine is gone", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(this, "Running ${r.name}", Toast.LENGTH_SHORT).show()
            Engine.runNow(applicationContext, r) { }
        }
        finish()
    }

    companion object {
        const val EXTRA_ID = "routine_id"

        fun intentFor(ctx: Context, r: Routine) =
            Intent(ctx, RunActivity::class.java).apply {
                action = Intent.ACTION_VIEW           // shortcuts require an action
                putExtra(EXTRA_ID, r.id)
            }

        /** Asks the launcher to pin a one-tap shortcut for this routine. */
        fun pinShortcut(ctx: Context, r: Routine): Boolean {
            val sm = ctx.getSystemService(ShortcutManager::class.java)
            if (sm?.isRequestPinShortcutSupported != true) return false
            val shortcut = ShortcutInfo.Builder(ctx, "routine-${r.id}")
                .setShortLabel(r.name.ifBlank { "Routine" })
                .setLongLabel("Run ${r.name}")
                .setIcon(Icon.createWithResource(ctx, R.mipmap.ic_launcher))
                .setIntent(intentFor(ctx, r))
                .build()
            return runCatching { sm.requestPinShortcut(shortcut, null) }.getOrDefault(false)
        }
    }
}
