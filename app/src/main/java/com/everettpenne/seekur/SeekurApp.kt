package com.everettpenne.seekur

import android.app.Application
import android.content.Context
import android.webkit.WebSettings
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import coil3.gif.AnimatedImageDecoder
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
import com.everettpenne.seekur.data.Favorites
import com.everettpenne.seekur.data.Settings
import com.everettpenne.seekur.data.api.RedditApi
import com.everettpenne.seekur.data.auth.AccountsManager
import com.everettpenne.seekur.data.auth.SecureStore
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

/** The app's shared parts, built once: the HTTP client, the encrypted store, the accounts, the Reddit client and the preferences. */
class Graph(app: Context) {
    val http: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS).readTimeout(30, TimeUnit.SECONDS).build()
    val settings = Settings(app)
    val favorites = Favorites(app)
    val localSaves = com.everettpenne.seekur.data.LocalSaves(app)
    val readPosts = com.everettpenne.seekur.data.ReadPosts(app)
    val accounts = AccountsManager(app, SecureStore(app))

    /** Posts the person has just seen, so a post opens at once while its comments load. Small and in memory only. */
    val postCache: MutableMap<String, com.everettpenne.seekur.data.model.Post> = java.util.Collections.synchronizedMap(
        object : LinkedHashMap<String, com.everettpenne.seekur.data.model.Post>(64, .75f, true) {
            override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, com.everettpenne.seekur.data.model.Post>?) = size > 120
        })

    /** The browser the person signs in with names itself in this string; requests carry the same one, not an invented one. */
    private val userAgent: String = runCatching { WebSettings.getDefaultUserAgent(app) }.getOrDefault(FALLBACK_USER_AGENT)
    val api = RedditApi(http, cookie = { accounts.activeCookie() }, userAgent = { userAgent }).also { accounts.api = it }

    private companion object {
        const val FALLBACK_USER_AGENT = "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0 Mobile Safari/537.36"
    }
}

class SeekurApp : Application(), SingletonImageLoader.Factory {
    lateinit var graph: Graph
        private set

    override fun onCreate() {
        super.onCreate()
        graph = Graph(this)
    }

    override fun newImageLoader(context: PlatformContext): ImageLoader = ImageLoader.Builder(context)
        .components {
            add(OkHttpNetworkFetcherFactory(callFactory = { graph.http }))
            add(AnimatedImageDecoder.Factory())
        }.build()
}

val Context.graph: Graph get() = (applicationContext as SeekurApp).graph
