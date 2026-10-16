package com.everettpenne.seekur.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.everettpenne.seekur.data.api.ApiResult
import com.everettpenne.seekur.data.model.Subreddit
import com.everettpenne.seekur.graph
import com.everettpenne.seekur.ui.components.SubredditIcon
import com.everettpenne.glass.GlassButton
import com.everettpenne.glass.GlassSheet
import com.everettpenne.glass.glassPalette
import kotlinx.coroutines.launch

/** About a subreddit: its numbers and description, and subscribing. Details are fetched when a link only gave a name. */
@Composable
fun SubredditInfoSheet(initial: Subreddit, onDismiss: () -> Unit) {
    val api = LocalContext.current.graph.api
    val scope = rememberCoroutineScope()
    val ink = glassPalette().ink
    var sub by remember { mutableStateOf(initial) }
    var subscribed by remember { mutableStateOf(initial.isSubscribed) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(initial.displayName) {
        if (initial.publicDescription.isEmpty() && initial.subscriberCount == 0 && initial.displayName.isNotEmpty()) {
            (api.fetchSubredditAbout(initial.displayName) as? ApiResult.Ok)?.value?.let { sub = it; subscribed = it.isSubscribed }
        }
    }
    GlassSheet(onDismiss) {
        Column(Modifier.padding(horizontal = 20.dp).padding(bottom = 12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                SubredditIcon(sub, 48)
                Column {
                    Text(sub.displayNamePrefixed, style = MaterialTheme.typography.titleLarge, color = ink)
                    if (!sub.displayName.startsWith("u_") && sub.subscriberCount > 0) Text("${sub.formattedSubscribers} subscribers", color = ink.copy(alpha = .65f), style = MaterialTheme.typography.bodyMedium)
                }
            }
            Text(sub.publicDescription.ifEmpty { "No description available" }, color = ink.copy(alpha = .85f), style = MaterialTheme.typography.bodyLarge)
            error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium) }
            Box(Modifier.fillMaxWidth(), Alignment.Center) {
                if (busy) CircularProgressIndicator(Modifier.size(28.dp))
                else GlassButton(if (subscribed) "Unsubscribe" else "Subscribe", {
                    busy = true; error = null
                    scope.launch {
                        val isUser = sub.displayName.startsWith("u_")
                        val name = if (isUser) sub.displayName.removePrefix("u_") else sub.displayName
                        val r = if (isUser) api.follow(name, !subscribed) else api.subscribe(name, !subscribed)
                        if (r is ApiResult.Ok) subscribed = !subscribed else error = (r as? ApiResult.Failed)?.reason?.message
                        busy = false
                    }
                }, prominent = !subscribed)
            }
        }
    }
}

/** About a user: following them. */
@Composable
fun UserInfoSheet(username: String, onDismiss: () -> Unit) {
    val api = LocalContext.current.graph.api
    val scope = rememberCoroutineScope()
    val ink = glassPalette().ink
    var following by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    GlassSheet(onDismiss) {
        Column(Modifier.padding(horizontal = 20.dp).padding(bottom = 12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("u/$username", style = MaterialTheme.typography.titleLarge, color = ink)
            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            Box(Modifier.fillMaxWidth(), Alignment.Center) {
                if (busy) CircularProgressIndicator(Modifier.size(28.dp))
                else GlassButton(if (following) "Following" else "Follow", {
                    busy = true; error = null
                    scope.launch {
                        val r = api.follow(username, !following)
                        if (r is ApiResult.Ok) following = !following else error = (r as? ApiResult.Failed)?.reason?.message
                        busy = false
                    }
                }, prominent = !following)
            }
        }
    }
}
