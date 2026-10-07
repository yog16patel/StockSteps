package org.example.stocksteps.presentation.backend

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.example.stocksteps.domain.GetBackendInfo

/** Asks the backend once whether it serves sample (mock) data; any failure means "not mock". */
internal class BackendInfoViewModel(getBackendInfo: GetBackendInfo, private val closeResources: () -> Unit) : ViewModel() {
    private val mutableIsMock = MutableStateFlow(false)
    val isMock = mutableIsMock.asStateFlow()

    init {
        viewModelScope.launch {
            mutableIsMock.value = try {
                getBackendInfo().isMock
            } catch (cause: Exception) {
                if (cause is CancellationException) throw cause
                false
            }
        }
    }

    override fun onCleared() = closeResources()
}
