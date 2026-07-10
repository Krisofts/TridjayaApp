package com.krisoft.tridjayaelektronik.domain.leads

import com.krisoft.tridjayaelektronik.data.AuthResult
import com.krisoft.tridjayaelektronik.data.CrmRepository
import com.krisoft.tridjayaelektronik.data.model.LeadDto
import com.krisoft.tridjayaelektronik.data.model.PipelineDto
import javax.inject.Inject

data class LeadDetailResult(val lead: LeadDto?, val pipeline: PipelineDto?, val errorMessage: String?)

/** Lead fetch + pipeline-lookup join (finds the pipeline matching the lead's current stage). */
class GetLeadDetailUseCase @Inject constructor(
    private val crmRepository: CrmRepository
) {
    suspend operator fun invoke(leadId: Long): LeadDetailResult {
        val leadResult = crmRepository.leadDetail(leadId)
        val lead = (leadResult as? AuthResult.Success)?.data
            ?: return LeadDetailResult(
                lead = null,
                pipeline = null,
                errorMessage = (leadResult as? AuthResult.Failure)?.message ?: "Gagal memuat prospek"
            )

        val pipelinesResult = crmRepository.pipelines()
        val pipeline = (pipelinesResult as? AuthResult.Success)?.data?.firstOrNull { it.id == lead.pipelineId }
        return LeadDetailResult(lead = lead, pipeline = pipeline, errorMessage = null)
    }
}
