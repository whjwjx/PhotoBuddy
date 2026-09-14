package com.example.photoorganizer.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.photoorganizer.data.MediaAsset
import com.example.photoorganizer.data.MediaStoreRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class HomeUiState(
    val isScanning: Boolean = false,
    val assets: List<MediaAsset> = emptyList(),
    val error: String? = null,
)

class HomeViewModel(
    app: Application,
) : AndroidViewModel(app) {
    private val repo = MediaStoreRepository(app.contentResolver)

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    fun scan() {
        _uiState.update { it.copy(isScanning = true, error = null) }
        viewModelScope.launch {
            runCatching { repo.loadAll() }
                .onSuccess { assets ->
                    _uiState.update { it.copy(isScanning = false, assets = assets) }
                }.onFailure { e ->
                    _uiState.update { it.copy(isScanning = false, error = e.message) }
                }
        }
    }
}
