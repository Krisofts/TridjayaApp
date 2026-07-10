package com.krisoft.tridjayaelektronik.domain.leads

import com.krisoft.tridjayaelektronik.data.AuthResult
import com.krisoft.tridjayaelektronik.data.CrmRepository
import com.krisoft.tridjayaelektronik.data.LeadSummary
import com.krisoft.tridjayaelektronik.data.model.LeadDto
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

data class LeadsLoadResult(val items: List<LeadDto>, val summary: LeadSummary, val errorMessage: String?)

/**
 * Consolidates the "sync (if stale or forced) → reload cache → recompute summary" pattern that
 * LeadsListViewModel previously duplicated once for its stale-sync path and once for manual
 * refresh.
 */
class GetLeadsUseCase @Inject constructor(
    private val crmRepository: CrmRepository
) {
    /** Live cache stream for the given search term — the list's reactive source of truth. */
    fun observe(search: String): Flow<List<LeadDto>> = crmRepository.observeCachedLeads(search)

    /** Live CRM summary stream, recomputed whenever the cache changes. */
    fun observeSummary(): Flow<LeadSummary> = crmRepository.observeSummary()

    /** Triggers a network refresh into the cache (the reactive stream then emits). Returns an error
     *  message on failure, or null on success. */
    suspend fun syncOnly(myId: String, forceRefresh: Boolean): String? {
        val syncResult = if (forceRefresh) crmRepository.syncLeads(myId) else crmRepository.syncLeadsIfStale(myId)
        return (syncResult as? AuthResult.Failure)?.message
    }

    /** Flush any leads created offline (pending) up to the server — retried on open/refresh. */
    suspend fun syncPending() = crmRepository.syncPendingLeads()

    suspend fun sync(myId: String, search: String, forceRefresh: Boolean): LeadsLoadResult {
        val syncResult = if (forceRefresh) crmRepository.syncLeads(myId) else crmRepository.syncLeadsIfStale(myId)
        val errorMessage = (syncResult as? AuthResult.Failure)?.message
        return loadFromCache(search, errorMessage)
    }

    suspend fun search(search: String): List<LeadDto> = crmRepository.cachedLeads(search)

    private suspend fun loadFromCache(search: String, errorMessage: String?): LeadsLoadResult {
        val items = crmRepository.cachedLeads(search)
        val summary = crmRepository.cachedSummary()
        return LeadsLoadResult(items, summary, errorMessage)
    }
}
