package com.watchout.core.model

/**
 * Estado do motor LiteRT-LM (modelo de IA).
 */
sealed interface ModelState {
    data object NotConfigured : ModelState
    data object Loading : ModelState
    data class Ready(val backend: String) : ModelState
    data class Error(val message: String) : ModelState
}
