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
import androidx.compose.ui.unit.sp
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
                is ApiResult.Ok -> subreddits = r.value.filter { it.isSubscribed }.distinctBy { it.id + "/" + it.displayName.lowercase() }
                is ApiResult.Failed -> failure = r.reason
            }
            loading = false
        }
    }
}

private sealed interface ProfileRow {
    data object Links : ProfileRow
    data class Message(val icon: androidx.compose.ui.graphics.vector.ImageVector, val title: String, val text: String) : ProfileRow
    data object Loading : ProfileRow
    data class Failed(val failure: Failure) : ProfileRow
    data class Header(val text: String) : ProfileRow
    data class Sub(val subreddit: Subreddit) : ProfileRow
}

/** Inbox and Saved up top, starred subreddits, then every subscribed subreddit as a card in alphabetical sections with an index down the edge. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(nav: Navigator, onInbox: () -> Unit, onSaved: () -> Unit, onSettings: () -> Unit) {
    val graph = LocalContext.current.graph
    val vm: ProfileViewModel = viewModel { ProfileViewModel(graph) }
    val activeId by graph.accounts.activeId.collectAsState()
    val favoriteNames by graph.favorites.names.collectAsState()
    val saveLocally by graph.settings.saveLocally.collectAsState()
    LaunchedEffect(activeId) { vm.load() }
    var filter by remember { mutableStateOf("") }
    val rows = remember(vm.subreddits, vm.loading, vm.failure, filter, favoriteNames, activeId) {
        buildList {
            if (filter.isBlank()) add(ProfileRow.Links)
            val filtered = if (filter.isBlank()) vm.subreddits else vm.subreddits.filter { it.displayName.contains(filter, ignoreCase = true) }
            when {
                activeId == null -> add(ProfileRow.Message(Icons.Rounded.PersonOff, "No account connected", "Go to Settings › Accounts to add one."))
                vm.loading && vm.subreddits.isEmpty() -> add(ProfileRow.Loading)
                vm.failure != null && vm.subreddits.isEmpty() -> add(ProfileRow.Failed(vm.failure!!))
            }
            val favorites = filtered.filter { it.displayName.lowercase() in favoriteNames }.sortedBy { it.displayName.lowercase() }
            if (favorites.isNotEmpty()) { add(ProfileRow.Header("★")); favorites.forEach { add(ProfileRow.Sub(it)) } }
            filtered.filter { it.displayName.lowercase() !in favoriteNames }.groupBy { it.displayName.firstOrNull()?.uppercaseChar()?.toString() ?: "#" }.toSortedMap()
                .forEach { (letter, subs) -> add(ProfileRow.Header(letter)); subs.sortedBy { it.displayName.lowercase() }.forEach { add(ProfileRow.Sub(it)) } }
        }
    }
    val listState = androidx.compose.foundation.lazy.rememberLazyListState()
    val scope = rememberCoroutineScope()
    val letters = rows.filterIsInstance<ProfileRow.Header>().map { it.text }

    GlassScreen(bars = {
        Column(Modifier.align(Alignment.TopCenter)) {
            GlassTopBar("Profile") { GlassIconButton(Icons.Rounded.Settings, "Settings", onSettings) }
            GlassField(filter, { filter = it }, "Filter subreddits", Modifier.padding(horizontal = 16.dp))
        }
        // The index down the right edge: tap a letter to jump to its section.
        if (letters.size > 1) Column(Modifier.align(Alignment.CenterEnd).padding(end = 3.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            letters.forEach { l ->
                Text(l, color = Seekur.accent, style = com.everettpenne.seekur.ui.theme.IosType.caption2.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.Bold),
                    modifier = Modifier.clickable { val i = rows.indexOfFirst { it is ProfileRow.Header && it.text == l }; if (i >= 0) scope.launch { listState.animateScrollToItem(i) } }.padding(horizontal = 6.dp, vertical = 2.dp))
            }
        }
    }) {
        PullToRefreshBox(isRefreshing = false, onRefresh = { vm.load(force = true) }, modifier = Modifier.fillMaxSize()) {
            LazyColumn(Modifier.fillMaxSize(), state = listState, contentPadding = PaddingValues(top = topContentPadding() + 56.dp, bottom = bottomContentPadding(), start = 16.dp, end = 28.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)) {
                items(rows, key = { r -> when (r) { is ProfileRow.Header -> "h_${r.text}"; is ProfileRow.Sub -> "s_${r.subreddit.id}_${r.subreddit.displayName}"; else -> r::class.simpleName.orEmpty() } }) { r ->
                    when (r) {
                        ProfileRow.Links -> Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            LinkTile(Icons.Rounded.Inbox, "Inbox", Seekur.accent, onInbox, Modifier.weight(1f))
                            LinkTile(Icons.Rounded.Bookmark, if (saveLocally) "Saved here" else "Saved", Seekur.green, onSaved, Modifier.weight(1f))
                        }
                        is ProfileRow.Message -> EmptyState(r.icon, r.title, r.text)
                        ProfileRow.Loading -> LoadingRow()
                        is ProfileRow.Failed -> ErrorState(r.failure, onRetry = { vm.load(force = true) })
                        is ProfileRow.Header -> Text(r.text, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(start = 10.dp, top = 8.dp))
                        is ProfileRow.Sub -> SubredditRow(r.subreddit, nav, graph.favorites, favoriteNames)
                    }
                }
            }
        }
    }
}

@Composable
private fun LinkTile(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, color: Color, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Column(modifier.background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(24.dp)).clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(34.dp).background(color, androidx.compose.foundation.shape.CircleShape), Alignment.Center) { Icon(icon, null, tint = Color.White, modifier = Modifier.size(20.dp)) }
            Spacer(Modifier.weight(1f))
            Icon(Icons.Rounded.ChevronRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Text(title, style = MaterialTheme.typography.titleMedium)
    }
}

@Composable
private fun SubredditRow(sub: Subreddit, nav: Navigator, favorites: com.everettpenne.seekur.data.Favorites, names: Set<String>) {
    val starred = sub.displayName.lowercase() in names
    Row(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(24.dp)).clickable { nav.openSubreddit(sub) }.padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        SubredditIcon(sub, 44)
        Column(Modifier.weight(1f)) {
            Text(sub.displayNamePrefixed, style = MaterialTheme.typography.bodyLarge)
            if (sub.subscriberCount > 0) Text("${sub.formattedSubscribers} subscribers", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Icon(if (starred) Icons.Rounded.Star else Icons.Rounded.StarBorder, if (starred) "Remove favourite" else "Add favourite",
            tint = if (starred) Seekur.yellow else MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(28.dp).clickable { favorites.toggle(sub) })
    }
}
