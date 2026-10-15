package com.everettpenne.seekur.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.unit.Dp
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.everettpenne.seekur.Graph
import com.everettpenne.seekur.data.api.ApiResult
import com.everettpenne.seekur.data.api.Failure
import com.everettpenne.seekur.data.model.*
import com.everettpenne.seekur.graph
import com.everettpenne.seekur.ui.components.*
import com.everettpenne.seekur.ui.glass.*
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/** One list of posts: its feed, sort, pages and state. Mirrors Lurker's `PostListDataSource`. */
class PostsViewModel(private val graph: Graph, val feed: PostFeed) : ViewModel() {
    var sort by mutableStateOf(graph.settings.initialSort(feed)); private set
    var posts by mutableStateOf<List<Post>>(emptyList()); private set
    var after by mutableStateOf<String?>(null); private set
    var loading by mutableStateOf(false); private set
    var refreshing by mutableStateOf(false); private set
    var failure by mutableStateOf<Failure?>(null); private set
    var query by mutableStateOf("")
    private var searching = false
    private var job: Job? = null
    private var started = false

    fun start() { if (!started) { started = true; load(reset = true) } }

    fun changeSort(next: ListingSort) {
        if (next == sort) return
        sort = next; graph.settings.rememberSort(feed, next); load(reset = true)
    }

    fun refresh() { refreshing = true; load(reset = true) }
    fun loadMore() { if (!loading && after != null && !searching) load(reset = false) }

    /** Search within the subreddit; an empty query goes back to the feed. */
    fun submitSearch() {
        val q = query.trim()
        if (q.isEmpty()) { clearSearch(); return }
        job?.cancel(); searching = true; loading = true; failure = null
        job = viewModelScope.launch {
            when (val r = graph.api.searchPosts(q, subreddit = feed.subredditName())) {
                is ApiResult.Ok -> { posts = r.value; after = null }
                is ApiResult.Failed -> failure = r.reason
            }
            loading = false
        }
    }

    fun clearSearch() { query = ""; if (searching) { searching = false; load(reset = true) } }

    private fun load(reset: Boolean) {
        job?.cancel()
        loading = true; failure = null
        if (reset) after = null
        val cursor = if (reset) null else after
        job = viewModelScope.launch {
            val username = graph.accounts.active?.userName
            when (val r = graph.api.fetchPosts(feed, sort, cursor, username = username)) {
                is ApiResult.Ok -> {
                    val seen = if (reset) emptySet() else posts.mapTo(HashSet()) { it.id }
                    posts = (if (reset) emptyList() else posts) + r.value.posts.filter { it.id !in seen }
                    after = r.value.after
                }
                is ApiResult.Failed -> failure = r.reason
            }
            loading = false; refreshing = false
        }
    }

    private fun PostFeed.subredditName() = (this as? PostFeed.OfSubreddit)?.subreddit?.displayName
}

@Composable
fun postsViewModel(feed: PostFeed): PostsViewModel {
    val graph = LocalContext.current.graph
    // A different account sees a different Home and Saved, so each account gets its own list.
    val account by graph.accounts.activeId.collectAsState()
    return viewModel(key = "posts-${feed.id}-$account") { PostsViewModel(graph, feed) }
}

/**
 * A feed of posts under a glass top bar: pull to refresh, endless scrolling, a sort menu, and for the front pages a switcher between
 * Home, Popular and All; inside a subreddit a search field and an info button.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PostsScreen(feed: PostFeed, nav: Navigator, onBack: (() -> Unit)? = null, onSwitchFeed: ((PostFeed) -> Unit)? = null, onSignIn: () -> Unit = {}) {
    val vm = postsViewModel(feed)
    LaunchedEffect(vm) { vm.start() }
    val listState = rememberLazyListState()
    val nearEnd by remember { derivedStateOf { val last = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0; last >= vm.posts.size - 4 } }
    LaunchedEffect(nearEnd, vm.posts.size) { if (nearEnd && vm.posts.isNotEmpty()) vm.loadMore() }
    var searchOpen by remember { mutableStateOf(false) }
    var sheet by remember { mutableStateOf<Sheet?>(null) }
    val signedIn by LocalContext.current.graph.accounts.activeId.collectAsState()
    // Home and Saved belong to an account; without one there is nothing to ask Reddit for.
    val needsAccount = signedIn == null && (feed == PostFeed.Home || feed == PostFeed.Saved)

    GlassScreen(bars = {
        Column(Modifier.align(Alignment.TopCenter)) {
            GlassTopBar(feed.displayName, onBack = onBack) {
                if (feed.supportsSearch) GlassIconButton(Icons.Rounded.Search, "Search this subreddit", { searchOpen = !searchOpen; if (!searchOpen) vm.clearSearch() })
                if (feed.canSort) SortMenu(vm.sort, feed.supportsBestSort, vm::changeSort)
                if (onSwitchFeed != null && !LocalContext.current.graph.settings.hideFeedSwitcher.collectAsState().value) FeedMenu(feed, onSwitchFeed)
                if (feed is PostFeed.OfSubreddit) GlassIconButton(Icons.Rounded.Info, "About ${feed.displayName}", { sheet = Sheet.Subreddit(feed.subreddit) })
                if (feed is PostFeed.OfUser) GlassIconButton(Icons.Rounded.Info, "About u/${feed.username}", { sheet = Sheet.User(feed.username) })
            }
            if (searchOpen) GlassField(vm.query, { vm.query = it }, "Search ${feed.displayName}", Modifier.padding(horizontal = 16.dp), onSubmit = vm::submitSearch)
        }
    }) {
        PullToRefreshBox(isRefreshing = vm.refreshing, onRefresh = vm::refresh, modifier = Modifier.fillMaxSize()) {
            LazyColumn(Modifier.fillMaxSize(), state = listState,
                contentPadding = PaddingValues(top = topContentPadding() + if (searchOpen) 56.dp else 0.dp, bottom = bottomContentPadding(), start = 8.dp, end = 8.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)) {
                itemsIndexed(vm.posts, key = { _, p -> p.id }) { _, post -> PostCard(post, nav, compact = true, modifier = Modifier.widthIn(max = 800.dp).fillMaxWidth()) }
                item {
                    when {
                        needsAccount -> Unit
                        vm.loading && vm.posts.isNotEmpty() -> LoadingRow()
                        vm.failure != null -> ErrorState(vm.failure!!, onRetry = { if (vm.posts.isEmpty()) vm.refresh() else vm.loadMore() })
                        !vm.loading && vm.posts.isEmpty() -> EmptyState(Icons.Rounded.Inbox, "Nothing here", "No posts to show.")
                    }
                }
            }
            if (vm.loading && vm.posts.isEmpty() && vm.failure == null && !needsAccount) Box(Modifier.fillMaxSize(), Alignment.Center) { CircularProgressIndicator() }
            if (needsAccount) Column(Modifier.fillMaxSize().padding(32.dp), Arrangement.Center, Alignment.CenterHorizontally) {
                EmptyState(Icons.Rounded.PersonOff, "Sign in to Reddit", "Your Home feed and saved posts need an account. Popular and All work with one too.")
                Spacer(Modifier.height(12.dp)); GlassButton("Add account", onSignIn, prominent = true)
            }
        }
    }
    when (val s = sheet) {
        is Sheet.Subreddit -> SubredditInfoSheet(s.subreddit) { sheet = null }
        is Sheet.User -> UserInfoSheet(s.username) { sheet = null }
        null -> Unit
    }
}

private sealed interface Sheet {
    data class Subreddit(val subreddit: com.everettpenne.seekur.data.model.Subreddit) : Sheet
    data class User(val username: String) : Sheet
}

@Composable
private fun SortMenu(current: ListingSort, supportsBest: Boolean, onSort: (ListingSort) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        GlassIconButton(Icons.Rounded.SwapVert, "Sort by ${current.label}", { open = true })
        GlassDropdownMenu(open, { open = false }) {
            (ListingSort.basic.filter { supportsBest || it != ListingSort.Best }).forEach { s ->
                GlassMenuItem(s.label, { open = false; onSort(s) }, selected = s == current)
            }
            ListingSort.topOptions.forEach { s -> GlassMenuItem("Top · ${s.label}", { open = false; onSort(s) }, selected = s == current) }
        }
    }
}

@Composable
private fun FeedMenu(current: PostFeed, onSwitch: (PostFeed) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        GlassIconButton(Icons.Rounded.ExpandCircleDown, "Switch feed", { open = true })
        GlassDropdownMenu(open, { open = false }) {
            PostFeed.frontPage.forEach { f ->
                GlassMenuItem(f.displayName, { open = false; onSwitch(f) }, selected = f.id == current.id,
                    icon = when (f) { PostFeed.Home -> Icons.Rounded.Home; PostFeed.Popular -> Icons.Rounded.TrendingUp; else -> Icons.Rounded.Public })
            }
        }
    }
}

@Composable
fun topContentPadding(): Dp = WindowInsets.statusBars.asPaddingValues().calculateTopPadding() + 64.dp

@Composable
fun bottomContentPadding(): Dp = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() + 104.dp

@Composable
fun LoadingRow() = Box(Modifier.fillMaxWidth().padding(16.dp), Alignment.Center) { CircularProgressIndicator(Modifier.size(28.dp), strokeWidth = 3.dp) }

@Composable
fun ErrorState(failure: Failure, onRetry: () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Icon(Icons.Rounded.CloudOff, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(40.dp))
        Text(failure.message, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        GlassButton("Try again", onRetry)
    }
}

@Composable
fun EmptyState(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, message: String) {
    Column(Modifier.fillMaxWidth().padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Icon(icon, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(44.dp))
        Text(title, style = MaterialTheme.typography.titleLarge)
        Text(message, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** A glass search field. */
@Composable
fun GlassField(value: String, onValueChange: (String) -> Unit, placeholder: String, modifier: Modifier = Modifier, onSubmit: () -> Unit = {}) {
    val ink = glassPalette().ink
    androidx.compose.foundation.text.BasicTextField(value, onValueChange, singleLine = true,
        textStyle = MaterialTheme.typography.bodyLarge.copy(color = ink), cursorBrush = androidx.compose.ui.graphics.SolidColor(ink),
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search), keyboardActions = KeyboardActions(onSearch = { onSubmit() }),
        modifier = modifier.fillMaxWidth().height(48.dp).glassSurface(androidx.compose.foundation.shape.RoundedCornerShape(percent = 50),
            tint = ink.copy(alpha = .06f).compositeOver(glassPalette().tint), blurRadius = 6f, lensHeight = 10f, lensAmount = 20f, shadowed = false),
        decorationBox = { inner ->
            Row(Modifier.padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Icon(Icons.Rounded.Search, null, tint = ink.copy(alpha = .6f), modifier = Modifier.size(20.dp))
                Box(Modifier.weight(1f)) { if (value.isEmpty()) Text(placeholder, color = ink.copy(alpha = .45f), style = MaterialTheme.typography.bodyLarge); inner() }
                if (value.isNotEmpty()) Icon(Icons.Rounded.Close, "Clear", tint = ink.copy(alpha = .6f), modifier = Modifier.size(20.dp).clickable { onValueChange("") })
            }
        })
}
