package com.everettpenne.seekur.data.api

/** Why a request failed, in terms a person can act on. Lurker only returned nil; the app is easier to use when it can say why. */
enum class Failure(val message: String) {
    OFFLINE("Can't reach Reddit. Check your connection."),
    SIGNED_OUT("You need to sign in for this. Add an account in Settings."),
    BLOCKED("Reddit refused the request. It may be blocking this app or your session; signing in again can help."),
    RATE_LIMITED("Reddit says you're going too fast. Try again in a moment."),
    NOT_FOUND("That doesn't exist, or it's private."),
    BAD_RESPONSE("Reddit sent something unexpected."),
}

sealed interface ApiResult<out T> {
    data class Ok<T>(val value: T) : ApiResult<T>
    data class Failed(val reason: Failure, val detail: String? = null) : ApiResult<Nothing>

    val valueOrNull: T? get() = (this as? Ok)?.value
    fun <R> map(transform: (T) -> R): ApiResult<R> = when (this) {
        is Ok -> Ok(transform(value))
        is Failed -> this
    }
}
