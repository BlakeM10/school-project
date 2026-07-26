package com.nextgen.courtvision

import android.app.Application
import com.google.firebase.FirebaseApp
import com.nextgen.courtvision.di.AppContainer

class CourtVisionApp : Application() {

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        // Returns null when google-services.json is absent; the container defers
        // Firebase access until first use so the app can still start in that state.
        FirebaseApp.initializeApp(this)
        container = AppContainer()
    }
}
