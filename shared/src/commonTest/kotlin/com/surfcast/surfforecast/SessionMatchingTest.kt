package com.surfcast.surfforecast

import kotlin.test.Test
import kotlin.test.assertTrue

class SessionMatchingTest {
    private fun v(kj: Int, wind: Int, cat: String, dir: Double = 2.0, tide: Int? = 40, period: Double = 10.0) =
        SessionVector(kj, wind, cat, dir, tide, period)

    @Test fun sameConditionsMatchNearlyPerfectly() {
        assertTrue(matchScore(v(214, 9, "offshore"), v(220, 10, "offshore")) >= 95.0)
    }

    @Test fun closeConditionsStillPassTheThreshold() {
        // Même type de vent, houle un peu plus petite, direction et marée proches.
        assertTrue(matchScore(v(214, 9, "offshore"), v(190, 11, "offshore", dir = 8.0, tide = 48)) >= PATTERN_MATCH_THRESHOLD)
    }

    @Test fun goodWindDoesNotCoverAMuchSmallerSwell() {
        // Le cas du 19 : 44 kJ contre 214 kJ, vent identique, direction et marée différentes.
        assertTrue(matchScore(v(214, 9, "offshore", dir = 2.0, tide = 79), v(44, 9, "offshore", dir = 22.0, tide = 26)) < 60.0)
    }

    @Test fun goodSwellDoesNotCoverADifferentWind() {
        assertTrue(matchScore(v(214, 9, "offshore"), v(215, 17, "side-off")) < PATTERN_MATCH_THRESHOLD)
        assertTrue(matchScore(v(214, 9, "offshore"), v(215, 12, "onshore")) < PATTERN_MATCH_THRESHOLD)
    }
}
