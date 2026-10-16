package com.everettpenne.seekur.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import com.everettpenne.glass.GlassColors
import com.everettpenne.glass.GlassTheme

/** Seekur's brand colours; the theme, type scale and glass kit come from the shared Glass library. */
object Seekur {
    /** The original app's orange: tab selection, icons, links, buttons. */
    val accent = Color(0xFFFF9F0A)
    /** Link cards use system blue, as the original does. */
    val link = Color(0xFF0A84FF)
    val green = Color(0xFF34C759)
    val indigo = Color(0xFF5E5CE6)
    val red = Color(0xFFFF453A)
    val yellow = Color(0xFFFFD60A)
    val orange = Color(0xFFFF9F0A)
}

@Composable
fun SeekurTheme(content: @Composable () -> Unit) =
    GlassTheme(GlassColors(accent = Seekur.accent, red = Seekur.red, green = Seekur.green), content)
