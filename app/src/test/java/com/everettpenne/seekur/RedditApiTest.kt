package com.everettpenne.seekur

import com.everettpenne.seekur.data.api.*
import com.everettpenne.seekur.data.auth.AccountsManager
import com.everettpenne.seekur.data.model.*
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class RedditApiTest {
    private lateinit var server: MockWebServer
    private var cookie: String? = "sess123"
    private lateinit var api: RedditApi

    @Before fun setUp() {
        server = MockWebServer().apply { start() }
        api = RedditApi(OkHttpClient(), cookie = { cookie }, userAgent = { "TestAgent/1" }, baseUrl = server.url("/").toString().trimEnd('/'))
    }
    @After fun tearDown() { server.shutdown() }

    private fun json(body: String, code: Int = 200) = MockResponse().setResponseCode(code).setBody(body)

    @Test fun postsAreRequestedWithTheSessionAgentAndParameters() = runBlocking {
        server.enqueue(json(Fixtures.listing(Fixtures.imagePost, Fixtures.selfPost, after = "t3_next")))
        val result = api.fetchPosts(PostFeed.Popular, ListingSort.Top(TopPeriod.WEEK), after = "t3_prev", limit = 10)
        val request = server.takeRequest()
        assertEquals("/r/popular/top.json", request.requestUrl!!.encodedPath)
        val q = request.requestUrl!!
        assertEquals("10", q.queryParameter("limit")); assertEquals("1", q.queryParameter("raw_json")); assertEquals("1", q.queryParameter("sr_detail"))
        assertEquals("t3_prev", q.queryParameter("after")); assertEquals("week", q.queryParameter("t"))
        assertEquals("reddit_session=sess123", request.getHeader("Cookie")); assertEquals("TestAgent/1", request.getHeader("User-Agent"))
        val page = (result as ApiResult.Ok).value
        assertEquals(listOf("abc123", "s1"), page.posts.map { it.id }); assertEquals("t3_next", page.after)
    }

    @Test fun everyFeedHasItsOwnPath() {
        fun path(feed: PostFeed, sort: ListingSort = ListingSort.Best, user: String? = null) = api.postsUrl(feed, sort, null, 25, user)?.encodedPath
        assertEquals("/best.json", path(PostFeed.Home)); assertEquals("/r/all/hot.json", path(PostFeed.All, ListingSort.Hot))
        assertEquals("/r/kotlin/new.json", path(PostFeed.OfSubreddit(Subreddit.named("kotlin")), ListingSort.New))
        assertEquals("/user/me/saved.json", path(PostFeed.Saved, user = "me")); assertNull(path(PostFeed.Saved))
        assertEquals("/user/zed/submitted.json", path(PostFeed.OfUser("zed")))
        assertNull(api.postsUrl(PostFeed.Home, ListingSort.Top(TopPeriod.DAY), null, 25, null)!!.queryParameter("t").takeIf { false })
    }

    @Test fun signedOutRequestsCarryNoCookie() = runBlocking {
        cookie = null
        server.enqueue(json(Fixtures.listing()))
        api.fetchPosts(PostFeed.All, ListingSort.Hot)
        assertNull(server.takeRequest().getHeader("Cookie"))
    }

    @Test fun httpStatusesBecomeReasons() = runBlocking {
        for ((code, reason) in listOf(401 to Failure.SIGNED_OUT, 403 to Failure.BLOCKED, 404 to Failure.NOT_FOUND, 429 to Failure.RATE_LIMITED, 500 to Failure.BAD_RESPONSE)) {
            server.enqueue(json("{}", code))
            assertEquals(reason, (api.fetchPosts(PostFeed.All, ListingSort.Hot) as ApiResult.Failed).reason)
        }
    }

    @Test fun garbageIsABadResponseNotACrash() = runBlocking {
        server.enqueue(json("<html>blocked</html>"))
        assertEquals(Failure.BAD_RESPONSE, (api.fetchPosts(PostFeed.All, ListingSort.Hot) as ApiResult.Failed).reason)
    }

    @Test fun anUnreachableServerIsOffline() = runBlocking {
        server.shutdown()
        assertEquals(Failure.OFFLINE, (api.fetchPosts(PostFeed.All, ListingSort.Hot) as ApiResult.Failed).reason)
    }

    @Test fun commentsComeBackWithThePost() = runBlocking {
        server.enqueue(json(Fixtures.commentsResponse))
        val result = (api.fetchPostWithComments("pics", "t3_abc123", commentId = "t1_c_b", sort = CommentSort.TOP) as ApiResult.Ok).value
        val url = server.takeRequest().requestUrl!!
        assertEquals("/r/pics/comments/abc123/comment/c_b.json", url.encodedPath); assertEquals("top", url.queryParameter("sort"))
        assertEquals("100", url.queryParameter("limit")); assertEquals("8", url.queryParameter("depth"))
        assertEquals("abc123", result.post!!.id); assertEquals(4, result.comments.size)
    }

    @Test fun votingFetchesTheModhashOnceAndSendsItWithTheForm() = runBlocking {
        server.enqueue(json("""{"kind":"t2","data":{"id":"u1","name":"alice","modhash":"hash-9"}}"""))
        server.enqueue(json("{}")); server.enqueue(json("{}"))
        assertTrue(api.vote(RedditApi.Vote.UP, "t3_abc") is ApiResult.Ok)
        assertTrue(api.vote(RedditApi.Vote.NONE, "t3_abc") is ApiResult.Ok)
        assertEquals("/user/me/about.json", server.takeRequest().requestUrl!!.encodedPath)
        val vote = server.takeRequest()
        assertEquals("POST", vote.method); assertEquals("/api/vote", vote.requestUrl!!.encodedPath); assertEquals("hash-9", vote.getHeader("X-Modhash"))
        val body = vote.body.readUtf8()
        assertTrue(body, "dir=1" in body && "id=t3_abc" in body && "api_type=json" in body)
        assertEquals(3, server.requestCount)
    }

    @Test fun writesNeedASession() = runBlocking {
        cookie = null
        assertEquals(Failure.SIGNED_OUT, (api.save(true, "t3_x") as ApiResult.Failed).reason)
        assertEquals(0, server.requestCount)
    }

    @Test fun repliesAreEncodedAsAForm() = runBlocking {
        api.modhash = "h"
        server.enqueue(json("{}"))
        api.reply("a & b = c", "t1_parent")
        val body = server.takeRequest().body.readUtf8()
        assertTrue(body, "text=a%20%26%20b%20%3D%20c" in body && "thing_id=t1_parent" in body)
    }

    @Test fun searchAndSubscribeUseTheRightEndpoints() = runBlocking {
        server.enqueue(json(Fixtures.listing(Fixtures.imagePost)))
        api.searchPosts("kotlin", subreddit = "androiddev")
        val search = server.takeRequest().requestUrl!!
        assertEquals("/r/androiddev/search.json", search.encodedPath); assertEquals("true", search.queryParameter("restrict_sr")); assertEquals("link", search.queryParameter("type"))
        assertEquals(emptyList<Post>(), (api.searchPosts("   ") as ApiResult.Ok).value); assertEquals(1, server.requestCount)
        api.modhash = "h"; server.enqueue(json("{}")); api.follow("zed", true)
        val follow = server.takeRequest(); val body = follow.body.readUtf8()
        assertEquals("/api/subscribe", follow.requestUrl!!.encodedPath); assertTrue(body, "action=sub" in body && "sr_name=u%2Fzed" in body)
    }

    @Test fun inboxRepliesLeadToTheirPost() {
        val reply = Message("m1", null, "x", null, "hi", null, "/r/pics/comments/abc123/title/m1/", null, "comment_reply", 0.0, true)
        assertEquals(PostNavigation("abc123", "pics", "m1"), reply.postNavigation("pics"))
        val post = reply.copy(type = "post_reply", parentId = "t3_zzz")
        assertEquals("zzz", post.postNavigation("pics")!!.postId)
        assertNull(reply.copy(type = "unknown").postNavigation("pics"))
    }

    @Test fun accountListsSurviveSerialisation() {
        val accounts = listOf(Account("1", "alice", "https://x/p.png"), Account("2", null, null))
        assertEquals(accounts, AccountsManager.decode(AccountsManager.encode(accounts)))
        assertEquals(emptyList<Account>(), AccountsManager.decode("not json")); assertEquals(emptyList<Account>(), AccountsManager.decode(null))
    }
}
