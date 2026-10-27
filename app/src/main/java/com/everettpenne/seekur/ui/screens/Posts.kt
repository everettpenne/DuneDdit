package com.everettpenne.seekur.ui.screens

import com.everettpenne.glass.frostedGlass
import com.everettpenne.glass.GlassRefreshIndicator
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.ui.graphics.compositeOver
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
import com.everettpenne.glass.*
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

    private val localSaved get() = feed == PostFeed.Saved && graph.settings.saveLocally.value

    fun start() { if (!started) { started = true; if (!localSaved) load(reset = true) } }

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
                is ApiResult.Ok -> { posts = r.value.distinctBy { it.id }; after = null }
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
                    posts = ((if (reset) emptyList() else posts) + r.value.posts.filter { it.id !in seen }).distinctBy { it.id }
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
    val graph = LocalContext.current.graph
    val signedIn by graph.accounts.activeId.collectAsState()
    val hideSwitcher by graph.settings.hideFeedSwitcher.collectAsState()
    val saveLocally by graph.settings.saveLocally.collectAsState()
    val localPosts by graph.localSaves.posts.collectAsState()
    // "Saved" is the phone's own list when saving on this device is on: no account and no network involved.
    val localList = feed == PostFeed.Saved && saveLocally
    // Home and Saved belong to an account; without one there is nothing to ask Reddit for.
    val needsAccount = signedIn == null && (feed == PostFeed.Home || (feed == PostFeed.Saved && !saveLocally))
    val posts = if (localList) localPosts else vm.posts

    GlassScreen(bars = {
        Column(Modifier.align(Alignment.TopCenter)) {
            GlassTopBar(feed.displayName, onBack = onBack) {
                if (feed.canSort || (onSwitchFeed != null && !hideSwitcher) || feed.supportsSearch || feed is PostFeed.OfSubreddit || feed is PostFeed.OfUser) GlassActionPill {
                    if (feed.supportsSearch) PillIcon(Icons.Rounded.Search, "Search this subreddit", { searchOpen = !searchOpen; if (!searchOpen) vm.clearSearch() })
                    if (feed.canSort) MenuPillIcon(vm.sort.icon(), "Sort by ${vm.sort.label}") { close ->
                        ListingSort.basic.filter { feed.supportsBestSort || it != ListingSort.Best }.forEach { s -> GlassMenuItem(s.label, { close(); vm.changeSort(s) }, icon = s.icon(), selected = s == vm.sort) }
                        MenuSection("Top")
                        ListingSort.topOptions.forEach { s -> GlassMenuItem("Top · ${s.label}", { close(); vm.changeSort(s) }, icon = s.icon(), selected = s == vm.sort) }
                    }
                    if (onSwitchFeed != null && !hideSwitcher) MenuPillIcon(feed.icon(), "Switch feed") { close ->
                        PostFeed.frontPage.forEach { f -> GlassMenuItem(f.displayName, { close(); onSwitchFeed(f) }, icon = f.icon(), selected = f.id == feed.id) }
                    }
                    if (feed is PostFeed.OfSubreddit) PillIcon(Icons.Rounded.Info, "About ${feed.displayName}", { sheet = Sheet.Subreddit(feed.subreddit) })
                    if (feed is PostFeed.OfUser) PillIcon(Icons.Rounded.Info, "About u/${feed.username}", { sheet = Sheet.User(feed.username) })
                }
            }
            if (searchOpen) GlassField(vm.query, { vm.query = it }, "Search ${feed.displayName}", Modifier.padding(horizontal = 16.dp), onSubmit = vm::submitSearch)
        }
    }) { contentPadding ->
        val pullState = androidx.compose.material3.pulltorefresh.rememberPullToRefreshState()
        PullToRefreshBox(isRefreshing = vm.refreshing, onRefresh = { if (!localList) vm.refresh() }, modifier = Modifier.fillMaxSize(), state = pullState,
            indicator = { GlassRefreshIndicator(pullState, vm.refreshing, contentPadding.calculateTopPadding()) }) {
            LazyColumn(Modifier.fillMaxSize(), state = listState,
                contentPadding = PaddingValues(top = contentPadding.calculateTopPadding() + if (searchOpen) 56.dp else 0.dp, bottom = contentPadding.calculateBottomPadding(), start = 8.dp, end = 8.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)) {
                if (localList) item { Text("Saved on this device", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(start = 8.dp)) }
                itemsIndexed(posts, key = { _, p -> p.id }) { _, post -> PostCard(post, nav, compact = true, modifier = Modifier.widthIn(max = 800.dp).fillMaxWidth()) }
                item {
                    when {
                        needsAccount -> Unit
                        localList && posts.isEmpty() -> EmptyState(Icons.Rounded.BookmarkBorder, "Nothing saved yet", "Posts you save with the bookmark stay on this phone.")
                        localList -> Unit
                        vm.loading && vm.posts.isNotEmpty() -> LoadingRow()
                        vm.failure != null && signedIn == null && vm.failure == Failure.BLOCKED -> SignInToBrowse(onSignIn)
                        vm.failure != null -> ErrorState(vm.failure!!, onRetry = { if (vm.posts.isEmpty()) vm.refresh() else vm.loadMore() })
                        !vm.loading && vm.posts.isEmpty() -> EmptyState(Icons.Rounded.Inbox, "Nothing here", "No posts to show.")
                    }
                }
            }
            if (vm.loading && vm.posts.isEmpty() && vm.failure == null && !needsAccount && !localList) Box(Modifier.fillMaxSize(), Alignment.Center) { CircularProgressIndicator() }
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

/** A [PillIcon] that opens a glass menu when tapped; [content] builds the rows and is handed a function that closes the menu. */
@Composable
fun MenuPillIcon(icon: androidx.compose.ui.graphics.vector.ImageVector, description: String, content: @Composable ColumnScope.(close: () -> Unit) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        PillIcon(icon, description, { open = true })
        GlassDropdownMenu(open, { open = false }) { content { open = false } }
    }
}

fun ListingSort.icon(): androidx.compose.ui.graphics.vector.ImageVector = when (this) {
    ListingSort.Best -> Icons.Rounded.EmojiEvents
    ListingSort.Hot -> Icons.Rounded.LocalFireDepartment
    ListingSort.New -> Icons.Rounded.Newspaper
    ListingSort.Controversial -> Icons.Rounded.SportsMartialArts
    is ListingSort.Top -> when (period) {
        TopPeriod.HOUR -> Icons.Rounded.Schedule; TopPeriod.DAY -> Icons.Rounded.WbSunny; TopPeriod.WEEK -> Icons.Rounded.DateRange
        TopPeriod.MONTH -> Icons.Rounded.CalendarMonth; TopPeriod.YEAR -> Icons.Rounded.Public; TopPeriod.ALL -> Icons.Rounded.AllInclusive
    }
}

fun PostFeed.icon(): androidx.compose.ui.graphics.vector.ImageVector = when (this) {
    PostFeed.Home -> Icons.Rounded.Home
    PostFeed.Popular -> Icons.Rounded.TrendingUp
    PostFeed.All -> Icons.Rounded.Public
    is PostFeed.OfSubreddit -> Icons.Rounded.Forum
    PostFeed.Saved -> Icons.Rounded.Bookmark
    is PostFeed.OfUser -> Icons.Rounded.Person
}

fun CommentSort.icon(): androidx.compose.ui.graphics.vector.ImageVector = when (this) {
    CommentSort.CONFIDENCE -> Icons.Rounded.LocalFireDepartment; CommentSort.TOP -> Icons.Rounded.EmojiEvents; CommentSort.NEW -> Icons.Rounded.Newspaper
    CommentSort.CONTROVERSIAL -> Icons.Rounded.SportsMartialArts; CommentSort.OLD -> Icons.Rounded.History; CommentSort.RANDOM -> Icons.Rounded.Casino
    CommentSort.QA -> Icons.Rounded.QuestionAnswer; CommentSort.LIVE -> Icons.Rounded.Sensors
}

@Composable
fun LoadingRow() = com.everettpenne.glass.GlassSkeleton(count = 3, rowHeight = 52.dp, modifier = Modifier.padding(horizontal = 4.dp))

@Composable
fun ErrorState(failure: Failure, onRetry: () -> Unit) =
    com.everettpenne.glass.GlassErrorState("Couldn't load", failure.message, onRetry, retryLabel = "Try again")

@Composable
fun EmptyState(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, message: String) =
    com.everettpenne.glass.GlassEmptyState(icon, title, message)

/** A signed-out person hitting a 403 (Reddit refuses anonymous requests) gets pointed at signing in, not left with "refused". */
@Composable
private fun SignInToBrowse(onSignIn: () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Icon(Icons.Rounded.PersonOff, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(44.dp))
        Text("Reddit needs you to sign in", style = MaterialTheme.typography.titleLarge)
        Text("Reddit refuses anonymous requests, so Popular and All need an account too.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        GlassButton("Add account", onSignIn, prominent = true)
    }
}

/** A quiet section label inside a glass menu, so the "Top · …" periods read as a group rather than nine flat rows. */
@Composable
private fun androidx.compose.foundation.layout.ColumnScope.MenuSection(label: String) {
    Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = 18.dp, top = 6.dp, bottom = 2.dp))
}

/** A glass search field. */
@Composable
fun GlassField(value: String, onValueChange: (String) -> Unit, placeholder: String, modifier: Modifier = Modifier, onSubmit: () -> Unit = {}) {
    val ink = glassPalette().ink
    androidx.compose.foundation.text.BasicTextField(value, onValueChange, singleLine = true,
        textStyle = MaterialTheme.typography.bodyLarge.copy(color = ink), cursorBrush = androidx.compose.ui.graphics.SolidColor(ink),
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search), keyboardActions = KeyboardActions(onSearch = { onSubmit() }),
        modifier = modifier.fillMaxWidth().height(48.dp).frostedGlass(androidx.compose.foundation.shape.RoundedCornerShape(percent = 50), lensHeight = 10f, lensAmount = 20f),
        decorationBox = { inner ->
            Row(Modifier.padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Icon(Icons.Rounded.Search, null, tint = ink.copy(alpha = .6f), modifier = Modifier.size(20.dp))
                Box(Modifier.weight(1f)) { if (value.isEmpty()) Text(placeholder, color = ink.copy(alpha = .45f), style = MaterialTheme.typography.bodyLarge); inner() }
                if (value.isNotEmpty()) Icon(Icons.Rounded.Close, "Clear", tint = ink.copy(alpha = .6f), modifier = Modifier.size(20.dp).clickable { onValueChange("") })
            }
        })
}
