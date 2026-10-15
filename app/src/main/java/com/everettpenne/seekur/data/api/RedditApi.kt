package com.everettpenne.seekur.data.api

import com.everettpenne.seekur.data.media.Comments
import com.everettpenne.seekur.data.media.Posts
import com.everettpenne.seekur.data.model.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.decodeFromJsonElement
import okhttp3.Call
import okhttp3.Callback
import okhttp3.FormBody
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.io.IOException
import kotlin.coroutines.resume

/**
 * Talks to Reddit's website JSON, signed in with the person's own `reddit_session` cookie, as Lurker does. This is not Reddit's
 * official OAuth API: it can be blocked or changed by Reddit at any time, and anonymous requests are refused. [cookie] supplies the
 * active account's cookie (null when signed out); [userAgent] should be the same browser engine the person signed in with.
 */
class RedditApi(
    private val client: OkHttpClient,
    private val cookie: () -> String?,
    private val userAgent: () -> String,
    private val baseUrl: String = "https://www.reddit.com",
) {
    /** The CSRF token Reddit wants on writes (voting, saving, commenting), learned from /user/me/about. */
    @Volatile var modhash: String? = null

    // ---- plumbing ---------------------------------------------------------------------------------------------------------

    private fun requestBuilder(url: okhttp3.HttpUrl): Request.Builder = Request.Builder().url(url)
        .header("User-Agent", userAgent()).header("Accept", "application/json")
        .also { b -> cookie()?.let { b.header("Cookie", "reddit_session=$it") } }

    private fun url(path: String, vararg query: Pair<String, String?>): okhttp3.HttpUrl {
        val builder = (baseUrl + path).toHttpUrl().newBuilder()
        for ((k, v) in query) if (v != null) builder.addQueryParameter(k, v)
        return builder.build()
    }

    /** What a request came back with, read and released entirely on OkHttp's own thread. */
    private class Raw(val code: Int, val body: String) { val ok: Boolean get() = code in 200..299 }

    /** Cancelling a call closes its socket, and closing a TLS socket writes to the network, which Android forbids on the main thread. */
    private val cancelScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /**
     * Sends [request] and reads the answer. Everything that touches the connection (reading the body, closing the response, cancelling the call)
     * happens off the calling thread: closing a response writes to the socket, and doing that on the main thread, where these suspend functions
     * are called from, killed the app with NetworkOnMainThreadException the first time an error response came back.
     */
    private suspend fun send(request: Request): Result<Raw> = suspendCancellableCoroutine { cont ->
        val call = client.newCall(request)
        cont.invokeOnCancellation { cancelScope.launch { call.cancel() } }
        call.enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) { if (cont.isActive) cont.resume(Result.failure(e)) }
            override fun onResponse(call: Call, response: Response) {
                val raw = runCatching { response.use { Raw(it.code, if (it.isSuccessful) it.body?.string().orEmpty() else "") } }
                if (cont.isActive) cont.resume(raw)
            }
        })
    }

    private fun failureFor(code: Int): Failure = when (code) {
        401 -> Failure.SIGNED_OUT
        403 -> Failure.BLOCKED
        404 -> Failure.NOT_FOUND
        429 -> Failure.RATE_LIMITED
        else -> Failure.BAD_RESPONSE
    }

    private suspend fun getText(url: okhttp3.HttpUrl): ApiResult<String> {
        val raw = send(requestBuilder(url).get().build()).getOrElse { return ApiResult.Failed(Failure.OFFLINE, it.message) }
        return if (raw.ok) ApiResult.Ok(raw.body) else ApiResult.Failed(failureFor(raw.code), "HTTP ${raw.code}")
    }

    private suspend inline fun <reified T> getJson(url: okhttp3.HttpUrl): ApiResult<T> {
        val text = when (val r = getText(url)) { is ApiResult.Ok -> r.value; is ApiResult.Failed -> return r }
        return try { ApiResult.Ok(RedditJson.decodeFromString<T>(text)) }
        catch (e: Exception) { ApiResult.Failed(Failure.BAD_RESPONSE, e.message) }
    }

    private suspend fun ensureModhash(): String? {
        modhash?.let { return it }
        fetchMe()
        return modhash
    }

    /** A write request; true when Reddit accepted it. Needs a session and the CSRF token. */
    private suspend fun post(path: String, vararg fields: Pair<String, String>): ApiResult<Unit> {
        if (cookie() == null) return ApiResult.Failed(Failure.SIGNED_OUT)
        val token = ensureModhash() ?: return ApiResult.Failed(Failure.SIGNED_OUT, "no modhash")
        val form = FormBody.Builder().apply { for ((k, v) in fields) add(k, v); add("raw_json", "1") }.build()
        val request = requestBuilder(url(path)).header("X-Modhash", token).post(form).build()
        val raw = send(request).getOrElse { return ApiResult.Failed(Failure.OFFLINE, it.message) }
        return if (raw.ok) ApiResult.Ok(Unit) else ApiResult.Failed(failureFor(raw.code), "HTTP ${raw.code}")
    }

    // ---- account ----------------------------------------------------------------------------------------------------------

    suspend fun fetchMe(): ApiResult<UserDto> =
        getJson<MeResponse>(url("/user/me/about.json")).map { me -> me.data }.also { r ->
            (r as? ApiResult.Ok)?.value?.modhash?.takeIf { it.isNotEmpty() }?.let { modhash = it }
        }

    // ---- reading ----------------------------------------------------------------------------------------------------------

    fun postsUrl(feed: PostFeed, sort: ListingSort, after: String?, limit: Int, username: String?): okhttp3.HttpUrl? {
        val path = when (feed) {
            PostFeed.Home -> "/${sort.apiPath}"
            PostFeed.Popular -> "/r/popular/${sort.apiPath}"
            PostFeed.All -> "/r/all/${sort.apiPath}"
            is PostFeed.OfSubreddit -> "/r/${feed.subreddit.displayName}/${sort.apiPath}"
            PostFeed.Saved -> "/user/${username ?: return null}/saved"
            is PostFeed.OfUser -> "/user/${feed.username}/submitted"
        }
        return url("$path.json", "limit" to limit.toString(), "raw_json" to "1", "sr_detail" to "1", "after" to after,
            "t" to (if (feed.canSort) sort.timeParameter else null))
    }

    suspend fun fetchPosts(feed: PostFeed, sort: ListingSort, after: String? = null, limit: Int = 25, username: String? = null): ApiResult<PostPage> {
        val url = postsUrl(feed, sort, after, limit, username) ?: return ApiResult.Failed(Failure.SIGNED_OUT)
        return getJson<Listing<PostDto>>(url).map { listing -> pageOf(listing) }
    }

    private fun pageOf(listing: Listing<PostDto>) =
        PostPage(listing.data.children.filter { it.kind == "t3" }.map { Posts.from(it.data) }, listing.data.after)

    suspend fun fetchPostWithComments(subreddit: String?, postId: String, commentId: String? = null, sort: CommentSort = CommentSort.CONFIDENCE,
                                      limit: Int = 100, depth: Int = 8): ApiResult<CommentsResult> {
        val cleanPost = postId.removePrefix("t3_")
        var path = if (subreddit != null) "/r/$subreddit/comments/$cleanPost" else "/comments/$cleanPost"
        if (commentId != null) path += "/comment/${commentId.removePrefix("t1_")}"
        val url = url("$path.json", "sort" to sort.id, "limit" to limit.toString(), "depth" to depth.toString(), "raw_json" to "1")
        val text = when (val r = getText(url)) { is ApiResult.Ok -> r.value; is ApiResult.Failed -> return r }
        return try {
            val root = RedditJson.parseToJsonElement(text) as? JsonArray ?: return ApiResult.Failed(Failure.BAD_RESPONSE)
            if (root.size < 2) return ApiResult.Failed(Failure.BAD_RESPONSE)
            val postListing = RedditJson.decodeFromJsonElement<Listing<PostDto>>(root[0])
            val commentListing = RedditJson.decodeFromJsonElement<RawListing>(root[1])
            ApiResult.Ok(CommentsResult(postListing.data.children.firstOrNull()?.data?.let(Posts::from), Comments.flatten(commentListing)))
        } catch (e: Exception) { ApiResult.Failed(Failure.BAD_RESPONSE, e.message) }
    }

    suspend fun searchSubreddits(query: String, limit: Int = 30): ApiResult<List<Subreddit>> {
        if (query.isBlank()) return ApiResult.Ok(emptyList())
        return getJson<Listing<SubredditDto>>(url("/subreddits/search.json", "q" to query, "limit" to limit.toString(), "sort" to "relevance", "raw_json" to "1"))
            .map { l -> l.data.children.filter { it.kind == "t5" }.map { Subreddit.from(it.data) } }
    }

    suspend fun searchPosts(query: String, subreddit: String? = null, limit: Int = 30): ApiResult<List<Post>> {
        if (query.isBlank()) return ApiResult.Ok(emptyList())
        val path = if (subreddit != null) "/r/$subreddit/search.json" else "/search.json"
        return getJson<Listing<PostDto>>(url(path, "q" to query, "limit" to limit.toString(), "sort" to "relevance", "type" to "link",
            "raw_json" to "1", "sr_detail" to "1", "restrict_sr" to (if (subreddit != null) "true" else null))).map { pageOf(it).posts }
    }

    suspend fun fetchSubredditAbout(name: String): ApiResult<Subreddit> =
        getJson<Thing<SubredditDto>>(url("/r/$name/about.json", "raw_json" to "1")).map { Subreddit.from(it.data) }

    suspend fun fetchUserSubreddits(): ApiResult<List<Subreddit>> {
        val all = ArrayList<Subreddit>(); var after: String? = null
        do {
            val page = when (val r = getJson<Listing<SubredditDto>>(url("/subreddits/mine/subscriber.json", "limit" to "100", "after" to after, "raw_json" to "1"))) {
                is ApiResult.Ok -> r.value; is ApiResult.Failed -> return r
            }
            all += page.data.children.map { Subreddit.from(it.data) }
            after = page.data.after
        } while (after != null)
        return ApiResult.Ok(all)
    }

    suspend fun fetchInbox(after: String? = null, limit: Int = 25): ApiResult<Pair<List<Message>, String?>> =
        getJson<Listing<MessageDto>>(url("/message/inbox.json", "mark" to "true", "count" to "0", "limit" to limit.toString(), "show" to "all",
            "sr_detail" to "1", "raw_json" to "1", "after" to after?.takeIf { it.isNotEmpty() })).map { l ->
            l.data.children.map { t -> t.data.toMessage() } to l.data.after
        }

    // ---- writing ----------------------------------------------------------------------------------------------------------

    enum class Vote(val dir: String) { UP("1"), NONE("0"), DOWN("-1") }

    suspend fun vote(vote: Vote, fullname: String) = post("/api/vote", "dir" to vote.dir, "id" to fullname, "api_type" to "json")
    suspend fun save(save: Boolean, fullname: String) = post(if (save) "/api/save" else "/api/unsave", "id" to fullname)
    suspend fun reply(text: String, parentFullname: String) = post("/api/comment", "api_type" to "json", "text" to text, "thing_id" to parentFullname)
    suspend fun subscribe(subredditName: String, subscribe: Boolean) =
        post("/api/subscribe", "action" to (if (subscribe) "sub" else "unsub"), "sr_name" to subredditName)
    suspend fun follow(username: String, follow: Boolean) = subscribe("u/$username", follow)
}

internal fun MessageDto.toMessage() = Message(
    id = id, subject = subject, author = author, subredditPrefixed = subredditNamePrefixed ?: subreddit?.let { "r/$it" }, body = body,
    linkTitle = linkTitle, context = context, parentId = parentId, type = type, created = createdUtc ?: 0.0, isNew = isNew ?: false)

/** Where a message leads: a reply to a post or a comment opens that post (and scrolls to the comment). */
fun Message.postNavigation(subredditName: String?): PostNavigation? {
    if (type != "post_reply" && type != "comment_reply") return null
    val postId = if (type == "post_reply" && parentId != null) parentId.removePrefix("t3_")
        else context?.split('/')?.let { if (it.size >= 5 && it[4].isNotEmpty()) it[4] else null } ?: return null
    return PostNavigation(postId, subredditName, id)
}
