package com.everettpenne.seekur.data

import android.content.Context
import com.everettpenne.seekur.data.model.ListingSort
import com.everettpenne.seekur.data.model.PostFeed
import com.everettpenne.seekur.data.model.Subreddit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** The app's preferences, as flows the screens watch. The same switches Lurker has: remember feed and sort, autoplay, mute on play. */
class Settings(context: Context) {
    private val prefs = context.getSharedPreferences("seekur_settings", Context.MODE_PRIVATE)

    private fun flag(key: String, default: Boolean): MutableStateFlow<Boolean> = MutableStateFlow(prefs.getBoolean(key, default))
    private fun set(key: String, flow: MutableStateFlow<Boolean>, value: Boolean) { flow.value = value; prefs.edit().putBoolean(key, value).apply() }

    private val _persistFeed = flag("persistFeed", false)
    private val _persistSort = flag("persistSort", false)
    private val _hideFeedSwitcher = flag("hideFeedSwitcher", false)
    private val _autoplay = flag("autoplay", true)
    private val _muteOnPlay = flag("muteOnPlay", false)
    private val _blurNsfw = flag("blurNsfw", true)
    private val _saveLocally = flag("saveLocally", false)

    val persistFeed: StateFlow<Boolean> = _persistFeed.asStateFlow()
    val persistSort: StateFlow<Boolean> = _persistSort.asStateFlow()
    val hideFeedSwitcher: StateFlow<Boolean> = _hideFeedSwitcher.asStateFlow()
    val autoplay: StateFlow<Boolean> = _autoplay.asStateFlow()
    val muteOnPlay: StateFlow<Boolean> = _muteOnPlay.asStateFlow()
    val blurNsfw: StateFlow<Boolean> = _blurNsfw.asStateFlow()
    /** When on, saving a post keeps it on this phone only and never touches the Reddit account. */
    val saveLocally: StateFlow<Boolean> = _saveLocally.asStateFlow()

    fun setPersistFeed(v: Boolean) = set("persistFeed", _persistFeed, v)
    fun setPersistSort(v: Boolean) = set("persistSort", _persistSort, v)
    fun setHideFeedSwitcher(v: Boolean) = set("hideFeedSwitcher", _hideFeedSwitcher, v)
    fun setAutoplay(v: Boolean) = set("autoplay", _autoplay, v)
    fun setMuteOnPlay(v: Boolean) = set("muteOnPlay", _muteOnPlay, v)
    fun setBlurNsfw(v: Boolean) = set("blurNsfw", _blurNsfw, v)
    fun setSaveLocally(v: Boolean) = set("saveLocally", _saveLocally, v)

    /** The front-page feed to open with: the last one when "remember" is on, otherwise Home. */
    fun initialFeed(): PostFeed =
        if (_persistFeed.value) PostFeed.frontPage(prefs.getString("lastFeed", null)) ?: PostFeed.Home else PostFeed.Home
    fun rememberFeed(feed: PostFeed) { if (_persistFeed.value && feed.isFrontPage) prefs.edit().putString("lastFeed", feed.id).apply() }

    fun initialSort(feed: PostFeed): ListingSort {
        if (!feed.isFrontPage || !_persistSort.value) return feed.defaultSort
        val saved = ListingSort.from(prefs.getString("lastSort", null)) ?: return feed.defaultSort
        return if (saved == ListingSort.Best && !feed.supportsBestSort) feed.defaultSort else saved
    }
    fun rememberSort(feed: PostFeed, sort: ListingSort) { if (_persistSort.value && feed.isFrontPage) prefs.edit().putString("lastSort", sort.id).apply() }
}

/** Starred subreddits, kept by lower-cased name and shown first on the profile tab. */
class Favorites(context: Context) {
    private val prefs = context.getSharedPreferences("seekur_favorites", Context.MODE_PRIVATE)
    private val _names = MutableStateFlow(prefs.getStringSet("names", emptySet()).orEmpty().toSet())
    val names: StateFlow<Set<String>> = _names.asStateFlow()

    fun isFavorite(subreddit: Subreddit) = subreddit.displayName.lowercase() in _names.value
    fun toggle(subreddit: Subreddit) {
        val key = subreddit.displayName.lowercase()
        _names.value = if (key in _names.value) _names.value - key else _names.value + key
        prefs.edit().putStringSet("names", _names.value).apply()
    }
}
