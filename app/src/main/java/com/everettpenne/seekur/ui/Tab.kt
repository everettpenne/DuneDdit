package com.everettpenne.seekur.ui

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Article
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Search
import androidx.compose.ui.graphics.vector.ImageVector
import com.everettpenne.glass.GlassTabSpec

/** Seekur's top-level destinations. Feed and Profile sit in the glass tab capsule; Search is the round button beside it. */
enum class Tab(override val route: String, override val label: String, override val icon: ImageVector) : GlassTabSpec {
    Feed("feed", "Feed", Icons.Rounded.Article),
    Profile("profile", "Profile", Icons.Rounded.Person),
    Search("search", "Search", Icons.Rounded.Search);

    companion object { val bar = listOf(Feed, Profile) }
}
