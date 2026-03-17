package com.arm.shugoai.app.manager

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.arm.shugoai.AiChat
import com.arm.shugoai.InferenceEngine
import com.arm.shugoai.isModelLoaded
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import java.io.File
import java.io.InputStream

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "model_prefs")

class ModelManager private constructor(context: Context) {

    private val appContext = context.applicationContext
    private val SELECTED_MODEL_PATH = stringPreferencesKey("selected_model_path")
    
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    
    private val _isModelLoaded = MutableStateFlow(false)
    val isModelLoaded: StateFlow<Boolean> = _isModelLoaded.asStateFlow()

    private val _isCurrentlyLoading = MutableStateFlow(false)
    val isCurrentlyLoading: StateFlow<Boolean> = _isCurrentlyLoading.asStateFlow()

    val engine: InferenceEngine = AiChat.getInferenceEngine(appContext)

    val selectedModelPath: Flow<String?> = appContext.dataStore.data
        .map { preferences ->
            preferences[SELECTED_MODEL_PATH]
        }

    init {
        scope.launch {
            // Wait for engine to be Initialized before trying to load anything
            engine.state.first { it is InferenceEngine.State.Initialized }
            
            selectedModelPath.collectLatest { path ->
                if (path != null) {
                    loadModelIntoEngine(path)
                } else {
                    _isModelLoaded.value = false
                    _isCurrentlyLoading.value = false
                    safeCleanUp()
                }
            }
        }
    }

    private suspend fun loadModelIntoEngine(path: String) {
        _isCurrentlyLoading.value = true
        _isModelLoaded.value = false
        try {
            safeCleanUp()
            engine.loadModel(path)
            _isModelLoaded.value = true
        } catch (e: Exception) {
            _isModelLoaded.value = false
        } finally {
            _isCurrentlyLoading.value = false
        }
    }

    private fun safeCleanUp() {
        val currentState = engine.state.value
        if (currentState.isModelLoaded || currentState is InferenceEngine.State.Error) {
            engine.cleanUp()
        }
    }

    suspend fun getSelectedModelPathSync(): String? {
        return selectedModelPath.first()
    }

    fun getModelsDir(): File {
        return File(appContext.filesDir, "models").apply {
            if (!exists()) mkdirs()
        }
    }

    fun listModels(): List<File> {
        return getModelsDir().listFiles { file -> file.isFile && file.extension == "gguf" }?.toList() ?: emptyList()
    }

    suspend fun selectModel(path: String?) {
        appContext.dataStore.edit { preferences ->
            if (path == null) {
                preferences.remove(SELECTED_MODEL_PATH)
            } else {
                preferences[SELECTED_MODEL_PATH] = path
            }
        }
    }

    suspend fun importModel(fileName: String, inputStream: InputStream): File {
        val modelsDir = getModelsDir()
        val targetFile = File(modelsDir, fileName)
        targetFile.outputStream().use { output ->
            inputStream.copyTo(output)
        }
        return targetFile
    }

    fun deleteModel(file: File) {
        if (file.exists()) {
            file.delete()
        }
    }

    companion object {
        @Volatile
        private var INSTANCE: ModelManager? = null

        fun getInstance(context: Context): ModelManager {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: ModelManager(context).also { INSTANCE = it }
            }
        }
    }
}
