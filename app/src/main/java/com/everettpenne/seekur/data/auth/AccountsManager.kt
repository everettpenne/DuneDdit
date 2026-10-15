package com.everettpenne.seekur.data.auth

import android.content.Context
import com.everettpenne.seekur.data.api.ApiResult
import com.everettpenne.seekur.data.api.RedditApi
import com.everettpenne.seekur.data.model.Account
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

/**
 * The signed-in Reddit accounts: each is a session cookie (kept encrypted in [SecureStore]) and what is known about the user. One is
 * active at a time and its cookie is what [RedditApi] sends. Mirrors Lurker's `CredentialsManager`.
 */
class AccountsManager(context: Context, private val secure: SecureStore) {
    private val prefs = context.getSharedPreferences("seekur_accounts", Context.MODE_PRIVATE)
    lateinit var api: RedditApi

    private val _accounts = MutableStateFlow<List<Account>>(emptyList())
    val accounts: StateFlow<List<Account>> = _accounts.asStateFlow()
    private val _activeId = MutableStateFlow<String?>(null)
    val activeId: StateFlow<String?> = _activeId.asStateFlow()

    init {
        _accounts.value = decode(prefs.getString(KEY_ACCOUNTS, null)).filter { secure.get(cookieKey(it.id)) != null }
        val saved = prefs.getString(KEY_ACTIVE, null)
        _activeId.value = _accounts.value.firstOrNull { it.id == saved }?.id ?: _accounts.value.firstOrNull()?.id
    }

    val active: Account? get() = _accounts.value.firstOrNull { it.id == _activeId.value }
    /** The active account's cookie, for the API client. */
    fun activeCookie(): String? = _activeId.value?.let { secure.get(cookieKey(it)) }
    val isSignedIn: Boolean get() = active != null

    /** Called with the `reddit_session` cookie the sign-in page produced: finds out who it is and adds (and activates) the account. */
    suspend fun addFromCookie(cookie: String): ApiResult<Account> {
        val id = UUID.randomUUID().toString()
        secure.put(cookieKey(id), cookie)
        val previous = _activeId.value
        _activeId.value = id               // so the "who am I" request is made with the new cookie
        api.modhash = null
        return when (val me = api.fetchMe()) {
            is ApiResult.Ok -> {
                val user = me.value
                // The same person signing in again replaces their old entry rather than doubling it.
                val old = _accounts.value.firstOrNull { it.userName.equals(user.name, ignoreCase = true) }
                if (old != null) { secure.remove(cookieKey(old.id)) }
                val account = Account(id, user.name, user.iconImg?.takeIf { it.isNotEmpty() }?.substringBefore('?'))
                _accounts.value = _accounts.value.filter { it.id != old?.id } + account
                persist(); ApiResult.Ok(account)
            }
            is ApiResult.Failed -> {
                secure.remove(cookieKey(id)); _activeId.value = previous; api.modhash = null
                me
            }
        }
    }

    fun setActive(id: String) {
        if (_accounts.value.none { it.id == id }) return
        _activeId.value = id; api.modhash = null; persist()
    }

    fun delete(id: String) {
        secure.remove(cookieKey(id))
        _accounts.value = _accounts.value.filter { it.id != id }
        if (_activeId.value == id) { _activeId.value = _accounts.value.firstOrNull()?.id; api.modhash = null }
        persist()
    }

    private fun persist() {
        prefs.edit().putString(KEY_ACCOUNTS, encode(_accounts.value)).putString(KEY_ACTIVE, _activeId.value).apply()
    }

    companion object {
        private const val KEY_ACCOUNTS = "accounts"
        private const val KEY_ACTIVE = "active"
        private fun cookieKey(id: String) = "cookie_$id"

        internal fun encode(accounts: List<Account>): String = JSONArray().also { arr ->
            accounts.forEach { arr.put(JSONObject().put("id", it.id).put("user", it.userName).put("pic", it.profilePicture)) }
        }.toString()

        internal fun decode(raw: String?): List<Account> = runCatching {
            val arr = JSONArray(raw ?: return emptyList())
            (0 until arr.length()).map { i ->
                val o = arr.getJSONObject(i)
                Account(o.getString("id"), o.optString("user").takeIf { it.isNotEmpty() && !o.isNull("user") },
                    o.optString("pic").takeIf { it.isNotEmpty() && !o.isNull("pic") })
            }
        }.getOrDefault(emptyList())
    }
}
