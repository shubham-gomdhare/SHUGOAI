package com.arm.shugoai.app

import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.EditText
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.addCallback
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.GravityCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import androidx.drawerlayout.widget.DrawerLayout
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.arm.shugoai.AiChat
import com.arm.shugoai.InferenceEngine
import com.arm.shugoai.gguf.GgufMetadata
import com.arm.shugoai.gguf.GgufMetadataReader
import com.google.android.material.appbar.AppBarLayout
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.button.MaterialButton
import com.google.android.material.navigation.NavigationView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.util.UUID

class MainActivity : AppCompatActivity() {

    // Android views
    private lateinit var drawerLayout: DrawerLayout
    private lateinit var ggufTv: TextView
    private lateinit var statusSubtitleTv: TextView
    private lateinit var modelStatusBadgeTv: TextView
    private lateinit var messagesRv: RecyclerView
    private lateinit var userInputEt: EditText
    private lateinit var userActionFab: MaterialButton
    private lateinit var stopBtn: ImageButton
    private lateinit var modelSelectionScreen: LinearLayout
    private lateinit var loadingScreen: LinearLayout
    private lateinit var loadingTv: TextView
    private lateinit var inputContainer: View
    private lateinit var toolbar: MaterialToolbar
    private lateinit var appBarLayout: AppBarLayout

    // Arm AI Chat inference engine
    private lateinit var engine: InferenceEngine
    private var generationJob: Job? = null

    // Conversation states
    private var isModelReady = false
    private val messages = mutableListOf<Message>()
    private val lastAssistantMsg = StringBuilder()
    private val messageAdapter = MessageAdapter(messages)

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Find views
        drawerLayout = findViewById(R.id.drawer_layout)
        appBarLayout = findViewById(R.id.app_bar)
        toolbar = findViewById(R.id.toolbar)
        statusSubtitleTv = findViewById(R.id.status_subtitle)
        modelStatusBadgeTv = findViewById(R.id.model_status_badge_text)
        messagesRv = findViewById(R.id.messages)
        userInputEt = findViewById(R.id.user_input)
        userActionFab = findViewById(R.id.fab)
        stopBtn = findViewById(R.id.stop_btn)
        modelSelectionScreen = findViewById(R.id.model_selection_screen)
        loadingScreen = findViewById(R.id.loading_screen)
        loadingTv = findViewById(R.id.loading_text)
        inputContainer = findViewById(R.id.input_container)

        val navView = findViewById<NavigationView>(R.id.nav_view)
        val navHeader = navView.findViewById<LinearLayout>(R.id.nav_header)
        ggufTv = navView.findViewById(R.id.gguf)

        // Handle window insets manually for precise control
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main_content)) { _, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            val ime = insets.getInsets(WindowInsetsCompat.Type.ime())

            // Apply top inset to App Bar
            appBarLayout.updatePadding(top = systemBars.top)

            // Apply bottom inset to Input Container (sticks to keyboard)
            val bottomInset = if (ime.bottom > 0) ime.bottom else systemBars.bottom
            inputContainer.updatePadding(bottom = bottomInset)

            // Apply top inset to Nav Drawer Header
            navHeader.updatePadding(top = systemBars.top)

            insets
        }

        onBackPressedDispatcher.addCallback {
            if (drawerLayout.isDrawerOpen(GravityCompat.START)) {
                drawerLayout.closeDrawer(GravityCompat.START)
            } else {
                finish()
            }
        }

        messagesRv.layoutManager = LinearLayoutManager(this).apply { stackFromEnd = true }
        messagesRv.adapter = messageAdapter

        toolbar.setNavigationOnClickListener {
            drawerLayout.openDrawer(GravityCompat.START)
        }

        findViewById<MaterialButton>(R.id.btn_select_model).setOnClickListener {
            getContent.launch(arrayOf("*/*"))
        }

        findViewById<View>(R.id.model_status_badge).setOnClickListener {
            getContent.launch(arrayOf("*/*"))
        }

        navView.findViewById<MaterialButton>(R.id.btn_change_model).setOnClickListener {
            drawerLayout.closeDrawer(GravityCompat.START)
            getContent.launch(arrayOf("*/*"))
        }
        navView.findViewById<MaterialButton>(R.id.btn_clear_chat).setOnClickListener {
            clearConversation()
            drawerLayout.closeDrawer(GravityCompat.START)
            addWelcomeMessage()
        }

        stopBtn.setOnClickListener {
            generationJob?.cancel()
            userInputEt.isEnabled = true
            userActionFab.isEnabled = true
            stopBtn.visibility = View.GONE
        }

        lifecycleScope.launch(Dispatchers.Default) {
            engine = AiChat.getInferenceEngine(applicationContext)
            withContext(Dispatchers.Main) {
                // Keep welcome screen visible initially
                modelSelectionScreen.visibility = View.VISIBLE
                messagesRv.visibility = View.GONE
                inputContainer.visibility = View.GONE
            }
        }

        userActionFab.setOnClickListener {
            if (isModelReady) {
                handleUserInput()
            }
        }
    }

    private fun addWelcomeMessage() {
        if (messages.isEmpty()) {
            messages.add(Message(UUID.randomUUID().toString(), getString(R.string.welcome_message), false))
            messageAdapter.notifyItemInserted(0)
        }
    }

    private val getContent = registerForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri?.let { handleSelectedModel(it) }
    }

    private fun handleSelectedModel(uri: Uri) {
        clearConversation()
        isModelReady = false

        modelSelectionScreen.visibility = View.GONE
        messagesRv.visibility = View.GONE
        inputContainer.visibility = View.GONE
        loadingScreen.visibility = View.VISIBLE

        loadingTv.text = getString(R.string.parsing_gguf)
        statusSubtitleTv.text = getString(R.string.parsing_gguf)

        lifecycleScope.launch(Dispatchers.IO) {
            contentResolver.openInputStream(uri)?.use {
                GgufMetadataReader.create().readStructuredMetadata(it)
            }?.let { metadata ->
                withContext(Dispatchers.Main) {
                    ggufTv.text = metadata.toString()
                    modelStatusBadgeTv.text = metadata.filename()
                }

                val modelName = metadata.filename() + FILE_EXTENSION_GGUF
                contentResolver.openInputStream(uri)?.use { input ->
                    ensureModelFile(modelName, input)
                }?.let { modelFile ->
                    loadModel(modelName, modelFile)

                    withContext(Dispatchers.Main) {
                        isModelReady = true

                        loadingScreen.visibility = View.GONE
                        messagesRv.visibility = View.VISIBLE
                        inputContainer.visibility = View.VISIBLE

                        statusSubtitleTv.text = getString(R.string.model_loaded)
                        userInputEt.hint = getString(R.string.type_and_send)
                        userInputEt.isEnabled = true
                        userActionFab.isEnabled = true
                        addWelcomeMessage()
                    }
                }
            }
        }
    }

    private fun clearConversation() {
        messages.clear()
        messageAdapter.notifyDataSetChanged()
    }

    private suspend fun ensureModelFile(modelName: String, input: InputStream) =
        withContext(Dispatchers.IO) {
            File(ensureModelsDirectory(), modelName).also { file ->
                if (!file.exists()) {
                    withContext(Dispatchers.Main) {
                        loadingTv.text = getString(R.string.copying_file)
                        statusSubtitleTv.text = getString(R.string.copying_file)
                    }
                    FileOutputStream(file).use { input.copyTo(it) }
                }
            }
        }

    private suspend fun loadModel(modelName: String, modelFile: File) =
        withContext(Dispatchers.IO) {
            withContext(Dispatchers.Main) {
                loadingTv.text = getString(R.string.loading_model)
                statusSubtitleTv.text = getString(R.string.loading_model)
            }
            engine.loadModel(modelFile.path)
        }

    private fun handleUserInput() {
        userInputEt.text.toString().also { userMsg ->
            if (userMsg.isEmpty()) {
                Toast.makeText(this, "Input message is empty!", Toast.LENGTH_SHORT).show()
            } else {
                userInputEt.text = null
                userInputEt.isEnabled = false
                userActionFab.isEnabled = false
                stopBtn.visibility = View.VISIBLE

                messages.add(Message(UUID.randomUUID().toString(), userMsg, true))
                lastAssistantMsg.clear()
                messages.add(Message(UUID.randomUUID().toString(), lastAssistantMsg.toString(), false))

                messagesRv.smoothScrollToPosition(messages.size - 1)

                generationJob = lifecycleScope.launch(Dispatchers.Default) {
                    engine.sendUserPrompt(userMsg)
                        .onCompletion {
                            withContext(Dispatchers.Main) {
                                userInputEt.isEnabled = true
                                userActionFab.isEnabled = true
                                stopBtn.visibility = View.GONE
                            }
                        }.collect { token ->
                            withContext(Dispatchers.Main) {
                                val messageCount = messages.size
                                if (messageCount > 0 && !messages[messageCount - 1].isUser) {
                                    messages.removeAt(messageCount - 1).copy(
                                        content = lastAssistantMsg.append(token).toString()
                                    ).let { messages.add(it) }

                                    messageAdapter.notifyItemChanged(messages.size - 1)
                                    messagesRv.scrollToPosition(messages.size - 1)
                                }
                            }
                        }
                }
            }
        }
    }

    private fun ensureModelsDirectory() =
        File(filesDir, DIRECTORY_MODELS).also {
            if (it.exists() && !it.isDirectory) { it.delete() }
            if (!it.exists()) { it.mkdir() }
        }

    override fun onStop() {
        generationJob?.cancel()
        super.onStop()
    }

    override fun onDestroy() {
        engine.destroy()
        super.onDestroy()
    }

    companion object {
        private const val DIRECTORY_MODELS = "models"
        private const val FILE_EXTENSION_GGUF = ".gguf"
    }
}

fun GgufMetadata.filename() = when {
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
        "model-${System.currentTimeMillis().toHexString()}"
    }
}
