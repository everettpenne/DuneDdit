package com.everettpenne.seekur.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Inbox
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.everettpenne.seekur.Graph
import com.everettpenne.seekur.data.api.ApiResult
import com.everettpenne.seekur.data.api.Failure
import com.everettpenne.seekur.data.api.postNavigation
import com.everettpenne.seekur.data.model.Message
import com.everettpenne.seekur.data.model.PostNavigation
import com.everettpenne.seekur.graph
import com.everettpenne.seekur.ui.glass.GlassScreen
import com.everettpenne.seekur.ui.glass.GlassCenteredTopBar
import com.everettpenne.seekur.util.timeAgo
import kotlinx.coroutines.launch

class InboxViewModel(private val graph: Graph) : ViewModel() {
    var messages by mutableStateOf<List<Message>>(emptyList()); private set
    var loading by mutableStateOf(false); private set
    var failure by mutableStateOf<Failure?>(null); private set
    private var after: String? = null
    private var started = false

    fun start() { if (!started) { started = true; load(reset = true) } }
    fun refresh() = load(reset = true)
    fun loadMore() { if (!loading && !after.isNullOrEmpty()) load(reset = false) }

    private fun load(reset: Boolean) {
        if (loading) return
        loading = true; failure = null
        viewModelScope.launch {
            when (val r = graph.api.fetchInbox(after = if (reset) null else after)) {
                is ApiResult.Ok -> { messages = (if (reset) emptyList() else messages) + r.value.first; after = r.value.second }
                is ApiResult.Failed -> failure = r.reason
            }
            loading = false
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InboxScreen(onBack: () -> Unit, onOpenPost: (PostNavigation) -> Unit) {
    val graph = LocalContext.current.graph
    val vm: InboxViewModel = viewModel { InboxViewModel(graph) }
    LaunchedEffect(vm) { vm.start() }
    GlassScreen(bars = { GlassCenteredTopBar("Inbox", onBack = onBack, modifier = Modifier.align(Alignment.TopCenter)) }) {
        PullToRefreshBox(isRefreshing = false, onRefresh = vm::refresh, modifier = Modifier.fillMaxSize()) {
            LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(top = topContentPadding(), bottom = bottomContentPadding(), start = 12.dp, end = 12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(vm.messages, key = { it.id }) { m ->
                    val target = m.postNavigation(m.subredditPrefixed?.removePrefix("r/"))
                    Row(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(16.dp))
                        .then(if (target != null) Modifier.clickable { onOpenPost(target) } else Modifier).padding(14.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        if (m.isNew) Box(Modifier.padding(top = 6.dp).size(9.dp).background(MaterialTheme.colorScheme.primary, CircleShape))
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(m.linkTitle ?: m.subject ?: "Message", style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                            Text(listOfNotNull(m.author?.let { "u/$it" }, m.subredditPrefixed, timeAgo(m.created)).joinToString(" · "),
                                style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            m.body?.let { Text(it, style = MaterialTheme.typography.bodyMedium, maxLines = 4, overflow = TextOverflow.Ellipsis) }
                        }
                    }
                    if (m == vm.messages.lastOrNull()) LaunchedEffect(m.id) { vm.loadMore() }
                }
                item {
                    when {
                        vm.loading -> LoadingRow()
                        vm.failure != null -> ErrorState(vm.failure!!, onRetry = vm::refresh)
                        vm.messages.isEmpty() -> EmptyState(Icons.Rounded.Inbox, "Inbox empty", "Replies and messages show up here.")
                    }
                }
            }
        }
    }
}
