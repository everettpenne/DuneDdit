package com.everettpenne.seekur.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.everettpenne.seekur.Graph
import com.everettpenne.seekur.data.api.ApiResult
import com.everettpenne.seekur.data.api.Failure
import com.everettpenne.seekur.data.model.Subreddit
import com.everettpenne.seekur.graph
import com.everettpenne.seekur.ui.components.Navigator
import com.everettpenne.seekur.ui.components.SubredditIcon
import com.everettpenne.seekur.ui.glass.*
import com.everettpenne.seekur.ui.theme.Seekur
import kotlinx.coroutines.launch

class ProfileViewModel(private val graph: Graph) : ViewModel() {
    var subreddits by mutableStateOf<List<Subreddit>>(emptyList()); private set
    var loading by mutableStateOf(false); private set
    var failure by mutableStateOf<Failure?>(null); private set
    private var loadedFor: String? = null

    fun load(force: Boolean = false) {
        val account = graph.accounts.activeId.value
        if (account == null) { subreddits = emptyList(); loadedFor = null; return }
        if (!force && loadedFor == account && subreddits.isNotEmpty()) return
        loading = true; failure = null; loadedFor = account
        viewModelScope.launch {
            when (val r = graph.api.fetchUserSubreddits()) {
                is ApiResult.Ok -> subreddits = r.value.filter { it.isSubscribed }
                is ApiResult.Failed -> failure = r.reason
            }
            loading = false
        }
    }
}

/** Inbox and Saved up top, starred subreddits, then every subscribed subreddit in alphabetical sections, with a filter. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(nav: Navigator, onInbox: () -> Unit, onSaved: () -> Unit, onSettings: () -> Unit) {
    val graph = LocalContext.current.graph
    val vm: ProfileViewModel = viewModel { ProfileViewModel(graph) }
    val activeId by graph.accounts.activeId.collectAsState()
    val favoriteNames by graph.favorites.names.collectAsState()
    LaunchedEffect(activeId) { vm.load() }
    var filter by remember { mutableStateOf("") }
    val filtered = remember(vm.subreddits, filter) { if (filter.isBlank()) vm.subreddits else vm.subreddits.filter { it.displayName.contains(filter, ignoreCase = true) } }
    val favorites = filtered.filter { it.displayName.lowercase() in favoriteNames }.sortedBy { it.displayName.lowercase() }
    val sections = filtered.filter { it.displayName.lowercase() !in favoriteNames }.groupBy { it.displayName.first().uppercaseChar().toString() }.toSortedMap()

    GlassScreen(bars = {
        Column(Modifier.align(Alignment.TopCenter)) {
            GlassTopBar("Profile") { GlassIconButton(Icons.Rounded.Settings, "Settings", onSettings) }
            GlassField(filter, { filter = it }, "Filter subreddits", Modifier.padding(horizontal = 16.dp))
        }
    }) {
        PullToRefreshBox(isRefreshing = false, onRefresh = { vm.load(force = true) }, modifier = Modifier.fillMaxSize()) {
            LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(top = topContentPadding() + 56.dp, bottom = bottomContentPadding(), start = 16.dp, end = 16.dp)) {
                if (filter.isBlank()) item {
                    Row(Modifier.fillMaxWidth().padding(bottom = 12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        LinkTile(Icons.Rounded.Inbox, "Inbox", Seekur.accent, onInbox, Modifier.weight(1f))
                        LinkTile(Icons.Rounded.Bookmark, "Saved", Seekur.green, onSaved, Modifier.weight(1f))
                    }
                }
                if (activeId == null) item { EmptyState(Icons.Rounded.PersonOff, "No account connected", "Go to Settings › Accounts to add one.") }
                else if (vm.loading && vm.subreddits.isEmpty()) item { LoadingRow() }
                else if (vm.failure != null && vm.subreddits.isEmpty()) item { ErrorState(vm.failure!!, onRetry = { vm.load(force = true) }) }
                if (favorites.isNotEmpty()) {
                    item { SectionHeader("Favorites") }
                    items(favorites, key = { "fav_" + it.id }) { SubredditRow(it, nav, graph.favorites, favoriteNames) }
                }
                sections.forEach { (letter, subs) ->
                    item(key = "h_$letter") { SectionHeader(letter) }
                    items(subs.sortedBy { it.displayName.lowercase() }, key = { it.id }) { SubredditRow(it, nav, graph.favorites, favoriteNames) }
                }
            }
        }
    }
}

@Composable
private fun SectionHeader(text: String) = Text(text, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 14.dp, bottom = 4.dp))

@Composable
private fun LinkTile(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, color: Color, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Column(modifier.background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(16.dp)).clickable(onClick = onClick).padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Icon(icon, null, tint = color, modifier = Modifier.size(34.dp))
        Text(title, style = MaterialTheme.typography.titleMedium)
    }
}

@Composable
private fun SubredditRow(sub: Subreddit, nav: Navigator, favorites: com.everettpenne.seekur.data.Favorites, names: Set<String>) {
    val starred = sub.displayName.lowercase() in names
    Row(Modifier.fillMaxWidth().clickable { nav.openSubreddit(sub) }.padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        SubredditIcon(sub, 36)
        Column(Modifier.weight(1f)) {
            Text(sub.displayNamePrefixed, style = MaterialTheme.typography.bodyLarge)
            if (sub.subscriberCount > 0) Text("${sub.formattedSubscribers} subscribers", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Icon(if (starred) Icons.Rounded.Star else Icons.Rounded.StarBorder, if (starred) "Remove favourite" else "Add favourite",
            tint = if (starred) Seekur.yellow else MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(26.dp).clickable { favorites.toggle(sub) })
    }
}
