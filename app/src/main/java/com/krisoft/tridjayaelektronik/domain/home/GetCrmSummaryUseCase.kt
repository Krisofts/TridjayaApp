package com.krisoft.tridjayaelektronik.domain.home

import com.krisoft.tridjayaelektronik.data.AuthRepository
import com.krisoft.tridjayaelektronik.data.CrmRepository
import com.krisoft.tridjayaelektronik.data.LeadSummary
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

/**
 * CRM summary for the Home dashboard widget, backed by the **same live leads cache** the Prospek tab
 * reads — so the two stay in sync automatically: any lead created (incl. offline/pending), synced, or
 * marked won/lost updates both places at once.
 */
class GetCrmSummaryUseCase @Inject constructor(
    private val crmRepository: CrmRepository,
    private val authRepository: AuthRepository
) {
    /** Live CRM summary stream from the cache (recomputes on every cache change). */
    fun observe(): Flow<LeadSummary> = crmRepository.observeSummary()

    /** Flush any offline-created leads, then refresh the leads cache from the network (TTL-gated or
     *  forced). The reactive [observe] stream — and the Prospek list — then reflect the result. */
    suspend fun sync(forceRefresh: Boolean = false) {
        val myId = authRepository.currentUserId ?: return
        crmRepository.syncPendingLeads()
        if (forceRefresh) crmRepository.syncLeads(myId) else crmRepository.syncLeadsIfStale(myId)
    }

    /** One-shot summary (used where a Flow isn't collected). */
    suspend operator fun invoke(forceRefresh: Boolean = false): LeadSummary {
        sync(forceRefresh)
        return crmRepository.cachedSummary()
    }
}
