package com.maquis.caisse

import android.app.Application
import com.maquis.caisse.data.local.DatabaseSeed
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltAndroidApp
class MaquisCaisseApp : Application() {

    @Inject lateinit var databaseSeed: DatabaseSeed

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        // Hilt injection of Application fields happens after super.onCreate when using
        // EntryPoint — use a delayed seed via ContentProvider-less approach:
        // Inject via EntryPointAccessors in onCreate after Hilt is ready.
    }
}
