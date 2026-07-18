package com.krisoft.tridjayaelektronik

import android.app.Application
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.disk.DiskCache
import coil.memory.MemoryCache
import com.krisoft.tridjayaelektronik.data.TokenStore
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@HiltAndroidApp
class TridjayaApplication : Application(), ImageLoaderFactory {

    @Inject lateinit var tokenStore: TokenStore

    override fun onCreate() {
        super.onCreate()
        // Seed the encrypted DataStore (and run the one-time legacy migration) off the main thread,
        // so the Keystore/crypto + disk cost is paid here instead of blocking MainActivity's first
        // frame — and so `sessionState` is resolved before the splash decides login vs. main.
        CoroutineScope(Dispatchers.IO).launch { tokenStore.warmUp() }
    }

    /** ImageLoader terpusat untuk semua AsyncImage (foto produk list/detail/flyer, bukti indent):
     *  cache memori + disk eksplisit dan crossfade halus, dibangun lazy oleh Coil saat gambar
     *  pertama diminta — bukan di onCreate, jadi tidak menambah waktu startup. */
    override fun newImageLoader(): ImageLoader =
        ImageLoader.Builder(this)
            .crossfade(true)
            .memoryCache {
                MemoryCache.Builder(this)
                    .maxSizePercent(0.20)
                    .build()
            }
            .diskCache {
                DiskCache.Builder()
                    .directory(cacheDir.resolve("image_cache"))
                    .maxSizeBytes(50L * 1024 * 1024)
                    .build()
            }
            .build()
}
