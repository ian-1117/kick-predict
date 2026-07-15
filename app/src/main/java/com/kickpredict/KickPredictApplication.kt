package com.kickpredict

import android.app.Application
import com.google.android.gms.ads.MobileAds
import com.kickpredict.di.AppContainer

/** Application entry point; owns the process-wide [AppContainer]. */
class KickPredictApplication : Application() {

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        // Initialise the ads SDK off the main thread; ad views wait for this internally.
        Thread { MobileAds.initialize(this) }.start()
    }
}
