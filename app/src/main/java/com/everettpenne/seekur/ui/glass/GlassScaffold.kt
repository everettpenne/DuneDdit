package com.everettpenne.seekur.ui.glass

import androidx.compose.material.icons.automirrored.rounded.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.platform.LocalContext
import com.kyant.backdrop.backdrops.layerBackdrop
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.clickable
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import com.everettpenne.seekur.ui.theme.Seekur

/** The bar across the top of a screen: a large title on the left, glass buttons on the right, fading into the page beneath. */
@Composable
fun GlassTopBar(title: String, modifier: Modifier = Modifier, subtitle: String? = null, onBack: (() -> Unit)? = null,
                actions: @Composable RowScope.() -> Unit = {}) {
    val bg = MaterialTheme.colorScheme.background
    Box(modifier.fillMaxWidth().background(Brush.verticalGradient(listOf(bg.copy(alpha = .94f), bg.copy(alpha = .8f), Color.Transparent)))) {
        Row(Modifier.fillMaxWidth().statusBarsPadding().padding(start = 16.dp, end = 16.dp, top = 6.dp, bottom = 14.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            if (onBack != null) GlassIconButton(Icons.AutoMirrored.Rounded.ArrowBack, "Back", onBack)
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.headlineMedium, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    fontSize = if (onBack != null) 22.sp else 30.sp)
                if (subtitle != null) Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
            }
            actions()
        }
    }
}

/** How far the top bar reaches down, for the content's top padding (status bar plus the bar itself). */
val TopBarHeight = 72.dp

enum class Tab(val route: String, val label: String, val icon: ImageVector) {
    Feed("feed", "Feed", Icons.Rounded.Article),
    Profile("profile", "Profile", Icons.Rounded.Person),
    Search("search", "Search", Icons.Rounded.Search),
}

/** The floating tab bar: a glass capsule at the bottom, the chosen tab lit as an accent lens. */
@Composable
fun GlassTabBar(selected: Tab?, onSelect: (Tab) -> Unit, modifier: Modifier = Modifier) {
    val ink = glassPalette().ink
    Box(modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 24.dp, vertical = 10.dp), contentAlignment = Alignment.Center) {
        Row(Modifier.widthIn(max = 420.dp).fillMaxWidth().height(64.dp)
            .glassSurface(RoundedCornerShape(percent = 50), tint = ink.copy(alpha = .05f).compositeOver(glassPalette().tint), blurRadius = 8f, lensHeight = 24f, lensAmount = 32f)
            .padding(4.dp), verticalAlignment = Alignment.CenterVertically) {
            Tab.entries.forEach { tab ->
                val press = rememberGlassPress()
                val on = tab == selected
                val shape = RoundedCornerShape(percent = 50)
                Column(Modifier.weight(1f).fillMaxHeight()
                    .then(if (on) Modifier.clip(shape).background(Seekur.accent.copy(alpha = .20f)) else Modifier)
                    .graphicsLayer { with(press) { swell() } }.then(press.light).then(press.watcher)
                    .clickable(interactionSource = null, indication = null, role = Role.Tab) { onSelect(tab) }
                    .semantics { contentDescription = tab.label },
                    horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                    Icon(tab.icon, null, tint = if (on) Seekur.accent else ink.copy(alpha = .65f), modifier = Modifier.size(24.dp))
                    Text(tab.label, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = if (on) Seekur.accent else ink.copy(alpha = .65f))
                }
            }
        }
    }
}

/** Supplied by the root: the floating tab bar, drawn by each top-level screen so it refracts that screen's content. Null on pushed screens. */
val LocalTabBar = androidx.compose.runtime.compositionLocalOf<(@Composable () -> Unit)?> { null }

/**
 * A screen's glass layout. [content] (the scrolling page) is recorded into a layer; [bars] (top bar, search field, the tab bar) are drawn over
 * it and sample that layer, so the page really bends and blurs under them as it scrolls. Menus and sheets opened from anywhere in the screen
 * sample the same layer through [LocalOverlayBackdrop].
 */
@Composable
fun GlassScreen(modifier: Modifier = Modifier, bars: @Composable BoxScope.() -> Unit, content: @Composable BoxScope.() -> Unit) {
    val backdrop = com.kyant.backdrop.backdrops.rememberLayerBackdrop()
    val tabBar = LocalTabBar.current
    CompositionLocalProvider(LocalOverlayBackdrop provides backdrop) {
        Box(modifier.fillMaxSize()) {
            Box(Modifier.fillMaxSize().layerBackdrop(backdrop).background(MaterialTheme.colorScheme.background), content = content)
            CompositionLocalProvider(LocalGlassBackdrop provides backdrop) {
                bars()
                if (tabBar != null) Box(Modifier.align(Alignment.BottomCenter)) { tabBar() }
            }
        }
    }
}


/** A bottom sheet that floats as an inset liquid-glass card. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GlassSheet(onDismiss: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    val surface = MaterialTheme.colorScheme.surface.copy(alpha = .62f)
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = Color.Transparent, tonalElevation = 0.dp, scrimColor = Color.Black.copy(alpha = .22f), dragHandle = null) {
        CompositionLocalProvider(LocalGlassBackdrop provides (LocalOverlayBackdrop.current ?: LocalGlassBackdrop.current)) {
        Column(Modifier.padding(horizontal = 8.dp).padding(bottom = 8.dp).fillMaxWidth()
            .glassSurface(RoundedCornerShape(32.dp), tint = surface, blurRadius = 8f, lensHeight = 24f, lensAmount = 48f, shadowed = false)
            .background(if (LocalGlassBackdrop.current == null) MaterialTheme.colorScheme.surface else Color.Transparent, RoundedCornerShape(32.dp))
            .navigationBarsPadding().padding(bottom = 8.dp)) {
            androidx.compose.material3.BottomSheetDefaults.DragHandle(Modifier.align(Alignment.CenterHorizontally))
            content()
        }
        }
    }
}
