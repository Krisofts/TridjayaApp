package com.krisoft.tridjayaelektronik.data

import com.krisoft.tridjayaelektronik.BuildConfig

/** Resolves a product's raw `Gambar` field (from the ERP, format unconfirmed — may be a bare
 * relative path or a full URL) into a loadable image URL. Shared by every screen/export that
 * shows product photos so they all agree on one resolution rule. */
object ProductImageUrl {
    fun resolve(raw: String?): String? {
        val trimmed = raw?.trim()
        if (trimmed.isNullOrEmpty()) return null
        return if (trimmed.startsWith("http://") || trimmed.startsWith("https://")) {
            trimmed
        } else {
            BuildConfig.API_BASE_URL.trimEnd('/') + "/" + trimmed.trimStart('/')
        }
    }
}
