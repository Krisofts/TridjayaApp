package com.krisoft.tridjayaelektronik.domain.leads

import com.krisoft.tridjayaelektronik.data.AuthResult
import com.krisoft.tridjayaelektronik.data.CrmRepository
import com.krisoft.tridjayaelektronik.data.model.PipelineDto
import javax.inject.Inject

class GetPipelinesUseCase @Inject constructor(
    private val crmRepository: CrmRepository
) {
    suspend operator fun invoke(): AuthResult<List<PipelineDto>> = crmRepository.pipelines()
}
