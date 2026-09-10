package com.watchout.feature.assistant

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.watchout.core.model.AssistanceMode
import com.watchout.core.model.ModelState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Estado da UI do ecrã principal do assistente.
 */
data class AssistantUiState(
    val modelState: ModelState = ModelState.NotConfigured,
    val selectedMode: AssistanceMode = AssistanceMode.SAFETY,
    val isAnalyzing: Boolean = false,
    val lastResult: String = "",
    val lastBackend: String = "—",
    val lastInferenceMs: Long = 0,
    val autoAnalysisEnabled: Boolean = true,
    val autoIntervalSeconds: Int = 2,
    val isSpeaking: Boolean = false,
)

/**
 * ViewModel do ecrã principal. Orquestra câmara, modelo de IA, TTS e vibração.
 * Não contém lógica de UI — apenas estado reativo via StateFlow.
 */
class AssistantViewModel : ViewModel() {

    private val _uiState = MutableStateFlow(AssistantUiState())
    val uiState: StateFlow<AssistantUiState> = _uiState.asStateFlow()

    fun selectMode(mode: AssistanceMode) {
        _uiState.value = _uiState.value.copy(selectedMode = mode)
    }

    fun onModelReady(backend: String) {
        _uiState.value = _uiState.value.copy(
            modelState = ModelState.Ready(backend),
            lastBackend = backend
        )
    }

    fun onModelError(message: String) {
        _uiState.value = _uiState.value.copy(modelState = ModelState.Error(message))
    }

    fun onAnalysisStarted() {
        _uiState.value = _uiState.value.copy(isAnalyzing = true)
    }

    fun onAnalysisComplete(result: String, inferenceMs: Long) {
        _uiState.value = _uiState.value.copy(
            isAnalyzing = false,
            lastResult = result,
            lastInferenceMs = inferenceMs
        )
    }

    fun toggleAutoAnalysis(enabled: Boolean) {
        _uiState.value = _uiState.value.copy(autoAnalysisEnabled = enabled)
    }

    fun setAutoInterval(seconds: Int) {
        _uiState.value = _uiState.value.copy(autoIntervalSeconds = seconds)
    }

    fun clearLastResult() {
        _uiState.value = _uiState.value.copy(lastResult = "", lastInferenceMs = 0)
    }
}
