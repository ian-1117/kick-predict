package com.kickpredict

import android.app.Application
import com.google.android.gms.ads.MobileAds
import com.kickpredict.di.AppContainer
import com.kickpredict.presentation.ads.AdsState

/** Application entry point; owns the process-wide [AppContainer]. */
class KickPredictApplication : Application() {

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        if (Features.ADS) {
            // Init the ads SDK; only after it completes do we let any ad view (and its WebView) be
            // created, so ads never block the app's first frame.
            MobileAds.initialize(this) { AdsState.initialized = true }
        }
    }
}
