package com.everettpenne.seekur

import com.everettpenne.seekur.data.api.Failure
import com.everettpenne.seekur.data.api.RedditApi
import com.everettpenne.seekur.ui.components.VoteBarState
import com.everettpenne.seekur.ui.components.VoteMath
import com.everettpenne.seekur.ui.components.saveFailureMessage
import com.everettpenne.seekur.ui.components.voteFailureMessage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class VoteTest {
    @Test fun voteMathScoresMatchTheDocumentedBehaviour() {
        // (likes after, score delta)
        assertEquals(true to 1, VoteMath.apply(null, true))
        assertEquals(false to -1, VoteMath.apply(null, false))
        assertEquals(null to -1, VoteMath.apply(true, true))   // tapping up on an upvote removes it
        assertEquals(null to 1, VoteMath.apply(false, false))  // tapping down on a downvote removes it
        assertEquals(true to 2, VoteMath.apply(false, true))   // swinging the vote counts twice
        assertEquals(false to -2, VoteMath.apply(true, false))
    }

    @Test fun voteStateAppliesOptimisticallyAndRollsBackExactly() {
        val s = VoteBarState(null, 10)
        val up = s.apply(true)
        assertEquals(true, s.likes); assertEquals(11, s.ups); assertEquals(RedditApi.Vote.UP, up.dir)

        val undo = s.apply(true)
        assertNull(s.likes); assertEquals(10, s.ups); assertEquals(RedditApi.Vote.NONE, undo.dir)

        // Rolling back the older tap after a newer one is a no-op.
        s.rollback(up)
        assertNull(s.likes); assertEquals(10, s.ups)

        // Rolling back the current tap restores the state the vote started from.
        s.rollback(undo)
        assertEquals(true, s.likes); assertEquals(11, s.ups)
    }

    @Test fun rollbackKeepsTheNewerOfTwoQuickTaps() {
        val s = VoteBarState(null, 5)
        val first = s.apply(true)     // +1
        s.apply(false)                // swings: 5 -> down, score 4
        assertEquals(false, s.likes); assertEquals(4, s.ups)
        s.rollback(first)             // the first tap's refusal must not clobber the second tap
        assertEquals(false, s.likes); assertEquals(4, s.ups)
    }

    @Test fun failureMessagesAreShortAndActionable() {
        assertEquals("Sign in to vote", voteFailureMessage(Failure.SIGNED_OUT))
        assertEquals("No connection — vote not saved", voteFailureMessage(Failure.OFFLINE))
        assertEquals("Slow down — try again", voteFailureMessage(Failure.RATE_LIMITED))
        assertEquals("Reddit refused your vote", voteFailureMessage(Failure.BLOCKED))
        assertEquals("Sign in to save", saveFailureMessage(Failure.SIGNED_OUT))
    }
}
