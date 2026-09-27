package com.mobilestudio.app

import android.app.Application

class MobileStudioApp : Application() {
    override fun onCreate() {
        super.onCreate()
        // Room/DataStore are lazily created on first access (see StudioRepository /
        // SettingsRepository) — nothing to eagerly initialize here.
    }
}
