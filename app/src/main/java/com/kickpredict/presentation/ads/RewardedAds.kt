package com.kickpredict.presentation.ads

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import com.google.android.gms.ads.AdError
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.rewarded.RewardedAd
import com.google.android.gms.ads.rewarded.RewardedAdLoadCallback

/**
 * An opt-in rewarded ad: the user watches it to unlock the European leagues for a while. Keeps one
 * preloaded so the "unlock" tap shows it instantly; [onReward] fires only if the user earns the
 * reward (watches enough), never on a dismiss-without-watching.
 */
object RewardedAds {

    /** Google's official TEST rewarded unit — replace with your real id before publishing. */
    private const val TEST_REWARDED_UNIT = "ca-app-pub-3940256099942544/5224354917"

    private var ad: RewardedAd? = null
    private var loading = false

    val isReady: Boolean get() = ad != null

    fun preload(context: Context) {
        if (ad != null || loading) return
        loading = true
        RewardedAd.load(
            context.applicationContext,
            TEST_REWARDED_UNIT,
            AdRequest.Builder().build(),
            object : RewardedAdLoadCallback() {
                override fun onAdLoaded(loaded: RewardedAd) { ad = loaded; loading = false }
                override fun onAdFailedToLoad(error: LoadAdError) { ad = null; loading = false }
            },
        )
    }

    /**
     * Show the rewarded ad. [onReward] runs when the reward is earned; [onUnavailable] runs if no ad
     * is loaded yet (caller can tell the user to retry). Reloads the next ad after it closes.
     */
    fun show(activity: Activity, onReward: () -> Unit, onUnavailable: () -> Unit = {}) {
        val ready = ad
        if (ready == null) {
            preload(activity)
            onUnavailable()
            return
        }
        ready.fullScreenContentCallback = object : FullScreenContentCallback() {
            override fun onAdDismissedFullScreenContent() { ad = null; preload(activity) }
            override fun onAdFailedToShowFullScreenContent(e: AdError) { ad = null; preload(activity) }
        }
        ready.show(activity) { onReward() }
    }
}

/** Unwrap a (possibly localized/themed) context to the hosting Activity, or null. */
fun Context.findActivity(): Activity? {
    var ctx: Context? = this
    while (ctx is ContextWrapper) {
        if (ctx is Activity) return ctx
        ctx = ctx.baseContext
    }
    return null
}
