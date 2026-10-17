package com.everettpenne.seekur.ui.components

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.everettpenne.seekur.data.api.Failure
import com.everettpenne.seekur.data.api.RedditApi

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

/**
 * The vote state of one thing (a post or comment), updated optimistically the moment the arrow is tapped. Each tap returns a
 * [Pending] carrying the state before it, so that when Reddit refuses the write the UI can roll back exactly that tap — and only
 * if nothing else has been tapped since ([version] guards the fast re-tap race).
 */
class VoteBarState(initialLikes: Boolean?, initialUps: Int) {
    var likes by mutableStateOf(initialLikes); private set
    var ups by mutableIntStateOf(initialUps); private set
    private var version = 0L

    data class Pending(val version: Long, val dir: RedditApi.Vote, val likes: Boolean?, val ups: Int)

    fun apply(up: Boolean): Pending {
        val (nextLikes, delta) = VoteMath.apply(likes, up)
        val pending = Pending(++version, when (nextLikes) { true -> RedditApi.Vote.UP; false -> RedditApi.Vote.DOWN; null -> RedditApi.Vote.NONE }, likes, ups)
        likes = nextLikes; ups += delta
        return pending
    }

    /** Undoes a refused vote, unless a newer tap already changed the state. */
    fun rollback(pending: Pending) { if (pending.version == version) { likes = pending.likes; ups = pending.ups } }
}

/** A short, person-sized reason for the rollback toast, instead of the fuller [Failure.message] prose. */
fun voteFailureMessage(reason: Failure): String = when (reason) {
    Failure.OFFLINE -> "No connection — vote not saved"
    Failure.SIGNED_OUT -> "Sign in to vote"
    Failure.RATE_LIMITED -> "Slow down — try again"
    else -> "Reddit refused your vote"
}

/** The same, for a save Reddit refused. */
fun saveFailureMessage(reason: Failure): String = when (reason) {
    Failure.OFFLINE -> "No connection — not saved"
    Failure.SIGNED_OUT -> "Sign in to save"
    else -> "Reddit refused to save"
}
