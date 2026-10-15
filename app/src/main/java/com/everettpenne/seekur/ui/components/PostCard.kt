package com.everettpenne.seekur.ui.components

import androidx.compose.material.icons.automirrored.rounded.*
import android.content.Intent
import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.everettpenne.seekur.data.api.ApiResult
import com.everettpenne.seekur.data.api.RedditApi
import com.everettpenne.seekur.data.model.*
import com.everettpenne.seekur.graph
import com.everettpenne.seekur.ui.glass.GlassDropdownMenu
import com.everettpenne.seekur.ui.glass.GlassMenuItem
import com.everettpenne.seekur.ui.theme.Seekur
import com.everettpenne.seekur.util.compact
import com.everettpenne.seekur.util.timeAgo
import kotlinx.coroutines.launch

/** Where a tap on a post's parts goes; the root supplies it. */
interface Navigator {
    fun openPost(post: Post)
    fun openSubreddit(subreddit: Subreddit)
    fun openUser(username: String)
}

/** A post: title, flair, text, media, and the footer with subreddit, counts, save and vote. [compact] is the feed's card; otherwise the post's own page. */
@Composable
fun PostCard(post: Post, nav: Navigator, compact: Boolean, modifier: Modifier = Modifier, onReply: (() -> Unit)? = null, nested: Boolean = false) {
    val context = LocalContext.current
    val api = context.graph.api
    val scope = rememberCoroutineScope()
    val blurNsfw by context.graph.settings.blurNsfw.collectAsState()
    var saved by remember(post.id) { mutableStateOf(post.saved) }
    var menu by remember { mutableStateOf(false) }
    var revealed by remember(post.id) { mutableStateOf(false) }

    fun toggleSave() = scope.launch {
        if (api.save(!saved, post.fullname) is ApiResult.Ok) saved = !saved
    }

    Box(modifier) {
        Column(Modifier.fillMaxWidth()
            .then(if (compact && !nested) Modifier.clip(RoundedCornerShape(16.dp)).background(MaterialTheme.colorScheme.surfaceVariant) else Modifier)
            .then(if (compact) Modifier.combinedClickable(onClick = { nav.openPost(post) }, onLongClick = { menu = true }) else Modifier)
            .padding(if (compact && !nested) 12.dp else 0.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(post.title, style = MaterialTheme.typography.titleMedium, fontSize = 19.sp, maxLines = if (compact) 3 else Int.MAX_VALUE, overflow = TextOverflow.Ellipsis)
            post.linkFlairText?.let { flair ->
                Text(flair, style = MaterialTheme.typography.labelSmall,
                    color = if (post.flairBackground != null && post.flairTextLight) Color.White else Color.Black,
                    modifier = Modifier.background(post.flairBackground?.let { Color(it) } ?: Color(0xFFD5D7D9), RoundedCornerShape(4.dp)).padding(horizontal = 6.dp, vertical = 2.dp))
            }
            if (!compact) Text("u/${post.author}", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.clickable { nav.openUser(post.author) })
            if (post.selftext.isNotEmpty()) MarkdownText(post.selftext, compact = compact, selectable = !compact)
            if (post.media != MediaType.None) {
                val hide = blurNsfw && post.isNsfw && !revealed
                Box {
                    PostMedia(post.media, nsfwBlur = hide, modifier = if (hide) Modifier.blur(28.dp) else Modifier,
                        repost = { original -> PostCard(original, nav, compact = true, nested = true) })
                    if (hide) Text("NSFW · tap to reveal", color = Color.White, style = MaterialTheme.typography.labelMedium,
                        modifier = Modifier.align(Alignment.Center).background(Color.Black.copy(alpha = .6f), CircleShape).clickable { revealed = true }.padding(horizontal = 14.dp, vertical = 8.dp))
                }
            }
            Box(Modifier.fillMaxWidth().height(1.dp).background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = .6f)))
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                SubredditIcon(post.subreddit, 32) { nav.openSubreddit(post.subreddit) }
                Column(Modifier.weight(1f)) {
                    Text(post.subreddit.displayNamePrefixed, style = MaterialTheme.typography.labelMedium, color = post.subreddit.keyColor?.let { Color(it) } ?: MaterialTheme.colorScheme.primary,
                        maxLines = 1, modifier = Modifier.clickable { nav.openSubreddit(post.subreddit) })
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                        Stat(Icons.Rounded.ChatBubbleOutline, post.numComments.compact())
                        Stat(Icons.Rounded.Schedule, timeAgo(post.created))
                    }
                }
                if (onReply != null) CircleButton(Icons.AutoMirrored.Rounded.Reply, "Reply", onReply)
                CircleButton(if (saved) Icons.Rounded.Bookmark else Icons.Rounded.BookmarkBorder, if (saved) "Unsave" else "Save", { toggleSave() },
                    tint = if (saved) Seekur.green else MaterialTheme.colorScheme.onSurfaceVariant)
                VoteBar(post.likes, post.ups, post.fullname)
            }
        }
        GlassDropdownMenu(menu, { menu = false }) {
            GlassMenuItem("u/${post.author}", { menu = false; nav.openUser(post.author) }, icon = Icons.Rounded.Person)
            GlassMenuItem(if (saved) "Unsave" else "Save", { menu = false; toggleSave() }, icon = if (saved) Icons.Rounded.Bookmark else Icons.Rounded.BookmarkBorder)
            GlassMenuItem("Share", {
                menu = false
                context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, post.redditUrl), null))
            }, icon = Icons.Rounded.Share)
        }
    }
}

@Composable
private fun Stat(icon: androidx.compose.ui.graphics.vector.ImageVector, text: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(14.dp))
        Text(text, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun CircleButton(icon: androidx.compose.ui.graphics.vector.ImageVector, description: String, onClick: () -> Unit, tint: Color = MaterialTheme.colorScheme.primary) {
    Box(Modifier.size(38.dp).background(MaterialTheme.colorScheme.onSurface.copy(alpha = .07f), CircleShape).clickable(onClick = onClick).semantics { contentDescription = description },
        contentAlignment = Alignment.Center) { Icon(icon, null, tint = tint, modifier = Modifier.size(20.dp)) }
}

@Composable
fun SubredditIcon(subreddit: Subreddit, size: Int, onClick: (() -> Unit)? = null) {
    val tint = subreddit.keyColor?.let { Color(it) } ?: MaterialTheme.colorScheme.primary
    Box(Modifier.size(size.dp).clip(CircleShape).background(tint.copy(alpha = .15f)).then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier),
        contentAlignment = Alignment.Center) {
        if (subreddit.iconUrl != null) coil3.compose.AsyncImage(subreddit.iconUrl, null, Modifier.fillMaxSize())
        else Text("r/", color = tint, fontWeight = FontWeight.Bold, fontSize = (size * .4f).sp)
    }
}

/**
 * Up and down arrows around the score. Votes show at once and are taken back if Reddit refuses them, as Lurker's `VoteActionViewModel`
 * does: a vote on a vote removes it, one on the opposite arrow swings the score by two.
 */
@Composable
fun VoteBar(initialLikes: Boolean?, initialUps: Int, fullname: String, small: Boolean = false) {
    val api = LocalContext.current.graph.api
    val haptics = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    var likes by remember(fullname) { mutableStateOf(initialLikes) }
    var ups by remember(fullname) { mutableIntStateOf(initialUps) }
    fun vote(up: Boolean) {
        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        val before = likes to ups
        val (nextLikes, delta) = VoteMath.apply(likes, up)
        likes = nextLikes; ups += delta
        scope.launch {
            val dir = when (nextLikes) { true -> RedditApi.Vote.UP; false -> RedditApi.Vote.DOWN; null -> RedditApi.Vote.NONE }
            if (api.vote(dir, fullname) !is ApiResult.Ok) { likes = before.first; ups = before.second }
        }
    }
    val iconSize = if (small) 18.dp else 22.dp
    Row(Modifier.background(MaterialTheme.colorScheme.onSurface.copy(alpha = .07f), CircleShape).padding(horizontal = 6.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(2.dp)) {
        Box(Modifier.size(if (small) 28.dp else 34.dp).clip(CircleShape).clickable { vote(true) }.semantics { contentDescription = "Upvote" }, contentAlignment = Alignment.Center) {
            Icon(Icons.Rounded.ArrowUpward, null, tint = if (likes == true) Seekur.indigo else MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(iconSize))
        }
        AnimatedContent(ups, label = "score") { n ->
            Text(n.compact(), fontWeight = FontWeight.SemiBold, fontSize = if (small) 13.sp else 15.sp,
                color = when (likes) { true -> Seekur.indigo; false -> Seekur.red; null -> MaterialTheme.colorScheme.onSurfaceVariant })
        }
        Box(Modifier.size(if (small) 28.dp else 34.dp).clip(CircleShape).clickable { vote(false) }.semantics { contentDescription = "Downvote" }, contentAlignment = Alignment.Center) {
            Icon(Icons.Rounded.ArrowDownward, null, tint = if (likes == false) Seekur.red else MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(iconSize))
        }
    }
}

object VoteMath {
    /** The vote state and score change after tapping up or down: (new likes, score delta). */
    fun apply(current: Boolean?, up: Boolean): Pair<Boolean?, Int> = when {
        up && current == true -> null to -1
        up && current == false -> true to 2
        up -> true to 1
        current == false -> null to 1
        current == true -> false to -2
        else -> false to -1
    }
}
