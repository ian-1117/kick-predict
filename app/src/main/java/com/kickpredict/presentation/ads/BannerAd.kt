package com.kickpredict.presentation.ads

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdSize
import com.google.android.gms.ads.AdView

/**
 * Google's official **test** banner ad unit. Ads served from it are always safe to click and never
 * earn revenue. Replace with your real `ca-app-pub-XXXXXXXX/YYYYYYYY` from the AdMob console (and the
 * app id in AndroidManifest) before publishing — clicking your own live ads gets the account banned.
 */
private const val TEST_BANNER_UNIT = "ca-app-pub-3940256099942544/6300978111"

/** A standard AdMob banner, hosted in Compose via [AndroidView]. Loads once when it enters the tree. */
@Composable
fun BannerAd(modifier: Modifier = Modifier, adUnitId: String = TEST_BANNER_UNIT) {
    AndroidView(
        modifier = modifier.fillMaxWidth(),
        factory = { context ->
            AdView(context).apply {
                setAdSize(AdSize.BANNER)
                this.adUnitId = adUnitId
                loadAd(AdRequest.Builder().build())
            }
        },
    )
}
