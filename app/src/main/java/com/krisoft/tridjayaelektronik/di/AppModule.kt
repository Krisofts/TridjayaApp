package com.krisoft.tridjayaelektronik.di

import android.content.Context
import androidx.room.Room
import com.krisoft.tridjayaelektronik.data.TokenStore
import com.krisoft.tridjayaelektronik.data.local.AppDatabase
import com.krisoft.tridjayaelektronik.data.local.BranchStockDao
import com.krisoft.tridjayaelektronik.data.local.DashboardCacheDao
import com.krisoft.tridjayaelektronik.data.local.LeadDao
import com.krisoft.tridjayaelektronik.data.local.SyncMetaDao
import com.krisoft.tridjayaelektronik.data.remote.AuthApi
import com.krisoft.tridjayaelektronik.data.remote.CrmApi
import com.krisoft.tridjayaelektronik.data.remote.InventoryApi
import com.krisoft.tridjayaelektronik.data.remote.NetworkModule
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
    fun provideCrmApi(tokenStore: TokenStore): CrmApi = NetworkModule.createCrmApi(tokenStore)

    @Provides
    @Singleton
    fun provideAppDatabase(@ApplicationContext context: Context): AppDatabase =
        Room.databaseBuilder(context, AppDatabase::class.java, "tridjaya.db")
            // Local cache only (server is the source of truth) — safe to wipe on schema bumps.
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
}
