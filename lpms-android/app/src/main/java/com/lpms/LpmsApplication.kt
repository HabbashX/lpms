package com.lpms

import android.app.Application
import com.lpms.data.sync.SyncEngine
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

@HiltAndroidApp
class LpmsApplication : Application() {

    /**
     * Drives the offline queue. Injected after [super.onCreate], which is when
     * Hilt has finished generating the singleton component.
     */
    @Inject
    lateinit var syncEngine: SyncEngine

    override fun onCreate() {
        super.onCreate()
        syncEngine.start()
    }
}
