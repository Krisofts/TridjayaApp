package com.krisoft.tridjayaelektronik.data.remote

import com.krisoft.tridjayaelektronik.data.model.ApiResponse
import com.krisoft.tridjayaelektronik.data.model.ErpPriceChangeResultDto
import retrofit2.Response
import retrofit2.http.GET

/**
 * Perubahan harga GS — inventory-service via gateway `/api/inventory/erp-price-changes`.
 * Baca-saja: `force=true` (tombol "Sync sekarang", scan ERP + broadcast WA) SENGAJA tidak
 * diekspos di sini — aksi berat admin-only, di luar scope layar mobile karyawan/manager.
 * Tanpa query param — daftar (default limit backend 500) diambil sekali, cabang/search
 * difilter client-side (dataset kecil, sama pola layar Indent).
 */
interface ErpPriceChangesApi {

    @GET("api/inventory/erp-price-changes")
    suspend fun list(): Response<ApiResponse<ErpPriceChangeResultDto>>
}
