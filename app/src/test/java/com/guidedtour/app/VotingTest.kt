package com.guidedtour.app

import com.guidedtour.app.data.vote
import org.junit.Assert.assertEquals
import org.junit.Test

class VotingTest {
    private val words = setOf("the", "temple", "was", "built", "for", "athena", "in", "432", "bc", "of", "marble")
    private fun isWord(w: String) = w in words

    @Test fun theMajorityMendsAWord() {
        val fixed = vote("The tcmple was built for Athena.", listOf("The temple was built for Athena." to 1f, "The temple was bui1t for Athena." to 1f), 1f, ::isWord)
        assertEquals("The temple was built for Athena.", fixed)
    }

    @Test fun oneDissentingReadingIsNotEnough() {
        assertEquals("The temple was built", vote("The temple was built", listOf("The tcmple was built" to 1f), 1f, ::isWord))
    }

    @Test fun wordsAddedOrMissedElsewhereDoNotVote() {
        val primary = "The temple was built in 432 BC."
        assertEquals(primary, vote(primary, listOf("The temple was built" to 1f, "Look: The great temple was built in 432 BC." to 1f), 1f, ::isWord))
    }

    @Test fun aRealWordIsNotOutvotedByANonWord() {
        assertEquals("of marble", vote("of marble", listOf("of rnarble" to 1f, "of rnarble" to 1f), 1f, ::isWord))
    }

    @Test fun onATieTheRealWordWins() {
        // The line read again counts as much as the first reading: where they differ, the real word is kept.
        assertEquals("the temple was built", vote("the tcmple was built", listOf("the temple was bui1t" to 1f), 1f, ::isWord))
    }

    @Test fun weightsCount() {
        // Two weak live readings don't outvote a strong photo reading...
        assertEquals("tcmple", vote("tcmple", listOf("temple" to 0.4f, "temple" to 0.4f), 1f, ::isWord))
        // ...but three do.
        assertEquals("temple", vote("tcmple", listOf("temple" to 0.4f, "temple" to 0.4f, "temple" to 0.4f), 1f, ::isWord))
    }

    @Test fun paragraphsAndPunctuationStay() {
        val fixed = vote("THE TCMPLE\n\nBuilt for Athena.", listOf("THE TEMPLE\n\nBuilt for Athena." to 1f, "THE TEMPLE Built for Athena." to 1f), 1f, ::isWord)
        assertEquals("THE TEMPLE\n\nBuilt for Athena.", fixed)
    }
}
