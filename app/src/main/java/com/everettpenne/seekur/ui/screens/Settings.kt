package com.everettpenne.seekur.ui.screens

import androidx.compose.material.icons.automirrored.rounded.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.draw.clip
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.text.style.TextAlign
import coil3.SingletonImageLoader
import com.everettpenne.seekur.data.AppIcon
import com.everettpenne.seekur.data.AppIcons
import com.everettpenne.seekur.BuildConfig
import com.everettpenne.seekur.graph
import com.everettpenne.glass.*
import com.everettpenne.seekur.ui.theme.Seekur

@Composable
fun SettingsScreen(onBack: () -> Unit, onAccounts: () -> Unit) {
    val context = LocalContext.current
    val settings = context.graph.settings
    val accounts by context.graph.accounts.accounts.collectAsState()
    val activeId by context.graph.accounts.activeId.collectAsState()
    val persistFeed by settings.persistFeed.collectAsState()
    val persistSort by settings.persistSort.collectAsState()
    val hideSwitcher by settings.hideFeedSwitcher.collectAsState()
    val autoplay by settings.autoplay.collectAsState()
    val mute by settings.muteOnPlay.collectAsState()
    val blur by settings.blurNsfw.collectAsState()
    var cacheCleared by remember { mutableStateOf(false) }

    val saveLocally by settings.saveLocally.collectAsState()
    val localCount by context.graph.localSaves.posts.collectAsState()
    var cacheBytes by remember { mutableStateOf(SingletonImageLoader.get(context).diskCache?.size ?: 0L) }

    GlassScreen(bars = { GlassCenteredTopBar("Settings", onBack = onBack, modifier = Modifier.align(Alignment.TopCenter)) }) {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(top = topContentPadding(), bottom = bottomContentPadding(), start = 16.dp, end = 16.dp),
            verticalArrangement = Arrangement.spacedBy(22.dp)) {
            Group("Reddit Login") {
                NavRow(Icons.Rounded.Person, "Accounts", accounts.firstOrNull { it.id == activeId }?.userName?.let { "u/$it" }, onAccounts)
            }
            Group("Saved Posts", footer = "On: the bookmark keeps a post on this phone only and never touches your Reddit account. " +
                "${localCount.size} saved here. Your Reddit saved list is left as it is; turning this off shows it again.") {
                SwitchRow(Icons.Rounded.PhoneAndroid, "Save on this device", saveLocally, settings::setSaveLocally)
            }
            var icon by remember { mutableStateOf(AppIcons.current(context)) }
            Group("App Icon", footer = "Your launcher may take a moment to update, and some show a brief flicker.") {
                Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 14.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
                    for (option in AppIcon.entries) IconChoice(option, selected = option == icon) { AppIcons.select(context, option); icon = option }
                }
            }
            Group("Feeds") {
                SwitchRow(Icons.Rounded.Layers, "Remember Selected Feed", persistFeed, settings::setPersistFeed)
                SwitchRow(Icons.Rounded.SwapVert, "Remember Sort Order", persistSort, settings::setPersistSort)
                SwitchRow(Icons.Rounded.ExpandCircleDown, "Hide Feed Switcher", hideSwitcher, settings::setHideFeedSwitcher)
                SwitchRow(Icons.Rounded.VisibilityOff, "Blur NSFW Media", blur, settings::setBlurNsfw)
            }
            Group("Playback Settings") {
                SwitchRow(Icons.Rounded.PlayArrow, "Autoplay Videos", autoplay, settings::setAutoplay)
                SwitchRow(Icons.AutoMirrored.Rounded.VolumeOff, "Mute on Play", mute, settings::setMuteOnPlay)
            }
            Group("Storage") {
                NavRow(Icons.Rounded.DeleteSweep, "Clear Image Cache", "%.1f MB".format(cacheBytes / 1_048_576.0)) {
                    val loader = SingletonImageLoader.get(context)
                    loader.memoryCache?.clear(); loader.diskCache?.clear(); cacheBytes = loader.diskCache?.size ?: 0L
                }
            }
            Group("About") {
                NavRow(Icons.Rounded.Info, "Seekur ${BuildConfig.VERSION_NAME}", "An Android port of Lurker, with iOS-style liquid glass") {}
                NavRow(Icons.Rounded.Code, "Open Source", "MIT licensed. Based on SwiftDdit (Lurker) by Zabir Raihan") {
                    context.startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse("https://github.com/SilverMarcs/SwiftDdit")))
                }
            }
            Text("Seekur uses Reddit's website, signed in with your own session, not Reddit's official API. It can stop working if Reddit changes or blocks that.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 8.dp))
        }
    }
}

@Composable
private fun Group(title: String, footer: String? = null, content: @Composable ColumnScope.() -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(title, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(start = 10.dp))
        Column(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(26.dp)), content = content)
        if (footer != null) Text(footer, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 10.dp))
    }
}

@Composable
private fun RowShell(icon: ImageVector, title: String, subtitle: String?, onClick: (() -> Unit)?, trailing: @Composable () -> Unit) {
    Row(Modifier.fillMaxWidth().heightIn(min = 56.dp).then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier).padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        Icon(icon, null, tint = Seekur.accent, modifier = Modifier.size(24.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            if (subtitle != null) Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        trailing()
    }
}

@Composable
private fun NavRow(icon: ImageVector, title: String, subtitle: String?, onClick: () -> Unit) =
    RowShell(icon, title, subtitle, onClick) { Icon(Icons.Rounded.ChevronRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant) }

@Composable
private fun SwitchRow(icon: ImageVector, title: String, checked: Boolean, onChange: (Boolean) -> Unit) =
    RowShell(icon, title, null, { onChange(!checked) }) { LiquidSwitch(checked, onChange) }

@Composable
private fun IconChoice(option: AppIcon, selected: Boolean, onClick: () -> Unit) {
    Column(Modifier.width(72.dp).clickable(onClick = onClick), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        AndroidView(
            factory = { ctx -> android.widget.ImageView(ctx).apply { setImageDrawable(androidx.core.content.ContextCompat.getDrawable(ctx, option.mipmap)) } },
            modifier = Modifier.size(60.dp).then(if (selected) Modifier.border(2.5.dp, Seekur.accent, RoundedCornerShape(18.dp)).padding(3.dp) else Modifier.padding(3.dp)).clip(RoundedCornerShape(15.dp)),
        )
        Text(option.label, style = MaterialTheme.typography.labelSmall, textAlign = TextAlign.Center,
            color = if (selected) Seekur.accent else MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
