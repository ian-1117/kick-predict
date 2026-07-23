package com.kickpredict

/** Build-wide feature switches. */
object Features {
    /**
     * Betting-adjacent features: the value-pick hub (ROI / CLV / Kelly / bankroll), accumulator +
     * saved parlays, the "Value" chip on fixtures, and the model-vs-market odds card + value edge on
     * the detail screen.
     *
     * Off for the store build so the app reads as a **prediction / analytics** product — this keeps it
     * out of the app stores' betting-tips category (which restricts distribution and blocks ad
     * monetisation) and clear for in-app ads. Flip to true to restore the full stack; all the code
     * stays in place behind this flag.
     */
    const val BETTING = false

    /** Show in-app banner ads. On for the store build; flip off for a clean/paid variant. */
    const val ADS = true
}
