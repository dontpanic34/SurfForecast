package com.surfcast.surfforecast

import kotlin.test.Test
import kotlin.test.assertTrue

class SessionMatchingTest {
    private fun v(kj: Int, wind: Int, cat: String, tide: Int? = 40) = SessionVector(kj, wind, cat, 5.0, tide)

    @Test fun sameConditionsMatchNearlyPerfectly() {
        assertTrue(matchScore(v(214, 9, "offshore"), v(220, 10, "offshore")) >= 95.0)
    }

    @Test fun goodSwellDoesNotCoverADifferentWind() {
        // Houle presque identique, mais vent léger offshore contre 17 km/h de côté : plus de « ressemble à ».
        assertTrue(matchScore(v(214, 9, "offshore"), v(215, 17, "side-off")) < PATTERN_MATCH_THRESHOLD)
        assertTrue(matchScore(v(214, 9, "offshore"), v(215, 12, "onshore")) < PATTERN_MATCH_THRESHOLD)
    }
}
