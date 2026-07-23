package com.krisoft.tridjayaelektronik.di

import android.content.Context
import androidx.room.Room
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.krisoft.tridjayaelektronik.data.TokenStore
import com.krisoft.tridjayaelektronik.data.local.AppDatabase
import com.krisoft.tridjayaelektronik.data.local.BranchStockDao
import com.krisoft.tridjayaelektronik.data.local.DashboardCacheDao
import com.krisoft.tridjayaelektronik.data.local.LeadDao
import com.krisoft.tridjayaelektronik.data.local.OpnameCountDao
import com.krisoft.tridjayaelektronik.data.local.SyncMetaDao
import com.krisoft.tridjayaelektronik.data.remote.AuthApi
import com.krisoft.tridjayaelektronik.data.remote.AbsensiApi
import com.krisoft.tridjayaelektronik.data.remote.DeadstockApi
import com.krisoft.tridjayaelektronik.data.remote.DeliveryFlowApi
import com.krisoft.tridjayaelektronik.data.remote.CrmApi
import com.krisoft.tridjayaelektronik.data.remote.DeviceApi
import com.krisoft.tridjayaelektronik.data.remote.ErpPriceChangesApi
import com.krisoft.tridjayaelektronik.data.remote.OffApi
import com.krisoft.tridjayaelektronik.data.remote.InventoryApi
import com.krisoft.tridjayaelektronik.data.remote.NetworkModule
import com.krisoft.tridjayaelektronik.data.remote.NotificationsApi
import com.krisoft.tridjayaelektronik.data.remote.PayrollApi
import com.krisoft.tridjayaelektronik.data.remote.SalesApi
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import javax.inject.Qualifier
import javax.inject.Singleton

/** Marks the application-lifetime coroutine scope used for fire-and-forget background work (e.g. the
 *  offline lead-sync queue) that must outlive any single ViewModel/screen. */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class AppScope

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    @AppScope
    fun provideAppScope(): CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    @Provides
    @Singleton
    fun provideTokenStore(@ApplicationContext context: Context): TokenStore = TokenStore(context)

    @Provides
    @Singleton
    fun provideAuthApi(tokenStore: TokenStore): AuthApi = NetworkModule.createAuthApi(tokenStore)

    @Provides
    @Singleton
    fun provideInventoryApi(tokenStore: TokenStore): InventoryApi =
        NetworkModule.createInventoryApi(tokenStore)

    @Provides
    @Singleton
    fun provideSalesApi(tokenStore: TokenStore): SalesApi =
        NetworkModule.createSalesApi(tokenStore)

    @Provides
    @Singleton
    fun provideCrmApi(tokenStore: TokenStore): CrmApi = NetworkModule.createCrmApi(tokenStore)

    @Provides
    @Singleton
    fun provideAbsensiApi(tokenStore: TokenStore): AbsensiApi =
        NetworkModule.createAbsensiApi(tokenStore)

    @Provides
    @Singleton
    fun provideOffApi(tokenStore: TokenStore): OffApi =
        NetworkModule.createOffApi(tokenStore)

    @Provides
    @Singleton
    fun provideDeviceApi(tokenStore: TokenStore): DeviceApi =
        NetworkModule.createDeviceApi(tokenStore)

    @Provides
    @Singleton
    fun provideDeliveryFlowApi(tokenStore: TokenStore): DeliveryFlowApi =
        NetworkModule.createDeliveryFlowApi(tokenStore)

    @Provides
    @Singleton
    fun provideNotificationsApi(tokenStore: TokenStore): NotificationsApi =
        NetworkModule.createNotificationsApi(tokenStore)

    @Provides
    @Singleton
    fun providePayrollApi(tokenStore: TokenStore): PayrollApi =
        NetworkModule.createPayrollApi(tokenStore)

    @Provides
    @Singleton
    fun provideErpPriceChangesApi(tokenStore: TokenStore): ErpPriceChangesApi =
        NetworkModule.createErpPriceChangesApi(tokenStore)

    @Provides
    @Singleton
    fun provideDeadstockApi(tokenStore: TokenStore): DeadstockApi =
        NetworkModule.createDeadstockApi(tokenStore)

    /** v11 → v12: kolom aging stok (umurHari + kondisi) di branch_stock. Migrasi ADDITIVE —
     *  jangan destruktif, supaya antrean offline (pending leads, hitungan opname) tidak terhapus
     *  saat update aplikasi. */
    private val MIGRATION_11_12 = object : Migration(11, 12) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE branch_stock ADD COLUMN umurHari INTEGER")
            db.execSQL("ALTER TABLE branch_stock ADD COLUMN kondisi TEXT")
        }
    }

    @Provides
    @Singleton
    fun provideAppDatabase(@ApplicationContext context: Context): AppDatabase =
        Room.databaseBuilder(context, AppDatabase::class.java, "tridjaya.db")
            .addMigrations(MIGRATION_11_12)
            // Local cache only (server is the source of truth) — safe to wipe on schema bumps
            // that don't have an explicit migration above.
            .fallbackToDestructiveMigration()
            .build()

    @Provides
    fun provideBranchStockDao(database: AppDatabase): BranchStockDao = database.branchStockDao()

    @Provides
    fun provideSyncMetaDao(database: AppDatabase): SyncMetaDao = database.syncMetaDao()

    @Provides
    fun provideDashboardCacheDao(database: AppDatabase): DashboardCacheDao = database.dashboardCacheDao()

    @Provides
    fun provideLeadDao(database: AppDatabase): LeadDao = database.leadDao()

    @Provides
    fun provideOpnameCountDao(database: AppDatabase): OpnameCountDao = database.opnameCountDao()
}
