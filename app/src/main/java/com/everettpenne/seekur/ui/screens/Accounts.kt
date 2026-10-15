package com.everettpenne.seekur.ui.screens

import android.annotation.SuppressLint
import android.webkit.CookieManager
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import coil3.compose.AsyncImage
import com.everettpenne.seekur.data.api.ApiResult
import com.everettpenne.seekur.graph
import com.everettpenne.seekur.ui.glass.*
import com.everettpenne.seekur.ui.theme.Seekur
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** The signed-in accounts: pick the active one, remove one, or add another through the sign-in page. */
@Composable
fun AccountsScreen(onBack: () -> Unit, onAdd: () -> Unit) {
    val accounts = LocalContext.current.graph.accounts
    val list by accounts.accounts.collectAsState()
    val active by accounts.activeId.collectAsState()
    var confirmDelete by remember { mutableStateOf<String?>(null) }
    GlassScreen(bars = { GlassCenteredTopBar("Accounts", onBack = onBack, modifier = Modifier.align(Alignment.TopCenter)) }) {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(top = topContentPadding(), bottom = bottomContentPadding(), start = 16.dp, end = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (list.isEmpty()) EmptyState(Icons.Rounded.PersonOff, "No accounts", "Sign in to Reddit to see your feed, vote and comment.")
            else Column(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(16.dp))) {
                list.forEach { a ->
                    Row(Modifier.fillMaxWidth().clickable { accounts.setActive(a.id) }.padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Box(Modifier.size(40.dp).clip(CircleShape).background(Seekur.accent.copy(alpha = .15f)), Alignment.Center) {
                            if (a.profilePicture != null) AsyncImage(a.profilePicture, null, Modifier.fillMaxSize()) else Icon(Icons.Rounded.Person, null, tint = Seekur.accent)
                        }
                        Column(Modifier.weight(1f)) {
                            Text(a.userName?.let { "u/$it" } ?: "Reddit account", style = MaterialTheme.typography.bodyLarge)
                            if (a.id == active) Text("Active", style = MaterialTheme.typography.bodySmall, color = Seekur.green)
                        }
                        if (a.id == active) Icon(Icons.Rounded.CheckCircle, null, tint = Seekur.green)
                        Icon(Icons.Rounded.Delete, "Remove account", tint = MaterialTheme.colorScheme.error, modifier = Modifier.clip(CircleShape).clickable { confirmDelete = a.id }.padding(6.dp))
                    }
                }
            }
            Box(Modifier.fillMaxWidth(), Alignment.Center) { GlassButton("Add account", onAdd, prominent = true) }
            Text("Your session cookie is kept encrypted on this phone and is only ever sent to reddit.com.", style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 8.dp))
        }
    }
    confirmDelete?.let { id ->
        GlassSheet({ confirmDelete = null }) {
            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Text("Remove this account?", style = MaterialTheme.typography.titleLarge, color = glassPalette().ink)
                Text("You'll need to sign in again to use it.", color = glassPalette().ink.copy(alpha = .7f))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.End)) {
                    GlassButton("Cancel", { confirmDelete = null })
                    GlassButton("Remove", { accounts.delete(id); confirmDelete = null }, prominent = true)
                }
            }
        }
    }
}

/**
 * Reddit's own sign-in page in a web view. Nothing is typed into Seekur: once the page sets the `reddit_session` cookie, that cookie is
 * taken (to be kept encrypted), the web view's own copy is wiped, and the account is added. Like Lurker, it starts from a clean slate so
 * an old session in the web view is never mistaken for a new sign-in.
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun LoginScreen(onDone: () -> Unit, onCancel: () -> Unit) {
    val context = LocalContext.current
    val accounts = context.graph.accounts
    val scope = rememberCoroutineScope()
    var status by remember { mutableStateOf<String?>(null) }
    var working by remember { mutableStateOf(false) }
    val cookies = remember { CookieManager.getInstance() }
    var web by remember { mutableStateOf<WebView?>(null) }

    fun sessionCookie(): String? = cookies.getCookie("https://www.reddit.com")?.split(';')?.map { it.trim() }
        ?.firstOrNull { it.startsWith("reddit_session=") }?.removePrefix("reddit_session=")?.takeIf { it.isNotEmpty() }

    LaunchedEffect(Unit) { cookies.removeAllCookies(null); cookies.flush() }
    LaunchedEffect(web) {
        while (web != null && !working) {
            sessionCookie()?.let { cookie ->
                working = true
                val result = accounts.addFromCookie(cookie)
                cookies.removeAllCookies(null); cookies.flush()
                when (result) {
                    is ApiResult.Ok -> onDone()
                    is ApiResult.Failed -> { status = "Signed in, but Reddit wouldn't confirm who you are: ${result.reason.message}"; working = false; web?.loadUrl("https://www.reddit.com/login") }
                }
            }
            delay(700)
        }
    }
    GlassScreen(bars = {
        GlassCenteredTopBar("Sign in", subtitle = status ?: "Use Reddit's own page", onBack = onCancel, modifier = Modifier.align(Alignment.TopCenter))
    }) {
        Box(Modifier.fillMaxSize().padding(top = topContentPadding())) {
            AndroidView(modifier = Modifier.fillMaxSize(), factory = { c ->
                WebView(c).apply {
                    settings.javaScriptEnabled = true; settings.domStorageEnabled = true
                    settings.cacheMode = WebSettings.LOAD_NO_CACHE
                    cookies.setAcceptCookie(true); cookies.setAcceptThirdPartyCookies(this, true)
                    webViewClient = object : WebViewClient() {
                        override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest) = false
                    }
                    loadUrl("https://www.reddit.com/login"); web = this
                }
            }, onRelease = { it.stopLoading(); it.destroy() })
            if (working) Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background.copy(alpha = .85f)), Alignment.Center) { CircularProgressIndicator() }
        }
    }
}
