package com.krisoft.tridjayaelektronik.data

import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import com.krisoft.tridjayaelektronik.data.local.BranchStockDao
import com.krisoft.tridjayaelektronik.data.local.BranchStockEntity
import com.krisoft.tridjayaelektronik.data.local.DashboardCacheDao
import com.krisoft.tridjayaelektronik.data.local.DashboardCacheEntity
import com.krisoft.tridjayaelektronik.data.local.ProductAggregate
import com.krisoft.tridjayaelektronik.data.local.SyncMetaDao
import com.krisoft.tridjayaelektronik.data.local.SyncMetaEntity
import com.krisoft.tridjayaelektronik.data.model.HomeDashboardCache
import com.krisoft.tridjayaelektronik.data.remote.InventoryApi
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.json.Json
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

private const val SYNC_PAGE_LIMIT = 1000
private val SYNC_INTERVAL_MILLIS = java.util.concurrent.TimeUnit.HOURS.toMillis(5)
private val DASHBOARD_CACHE_TTL_MILLIS = java.util.concurrent.TimeUnit.HOURS.toMillis(5)

/** Month-to-date range vs the same range one month back, for the *-performance endpoints. */
private data class DateRange(val start: String, val end: String, val compareStart: String, val compareEnd: String)

@Singleton
class InventoryRepository @Inject constructor(
    private val api: InventoryApi,
    private val branchStockDao: BranchStockDao,
    private val syncMetaDao: SyncMetaDao,
    private val dashboardCacheDao: DashboardCacheDao
) {

    private val json = Json { ignoreUnknownKeys = true }

    fun pagedProducts(
        search: String,
        region: String,
        readyOnly: Boolean,
        category: String,
        merk: String,
        sortOrder: Int
    ): Flow<PagingData<ProductAggregate>> {
        return Pager(
            config = PagingConfig(
                pageSize = 30,
                enablePlaceholders = false,
                initialLoadSize = 30,
                prefetchDistance = 10
            )
        ) {
            branchStockDao.pagingSource(search.trim(), region, readyOnly, category, merk, sortOrder)
        }.flow
    }

    suspend fun exportProducts(
        search: String,
        region: String,
        readyOnly: Boolean,
        category: String,
        merk: String,
        sortOrder: Int
    ): List<ProductAggregate> =
        branchStockDao.filteredProducts(search.trim(), region, readyOnly, category, merk, sortOrder)

    /** Simple product search for the global search screen — name/code match, default sort, no filters. */
    suspend fun searchProducts(query: String): List<ProductAggregate> =
        branchStockDao.filteredProducts(
            query.trim(),
            "",
            false,
            "",
            "",
            com.krisoft.tridjayaelektronik.data.local.ProductSortOrder.NAME_ASC
        )

    suspend fun categories(): List<String> = branchStockDao.distinctCategories()

    suspend fun merks(): List<String> = branchStockDao.distinctMerks()

    suspend fun branchBreakdown(kode: String, kodeCabang: String): List<BranchStockEntity> =
        branchStockDao.branchesForProduct(kode, kodeCabang)

    suspend fun productDetail(kode: String, kodeCabang: String): ProductAggregate? =
        branchStockDao.productAggregate(kode, kodeCabang)

    /** Refreshes the local cache from the network only if the last sync is older than 6 hours. */
    suspend fun syncIfStale(): AuthResult<Unit> {
        val lastSync = syncMetaDao.get(SyncMetaEntity.KEY_BRANCH_STOCK)?.lastSyncMillis ?: 0L
        val isStale = System.currentTimeMillis() - lastSync >= SYNC_INTERVAL_MILLIS
        if (!isStale) return AuthResult.Success(Unit)
        return sync()
    }

    /** Forces a network refresh regardless of the last sync time (pull-to-refresh). */
    suspend fun sync(): AuthResult<Unit> {
        return try {
            val rows = mutableListOf<BranchStockEntity>()
            var page = 1
            while (true) {
                val response = api.stokCabang(page = page, limit = SYNC_PAGE_LIMIT)
                if (!response.isSuccessful) {
                    return AuthResult.Failure("http_${response.code()}", "Gagal mengambil data stok (${response.code()})")
                }
                val data = response.body()?.data ?: break
                rows += data.items.map {
                    BranchStockEntity(
                        kode = it.Kode,
                        kodeDealer = it.kodeDealer,
                        nama = it.Nama,
                        kategori = it.Kategori,
                        merk = it.Merk,
                        harga = it.Harga,
                        stok = it.Stok,
                        kodeCabang = it.kodeCabang
                    )
                }
                if (!data.hasMore) break
                page += 1
            }
            branchStockDao.replaceAll(rows)
            syncMetaDao.upsert(SyncMetaEntity(SyncMetaEntity.KEY_BRANCH_STOCK, System.currentTimeMillis()))
            AuthResult.Success(Unit)
        } catch (e: Exception) {
            AuthResult.Failure("network_error", e.message ?: "Tidak bisa terhubung ke server")
        }
    }

    /**
     * KPI + monthly target + full branch/sales ranking, bundled and cached in Room for 5 minutes
     * so switching tabs or opening "Lihat Semua" doesn't re-hit the API every time.
     */
    suspend fun homeDashboard(forceRefresh: Boolean = false): AuthResult<HomeDashboardCache> {
        if (!forceRefresh) {
            val cached = dashboardCacheDao.get(DashboardCacheEntity.KEY_HOME_DASHBOARD)
            val isFresh = cached != null && System.currentTimeMillis() - cached.cachedAtMillis < DASHBOARD_CACHE_TTL_MILLIS
            if (isFresh) {
                val parsed = runCatching {
                    json.decodeFromString(HomeDashboardCache.serializer(), cached!!.jsonPayload)
                }.getOrNull()
                if (parsed != null) return AuthResult.Success(parsed)
            }
        }

        val range = monthToDateRange()
        return try {
            coroutineScope {
                // Fire all four independent endpoints concurrently instead of awaiting them one by
                // one — dashboard cold-load latency drops from the sum of four round-trips to the
                // slowest single one.
                val kpiDeferred = async { api.executiveKpi() }
                val targetDeferred = async { api.monthlyTarget() }
                val branchDeferred = async { api.branchPerformance(range.start, range.end, range.compareStart, range.compareEnd) }
                val salesDeferred = async { api.salesPerformance(range.start, range.end, range.compareStart, range.compareEnd) }

                val kpiResponse = kpiDeferred.await()
                val targetResponse = targetDeferred.await()
                val branchResponse = branchDeferred.await()
                val salesResponse = salesDeferred.await()

                val kpi = kpiResponse.body()?.data
                val target = targetResponse.body()?.data
                val branches = branchResponse.body()?.data?.items
                val sales = salesResponse.body()?.data?.items

                if (kpiResponse.isSuccessful && targetResponse.isSuccessful &&
                    branchResponse.isSuccessful && salesResponse.isSuccessful &&
                    kpi != null && target != null && branches != null && sales != null
                ) {
                    val bundle = HomeDashboardCache(
                        kpi = kpi,
                        target = target,
                        branches = branches.sortedByDescending { it.currentAmount },
                        sales = sales.sortedByDescending { it.currentAmount }
                    )
                    dashboardCacheDao.upsert(
                        DashboardCacheEntity(
                            key = DashboardCacheEntity.KEY_HOME_DASHBOARD,
                            jsonPayload = json.encodeToString(HomeDashboardCache.serializer(), bundle),
                            cachedAtMillis = System.currentTimeMillis()
                        )
                    )
                    AuthResult.Success(bundle)
                } else {
                    val failedCode = listOf(kpiResponse, targetResponse, branchResponse, salesResponse)
                        .firstOrNull { !it.isSuccessful }
                        ?.code() ?: 0
                    AuthResult.Failure("http_$failedCode", "Gagal memuat dashboard ($failedCode)")
                }
            }
        } catch (e: Exception) {
            AuthResult.Failure("network_error", e.message ?: "Tidak bisa terhubung ke server")
        }
    }

    private fun monthToDateRange(): DateRange {
        val fmt = SimpleDateFormat("yyyy-MM-dd", Locale.US)

        val endCal = Calendar.getInstance()
        val end = fmt.format(endCal.time)

        val startCal = Calendar.getInstance().apply { set(Calendar.DAY_OF_MONTH, 1) }
        val start = fmt.format(startCal.time)

        val compareEndCal = Calendar.getInstance().apply { add(Calendar.MONTH, -1) }
        val compareEnd = fmt.format(compareEndCal.time)

        val compareStartCal = Calendar.getInstance().apply {
            set(Calendar.DAY_OF_MONTH, 1)
            add(Calendar.MONTH, -1)
        }
        val compareStart = fmt.format(compareStartCal.time)

        return DateRange(start, end, compareStart, compareEnd)
    }
}
