package com.arm.shugoai.app

import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.EditText
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.arm.shugoai.AiChat
import com.arm.shugoai.InferenceEngine
import com.arm.shugoai.gguf.GgufMetadata
import com.arm.shugoai.gguf.GgufMetadataReader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.util.UUID

class ChatFragment : Fragment(R.layout.layout_feature_chat) {

    private lateinit var messagesRv: RecyclerView
    private lateinit var userInputEt: EditText
    private lateinit var userActionFab: View
    private lateinit var stopBtn: ImageButton
    private lateinit var modelSelectionScreen: LinearLayout
    private lateinit var loadingScreen: LinearLayout
    private lateinit var loadingTv: TextView
    private lateinit var inputContainer: View

    private lateinit var engine: InferenceEngine
    private var generationJob: Job? = null
    private var isModelReady = false
    private val messages = mutableListOf<Message>()
    private val messageAdapter = MessageAdapter(messages)

    private val getContent = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        uri?.let { handleSelectedModel(it) }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        messagesRv = view.findViewById(R.id.messages)
        userInputEt = view.findViewById(R.id.user_input)
        userActionFab = view.findViewById(R.id.fab)
        stopBtn = view.findViewById(R.id.stop_btn)
        modelSelectionScreen = view.findViewById(R.id.model_selection_screen)
        loadingScreen = view.findViewById(R.id.loading_screen)
        loadingTv = view.findViewById(R.id.loading_text)
        inputContainer = view.findViewById(R.id.input_container)

        setupChat()

        view.findViewById<View>(R.id.btn_select_model).setOnClickListener {
            selectModel()
        }

        userActionFab.setOnClickListener {
            if (isModelReady) {
                handleUserInput()
            }
        }

        stopBtn.setOnClickListener {
            generationJob?.cancel()
            userInputEt.isEnabled = true
            userActionFab.isEnabled = true
            stopBtn.visibility = View.GONE
        }

        lifecycleScope.launch(Dispatchers.Default) {
            engine = AiChat.getInferenceEngine(requireContext().applicationContext)
        }

        showInitialUI()
    }

    private fun setupChat() {
        messagesRv.layoutManager = LinearLayoutManager(context).apply { stackFromEnd = true }
        messagesRv.adapter = messageAdapter
    }

    private fun selectModel() {
        getContent.launch(arrayOf("*/*"))
    }

    private fun showInitialUI() {
        if (isModelReady) {
            messagesRv.visibility = View.VISIBLE
            inputContainer.visibility = View.VISIBLE
            modelSelectionScreen.visibility = View.GONE
            addWelcomeMessage()
        } else {
            messagesRv.visibility = View.GONE
            inputContainer.visibility = View.GONE
            modelSelectionScreen.visibility = View.VISIBLE
        }
        loadingScreen.visibility = View.GONE
    }

    private fun handleSelectedModel(uri: Uri) {
        clearConversation()
        isModelReady = false

        modelSelectionScreen.visibility = View.GONE
        loadingScreen.visibility = View.VISIBLE
        loadingTv.text = getString(R.string.parsing_gguf)

        lifecycleScope.launch(Dispatchers.IO) {
            val metadata = requireContext().contentResolver.openInputStream(uri)?.use {
                GgufMetadataReader.create().readStructuredMetadata(it)
            }

            if (metadata != null) {
                val modelName = metadata.filename() + ".gguf"
                val modelFile = requireContext().contentResolver.openInputStream(uri)?.use { input ->
                    ensureModelFile(modelName, input)
                }

                if (modelFile != null) {
                    loadModel(modelFile)
                    withContext(Dispatchers.Main) {
                        isModelReady = true
                        showInitialUI()
                        userInputEt.hint = getString(R.string.type_and_send)
                        userInputEt.isEnabled = true
                    }
                }
            } else {
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "Failed to parse model metadata.", Toast.LENGTH_SHORT).show()
                    showInitialUI()
                }
            }
        }
    }

    private fun addWelcomeMessage() {
        if (messages.isEmpty()) {
            messages.add(Message(UUID.randomUUID().toString(), getString(R.string.welcome_message), false))
            messageAdapter.notifyItemInserted(0)
        }
    }

    fun clearConversation() {
        val count = messages.size
        messages.clear()
        messageAdapter.notifyItemRangeRemoved(0, count)
    }

    private suspend fun ensureModelFile(modelName: String, input: InputStream): File = withContext(Dispatchers.IO) {
        val modelsDir = File(requireContext().filesDir, "models").apply { mkdirs() }
        File(modelsDir, modelName).also { file ->
            if (!file.exists()) {
                withContext(Dispatchers.Main) {
                    loadingTv.text = getString(R.string.copying_file)
                }
                FileOutputStream(file).use { output -> input.copyTo(output) }
            }
        }
    }

    private suspend fun loadModel(modelFile: File) {
        withContext(Dispatchers.Main) {
            loadingTv.text = getString(R.string.loading_model)
        }
        engine.loadModel(modelFile.path)
    }

    private fun handleUserInput() {
        val userMsg = userInputEt.text.toString()
        if (userMsg.isNotBlank()) {
            userInputEt.text.clear()
            userInputEt.isEnabled = false
            userActionFab.isEnabled = false
            stopBtn.visibility = View.VISIBLE

            messages.add(Message(UUID.randomUUID().toString(), userMsg, true))
            val assistantMessage = Message(UUID.randomUUID().toString(), "", false)
            messages.add(assistantMessage)
            messageAdapter.notifyItemRangeInserted(messages.size - 2, 2)
            messagesRv.smoothScrollToPosition(messages.size - 1)

            generationJob = lifecycleScope.launch(Dispatchers.Default) {
                val response = engine.sendUserPrompt(userMsg)
                val responseBuilder = StringBuilder()
                response.collect {
                    responseBuilder.append(it)
                    withContext(Dispatchers.Main) {
                        val index = messages.indexOf(assistantMessage)
                        if (index != -1) {
                            messages[index] = assistantMessage.copy(content = responseBuilder.toString())
                            messageAdapter.notifyItemChanged(index)
                            messagesRv.scrollToPosition(messages.size - 1)
                        }
                    }
                }
                withContext(Dispatchers.Main) {
                    userInputEt.isEnabled = true
                    userActionFab.isEnabled = true
                    stopBtn.visibility = View.GONE
                }
            }
        }
    }

    override fun onDestroyView() {
        generationJob?.cancel()
        if (::engine.isInitialized) {
            engine.destroy()
        }
        super.onDestroyView()
    }

    private fun GgufMetadata.filename(): String = when {
        basic.name != null -> {
            basic.name?.let { name ->
                basic.sizeLabel?.let { size ->
                    "$name-$size"
                } ?: name
            }
        }
        architecture?.architecture != null -> {
            architecture?.architecture?.let { arch ->
                basic.uuid?.let { uuid ->
                    "$arch-$uuid"
                } ?: "$arch-${System.currentTimeMillis()}"
            }
        }
        else -> {
            "model-${System.currentTimeMillis().toString(16)}"
        }
    } ?: "unknown-model"
}
