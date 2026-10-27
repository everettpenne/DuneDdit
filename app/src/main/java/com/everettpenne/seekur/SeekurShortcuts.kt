package com.everettpenne.seekur

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.ShortcutInfo
import android.content.pm.ShortcutManager
import android.graphics.drawable.Icon
import android.net.Uri
import com.everettpenne.seekur.data.AppIcons

/**
 * Seekur's launcher shortcuts. They are dynamic because Seekur's launcher icon is one of several activity aliases and a
 * manifest shortcut belongs to one activity: a dynamic shortcut can point at whichever alias is showing. They are published
 * when the app starts and whenever the icon changes, so a person's chosen icon keeps its own shortcuts.
 */
internal object SeekurShortcuts {
    private data class Action(val id: String, val label: String, val host: String)

    /** Four, the most a launcher shows in its long-press sheet (Uno reads up to four). */
    private val actions = listOf(
        Action("seekur-feed", "Front page", "feed"),
        Action("seekur-inbox", "Inbox", "inbox"),
        Action("seekur-saved", "Saved", "saved"),
        Action("seekur-search", "Search", "search"),
    )

    /** Publishes the set for the icon showing now. Silent: a launcher that refuses them simply shows none. */
    fun publish(context: Context) = runCatching {
        val manager = context.getSystemService(ShortcutManager::class.java) ?: return@runCatching
        val icon = AppIcons.current(context)
        val component = ComponentName(context.packageName, "${context.packageName}.${icon.alias}")
        val shortcuts = actions.mapIndexed { rank, action ->
            ShortcutInfo.Builder(context, action.id)
                .setShortLabel(action.label)
                .setLongLabel(action.label)
                .setRank(rank)
                .setIcon(Icon.createWithResource(context, icon.mipmap))
                .setActivity(component)
                .setIntent(Intent(Intent.ACTION_VIEW, Uri.parse("seekur://${action.host}")).setComponent(component))
                .build()
        }
        manager.setDynamicShortcuts(shortcuts)
    }
}
