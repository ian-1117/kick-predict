package com.kickpredict.presentation.ads

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.os.SystemClock
import com.google.android.gms.ads.AdError
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.interstitial.InterstitialAd
import com.google.android.gms.ads.interstitial.InterstitialAdLoadCallback

/**
 * A frequency-capped interstitial, shown only at a natural break — returning from a match detail to
 * the list, never mid-reading and never on app exit. Caps: at least [MIN_CLOSES_BEFORE_AD] details
 * closed since the last ad, a [COOLDOWN_MS] cooldown between ads, and [SESSION_CAP] per process. The
 * first ad skips the cooldown (there's no previous one), so it can fire after just the close count.
 * Always keeps one ad preloaded so it shows instantly.
 */
object InterstitialAds {

    /** Google's official TEST interstitial unit — replace with your real id before publishing. */
    private const val TEST_INTERSTITIAL_UNIT = "ca-app-pub-3940256099942544/1033173712"

    private const val MIN_CLOSES_BEFORE_AD = 3
    private const val COOLDOWN_MS = 5 * 60 * 1000L // 5 minutes
    private const val SESSION_CAP = 6

    private var ad: InterstitialAd? = null
    private var loading = false
    private var closesSinceAd = 0
    private var lastShownElapsed = 0L
    private var shownThisSession = 0

    /** Load one interstitial if we don't have (or aren't already loading) one. Safe to call often. */
    fun preload(context: Context) {
        if (ad != null || loading) return
        loading = true
        InterstitialAd.load(
            context.applicationContext,
            TEST_INTERSTITIAL_UNIT,
            AdRequest.Builder().build(),
            object : InterstitialAdLoadCallback() {
                override fun onAdLoaded(loaded: InterstitialAd) { ad = loaded; loading = false }
                override fun onAdFailedToLoad(error: LoadAdError) { ad = null; loading = false }
            },
        )
    }

    /**
     * Call when the user leaves a detail screen back to the list. Shows the ad only if the caps allow;
     * otherwise just advances the counter and keeps one preloaded for later.
     */
    fun onDetailClosed(activity: Activity) {
        closesSinceAd++
        preload(activity)
        val ready = ad ?: return
        if (!eligible()) return

        ready.fullScreenContentCallback = object : FullScreenContentCallback() {
            override fun onAdDismissedFullScreenContent() { ad = null; preload(activity) }
            override fun onAdFailedToShowFullScreenContent(e: AdError) { ad = null; preload(activity) }
        }
        ready.show(activity)
        closesSinceAd = 0
        lastShownElapsed = SystemClock.elapsedRealtime()
        shownThisSession++
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

    private fun eligible(): Boolean {
        if (shownThisSession >= SESSION_CAP) return false
        if (closesSinceAd < MIN_CLOSES_BEFORE_AD) return false
        // First ad has no previous one, so the cooldown doesn't apply.
        if (lastShownElapsed != 0L && SystemClock.elapsedRealtime() - lastShownElapsed < COOLDOWN_MS) return false
        return true
    }
}
