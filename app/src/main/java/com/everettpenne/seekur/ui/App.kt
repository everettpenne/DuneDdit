package com.everettpenne.seekur.ui

import android.content.Intent
import android.net.Uri
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import com.kyant.backdrop.backdrops.layerBackdrop
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.unit.dp
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.UriHandler
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.everettpenne.seekur.data.model.Post
import com.everettpenne.seekur.data.model.PostFeed
import com.everettpenne.seekur.data.model.PostNavigation
import com.everettpenne.seekur.data.model.Subreddit
import com.everettpenne.seekur.graph
import com.everettpenne.media.ImageViewer
import com.everettpenne.media.LocalImageViewer
import com.everettpenne.media.VideoPip
import com.everettpenne.seekur.ui.components.Navigator
import com.everettpenne.seekur.ui.components.LocalToast
import com.everettpenne.seekur.ui.components.ToastOverlay
import com.everettpenne.glass.*
import com.everettpenne.seekur.ui.screens.*
import com.everettpenne.seekur.ui.theme.SeekurTheme
import com.everettpenne.seekur.util.LinkRouter
import com.everettpenne.seekur.util.LinkTarget

/** Where the navigation is told to go. Pure strings, so deep links and taps share them. */
object Routes {
    const val FEED = "feed"; const val PROFILE = "profile"; const val SEARCH = "search"
    const val INBOX = "inbox"; const val SETTINGS = "settings"; const val ACCOUNTS = "accounts"; const val LOGIN = "login"; const val SAVED = "list/saved"
    fun subreddit(name: String) = "list/subreddit/${Uri.encode(name)}"
    fun user(name: String) = "list/user/${Uri.encode(name)}"
    fun post(id: String, subreddit: String? = null, comment: String? = null) =
        "post/${Uri.encode(id)}?sub=${Uri.encode(subreddit ?: "")}&comment=${Uri.encode(comment ?: "")}"
}

@Composable
fun SeekurApp(deepLink: Uri?) {
    SeekurTheme {
        val updater = com.everettpenne.updater.Updater.get(LocalContext.current, com.everettpenne.updater.UpdateConfig("everettpenne/DuneDdit", com.everettpenne.seekur.BuildConfig.VERSION_NAME))
        // Debug builds skip the launch check so the emulator tests never meet a dialog; Settings > Check for Updates still works there.
        LaunchedEffect(Unit) { if (!com.everettpenne.seekur.BuildConfig.DEBUG) updater.checkOnLaunch() }
        com.everettpenne.updater.UpdateDialog(updater, "Seekur")
        val context = LocalContext.current
        val graph = context.graph
        val navController = rememberNavController()
        var viewer by remember { mutableStateOf<Pair<List<String>, Int>?>(null) }
        var frontFeed by remember { mutableStateOf<PostFeed>(graph.settings.initialFeed()) }
        var toast by remember { mutableStateOf<String?>(null) }
        LaunchedEffect(toast) { if (toast != null) { kotlinx.coroutines.delay(2600); toast = null } }

        val navigator = remember(navController) {
            object : Navigator {
                override fun openPost(post: Post) { graph.postCache[post.id] = post; navController.navigate(Routes.post(post.id, post.subreddit.displayName)) }
                override fun openSubreddit(subreddit: Subreddit) = navController.navigate(Routes.subreddit(subreddit.displayName))
                override fun openUser(username: String) = navController.navigate(Routes.user(username))
            }
        }
        val uriHandler = remember(navController) { AppUriHandler(context, navController) { urls -> viewer = urls to 0 } }
        LaunchedEffect(deepLink) { deepLink?.let { uriHandler.openUri(it.toString()) } }

        val backStack by navController.currentBackStackEntryAsState()
        val route = backStack?.destination?.route
        val tabs = Tab.entries.associateBy { it.route }
        var lastTab by remember { mutableStateOf(Tab.Feed) }
        LaunchedEffect(route) { tabs[route]?.let { lastTab = it } }
        val showTabBar = route != Routes.LOGIN

        val tabBar: (@Composable () -> Unit)? = if (showTabBar) ({
            GlassTabBar(Tab.bar, selected = lastTab, search = Tab.Search, onSelect = { tab ->
                navController.navigate(tab.route) { popUpTo(Routes.FEED) { inclusive = false }; launchSingleTop = true }
            })
        }) else null

        CompositionLocalProvider(LocalImageViewer provides { urls, i -> viewer = urls to i }, LocalUriHandler provides uriHandler, LocalTabBar provides tabBar,
            com.everettpenne.updater.LocalUpdater provides updater, LocalToast provides { toast = it }) {
            // The plain page colour is also a glass backdrop, so controls drawn inside a scrolling page still have a surface to bend. It records
            // ONLY the colour, in a sibling of the screens: recording the screens themselves would make any glass control inside them sample a
            // layer that contains it, which sends the renderer into endless recursion and crashes the app.
            val pageBackdrop = com.kyant.backdrop.backdrops.rememberLayerBackdrop()
            Box(Modifier.fillMaxSize()) {
                Box(Modifier.fillMaxSize().layerBackdrop(pageBackdrop).background(MaterialTheme.colorScheme.background))
                CompositionLocalProvider(LocalGlassBackdrop provides pageBackdrop) {
                    Box(Modifier.fillMaxSize()) {
                    NavHost(navController, startDestination = Routes.FEED, modifier = Modifier.fillMaxSize(),
                        enterTransition = { fadeIn() }, exitTransition = { fadeOut() }, popEnterTransition = { fadeIn() }, popExitTransition = { fadeOut() }) {
                            composable(Routes.FEED) {
                                PostsScreen(frontFeed, navigator, onSwitchFeed = { frontFeed = it; graph.settings.rememberFeed(it) }, onSignIn = { navController.navigate(Routes.LOGIN) })
                            }
                            composable(Routes.PROFILE) {
                                ProfileScreen(navigator, onInbox = { navController.navigate(Routes.INBOX) }, onSaved = { navController.navigate(Routes.SAVED) },
                                    onSettings = { navController.navigate(Routes.SETTINGS) })
                            }
                            composable(Routes.SEARCH) { SearchScreen(navigator) }
                            composable("list/subreddit/{name}", listOf(navArgument("name") { type = NavType.StringType })) { e ->
                                PostsScreen(PostFeed.OfSubreddit(Subreddit.named(e.arguments?.getString("name").orEmpty())), navigator, onBack = { navController.popBackStack() })
                            }
                            composable("list/user/{name}", listOf(navArgument("name") { type = NavType.StringType })) { e ->
                                PostsScreen(PostFeed.OfUser(e.arguments?.getString("name").orEmpty()), navigator, onBack = { navController.popBackStack() })
                            }
                            composable(Routes.SAVED) { PostsScreen(PostFeed.Saved, navigator, onBack = { navController.popBackStack() }, onSignIn = { navController.navigate(Routes.LOGIN) }) }
                            composable("post/{id}?sub={sub}&comment={comment}", listOf(
                                navArgument("id") { type = NavType.StringType },
                                navArgument("sub") { type = NavType.StringType; defaultValue = "" },
                                navArgument("comment") { type = NavType.StringType; defaultValue = "" })) { e ->
                                val a = e.arguments
                                PostDetailScreen(PostNavigation(a?.getString("id").orEmpty(), a?.getString("sub")?.takeIf { it.isNotEmpty() }, a?.getString("comment")?.takeIf { it.isNotEmpty() }),
                                    navigator, onBack = { navController.popBackStack() })
                            }
                            composable(Routes.INBOX) { InboxScreen(onBack = { navController.popBackStack() }, onOpenPost = { t -> navController.navigate(Routes.post(t.postId, t.subreddit, t.commentId)) }) }
                            composable(Routes.SETTINGS) { SettingsScreen(onBack = { navController.popBackStack() }, onAccounts = { navController.navigate(Routes.ACCOUNTS) }) }
                            composable(Routes.ACCOUNTS) { AccountsScreen(onBack = { navController.popBackStack() }, onAdd = { navController.navigate(Routes.LOGIN) }) }
                            composable(Routes.LOGIN) { LoginScreen(onDone = { navController.popBackStack() }, onCancel = { navController.popBackStack() }) }
                    }
                    ToastOverlay(toast, Modifier.align(Alignment.BottomCenter)
                        .padding(start = 16.dp, end = 16.dp, bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() + 104.dp))
                    }
                    // In picture-in-picture the small window owns the video through the shared Glass player.
                    if (VideoPip.active) VideoPip.player?.let { com.everettpenne.media.VideoSurface(it, Modifier.fillMaxSize()) }
                }
            }
            viewer?.let { (urls, i) -> ImageViewer(urls, i) { viewer = null } }
        }
    }
}

/** Sends a tapped link where it belongs: Reddit content inside the app, pictures to the viewer, everything else to the browser. */
class AppUriHandler(
    private val context: android.content.Context,
    private val nav: NavHostController,
    private val showImages: (List<String>) -> Unit,
) : UriHandler {
    override fun openUri(uri: String) {
        when (val target = LinkRouter.parse(uri)) {
            is LinkTarget.Post -> nav.navigate(Routes.post(target.id, target.subreddit, target.commentId))
            is LinkTarget.Subreddit -> nav.navigate(Routes.subreddit(target.name))
            is LinkTarget.User -> nav.navigate(Routes.user(target.name))
            is LinkTarget.Image -> showImages(listOf(target.url))
            is LinkTarget.External -> runCatching {
                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(target.url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            }
        }
    }
}
