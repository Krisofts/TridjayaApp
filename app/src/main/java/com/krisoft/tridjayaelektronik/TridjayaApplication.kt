package com.krisoft.tridjayaelektronik

import android.app.Application
import com.krisoft.tridjayaelektronik.data.TokenStore
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@HiltAndroidApp
class TridjayaApplication : Application() {

    @Inject lateinit var tokenStore: TokenStore

    override fun onCreate() {
        super.onCreate()
        // Seed the encrypted DataStore (and run the one-time legacy migration) off the main thread,
        // so the Keystore/crypto + disk cost is paid here instead of blocking MainActivity's first
        // frame — and so `sessionState` is resolved before the splash decides login vs. main.
        CoroutineScope(Dispatchers.IO).launch { tokenStore.warmUp() }
    }
}
