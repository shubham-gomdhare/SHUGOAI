package com.arm.shugoai.app

import android.os.Bundle
import android.view.View
import android.widget.EditText
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.arm.shugoai.InferenceEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

class ChatFragment : Fragment(R.layout.layout_feature_chat) {

    private lateinit var messagesRv: RecyclerView
    private lateinit var userInputEt: EditText
    private lateinit var userActionFab: View
    private lateinit var stopBtn: ImageButton
    private lateinit var loadingScreen: LinearLayout
    private lateinit var loadingTv: TextView
    private lateinit var modelStatusBadgeText: TextView

    private lateinit var modelManager: ModelManager
    private var generationJob: Job? = null
    private val messages = mutableListOf<Message>()
    private val messageAdapter = MessageAdapter(messages)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        modelManager = ModelManager.getInstance(requireContext())
        messagesRv = view.findViewById(R.id.messages)
        userInputEt = view.findViewById(R.id.user_input)
        userActionFab = view.findViewById(R.id.fab)
        stopBtn = view.findViewById(R.id.stop_btn)
        loadingScreen = view.findViewById(R.id.loading_screen)
        loadingTv = view.findViewById(R.id.loading_text)
        modelStatusBadgeText = view.findViewById(R.id.model_status_badge_text)

        setupChat()

        view.findViewById<View>(R.id.model_status_badge).setOnClickListener {
            navigateToModelManager()
        }

        userActionFab.setOnClickListener {
            handleUserInput()
        }

        stopBtn.setOnClickListener {
            generationJob?.cancel()
            updateUiState(isGenerating = false)
        }

        // Handle Keyboard sticking
        ViewCompat.setOnApplyWindowInsetsListener(view.findViewById(R.id.chat_root)) { v, insets ->
            val imeInsets = insets.getInsets(WindowInsetsCompat.Type.ime())
            v.setPadding(0, 0, 0, imeInsets.bottom)
            insets
        }

        lifecycleScope.launch {
            modelManager.isModelLoaded.collectLatest { isLoaded ->
                if (isLoaded) {
                    loadingScreen.visibility = View.GONE
                    userInputEt.isEnabled = true
                    userActionFab.isEnabled = true
                } else {
                    loadingScreen.visibility = View.VISIBLE
                    loadingTv.text = getString(R.string.loading_model)
                    userInputEt.isEnabled = false
                    userActionFab.isEnabled = false
                }
            }
        }

        lifecycleScope.launch {
            modelManager.selectedModelPath.collectLatest { path ->
                modelStatusBadgeText.text = path?.let { File(it).name } ?: getString(R.string.no_model_selected)
                addWelcomeMessage()
            }
        }
    }

    private fun setupChat() {
        messagesRv.layoutManager = LinearLayoutManager(context).apply { stackFromEnd = true }
        messagesRv.adapter = messageAdapter
    }

    private fun navigateToModelManager() {
        (activity as? MainActivity)?.showModelManager()
    }

    private fun addWelcomeMessage() {
        if (messages.isEmpty()) {
            messages.add(Message(UUID.randomUUID().toString(), "Model is ready. How can I help you today?", false))
            messageAdapter.notifyItemInserted(0)
        }
    }

    fun clearConversation() {
        val count = messages.size
        messages.clear()
        messageAdapter.notifyItemRangeRemoved(0, count)
    }

    private fun handleUserInput() {
        val userMsg = userInputEt.text.toString()
        if (userMsg.isNotBlank() && modelManager.isModelLoaded.value) {
            userInputEt.text.clear()
            updateUiState(isGenerating = true)

            val userMessage = Message(UUID.randomUUID().toString(), userMsg, true)
            val assistantMessage = Message(UUID.randomUUID().toString(), "", false)
            
            messages.add(userMessage)
            messages.add(assistantMessage)
            
            messageAdapter.notifyItemRangeInserted(messages.size - 2, 2)
            messagesRv.scrollToPosition(messages.size - 1)

            generationJob = lifecycleScope.launch(Dispatchers.Default) {
                try {
                    val response = modelManager.engine.sendUserPrompt(userMsg)
                    val responseBuilder = StringBuilder()
                    response.collect { token ->
                        responseBuilder.append(token)
                        withContext(Dispatchers.Main) {
                            val index = messages.indexOfFirst { it.id == assistantMessage.id }
                            if (index != -1) {
                                messages[index] = messages[index].copy(content = responseBuilder.toString())
                                messageAdapter.notifyItemChanged(index)
                                messagesRv.scrollToPosition(messages.size - 1)
                            }
                        }
                    }
                } catch (e: Exception) {
                    // Handle error
                } finally {
                    withContext(Dispatchers.Main) {
                        updateUiState(isGenerating = false)
                    }
                }
            }
        }
    }

    private fun updateUiState(isGenerating: Boolean) {
        userInputEt.isEnabled = !isGenerating
        userActionFab.isEnabled = !isGenerating
        stopBtn.visibility = if (isGenerating) View.VISIBLE else View.GONE
    }

    override fun onDestroyView() {
        generationJob?.cancel()
        super.onDestroyView()
    }
}
