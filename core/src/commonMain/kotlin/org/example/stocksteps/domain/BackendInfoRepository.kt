package org.example.stocksteps.domain

import org.example.stocksteps.model.BackendInfo

interface BackendInfoRepository { suspend fun getBackendInfo(): BackendInfo }

class GetBackendInfo(private val repository: BackendInfoRepository) {
    suspend operator fun invoke() = repository.getBackendInfo()
}
