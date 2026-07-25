package com.krisoft.tridjayaelektronik.data.model

import kotlinx.serialization.Serializable

/**
 * Metadata APK terbaru — cocok 1:1 dengan `ApkMeta` backend
 * (`user-service/src/apk.rs`, serialize camelCase). `versionCode`/`versionName`/`changelog`
 * opsional (upload lama sebelum fitur versi tak punya field ini) — JANGAN asumsikan selalu ada.
 * `mandatory` selalu ada, default `false`.
 */
@Serializable
data class ApkMetaDto(
    val fileName: String = "",
    val size: Long = 0,
    val uploadedAt: String = "",
    val versionCode: Long? = null,
    val versionName: String? = null,
    val mandatory: Boolean = false,
    val changelog: String? = null
)
