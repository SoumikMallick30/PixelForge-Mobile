package dev.pixelforge.mobile.ui

import android.app.Application
import android.graphics.Bitmap
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import dev.pixelforge.mobile.diffusion.DiffusionEngine
import dev.pixelforge.mobile.model.ModelInfo
import dev.pixelforge.mobile.model.ModelStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ForgeState(
    val prompt: String = "", val reference: Uri? = null, val image: Bitmap? = null,
    val modelReady: Boolean = false, val modelInfo: ModelInfo? = null,
    val busy: Boolean = false, val progress: Float = 0f, val message: String? = null
)

class PixelForgeViewModel(app: Application) : AndroidViewModel(app) {
    private val store = ModelStore(app)
    private val _state = MutableStateFlow(ForgeState(modelReady = store.isReady(), modelInfo = store.info()))
    val state = _state.asStateFlow()

    fun prompt(value: String) = _state.update { it.copy(prompt = value, message = null) }
    fun reference(uri: Uri?) = _state.update { it.copy(reference = uri, image = null, message = null) }

    fun install(uri: Uri) = viewModelScope.launch {
        _state.update { it.copy(busy = true, progress = 0f, message = "Installing model…") }
        runCatching { store.import(uri) { p -> _state.update { it.copy(progress = p) } } }
            .onSuccess { _state.update { it.copy(busy = false, modelReady = true, modelInfo = store.info(), message = null) } }
            .onFailure { e -> _state.update { it.copy(busy = false, message = e.message ?: "Model setup failed") } }
    }

    fun generate() = viewModelScope.launch {
        val request = _state.value
        if (request.prompt.isBlank()) return@launch
        _state.update { it.copy(busy = true, progress = 0f, message = "Preparing…") }
        runCatching {
            DiffusionEngine(getApplication(), store.directory).use { engine ->
                engine.generate(request.prompt.trim(), request.reference) { p, label ->
                    _state.update { it.copy(progress = p, message = label) }
                }
            }
        }.onSuccess { bitmap -> _state.update { it.copy(busy = false, image = bitmap, progress = 1f, message = null) } }
            .onFailure { e -> _state.update { it.copy(busy = false, message = e.message ?: "Generation failed") } }
    }
}
