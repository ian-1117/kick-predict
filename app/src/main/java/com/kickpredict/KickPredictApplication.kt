package com.kickpredict

import android.app.Application
import com.kickpredict.di.AppContainer

/** Application entry point; owns the process-wide [AppContainer]. */
class KickPredictApplication : Application() {

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}
