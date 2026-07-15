package com.kickpredict.presentation.ads

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * Whether the ads SDK has finished initialising. Ad views (banner / interstitial) must not be created
 * before this is true: creating an [com.google.android.gms.ads.AdView] pulls in WebView, and doing so
 * on the first frame — before init completes — can jank hard enough to ANR on slow devices/emulators.
 */
object AdsState {
    var initialized by mutableStateOf(false)
        internal set
}
