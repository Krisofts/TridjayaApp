package com.krisoft.tridjayaelektronik.domain.leads

import com.krisoft.tridjayaelektronik.data.AuthResult
import com.krisoft.tridjayaelektronik.data.CrmRepository
import com.krisoft.tridjayaelektronik.data.model.LeadDto
import javax.inject.Inject

class MoveLeadStageUseCase @Inject constructor(private val crmRepository: CrmRepository) {
    suspend operator fun invoke(leadId: Long, stageId: Long): AuthResult<LeadDto> =
        crmRepository.moveStage(leadId, stageId)
}

class MarkLeadWonUseCase @Inject constructor(private val crmRepository: CrmRepository) {
    suspend operator fun invoke(leadId: Long): AuthResult<LeadDto> = crmRepository.markWon(leadId)
}

class MarkLeadLostUseCase @Inject constructor(private val crmRepository: CrmRepository) {
    suspend operator fun invoke(leadId: Long, reason: String): AuthResult<LeadDto> =
        crmRepository.markLost(leadId, reason)
}

class ReopenLeadUseCase @Inject constructor(private val crmRepository: CrmRepository) {
    suspend operator fun invoke(leadId: Long): AuthResult<LeadDto> = crmRepository.reopenLead(leadId)
}
