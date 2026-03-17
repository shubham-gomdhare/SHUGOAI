package com.arm.shugoai.app

import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.arm.shugoai.gguf.GgufMetadataReader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class ModelManagerFragment : Fragment(R.layout.fragment_model_manager) {

    private lateinit var rvModels: RecyclerView
    private lateinit var tvEmpty: TextView
    private lateinit var adapter: ModelAdapter
    private lateinit var modelManager: ModelManager

    private val getContent = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        uri?.let { importModel(it) }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        modelManager = ModelManager.getInstance(requireContext())
        rvModels = view.findViewById(R.id.rv_models)
        tvEmpty = view.findViewById(R.id.tv_empty)

        adapter = ModelAdapter(emptyList(), null, { model ->
            lifecycleScope.launch {
                modelManager.selectModel(model.absolutePath)
            }
        }, { model ->
            showDeleteConfirmation(model)
        })

        rvModels.layoutManager = LinearLayoutManager(context)
        rvModels.adapter = adapter

        view.findViewById<View>(R.id.btn_import_model).setOnClickListener {
            getContent.launch(arrayOf("*/*"))
        }

        lifecycleScope.launch {
            modelManager.selectedModelPath.collectLatest { path ->
                refreshModels(path)
            }
        }
    }

    private fun refreshModels(selectedPath: String?) {
        val models = modelManager.listModels()
        adapter.updateModels(models, selectedPath)
        tvEmpty.visibility = if (models.isEmpty()) View.VISIBLE else View.GONE
    }

    private fun importModel(uri: Uri) {
        lifecycleScope.launch(Dispatchers.IO) {
            val metadata = requireContext().contentResolver.openInputStream(uri)?.use {
                GgufMetadataReader.create().readStructuredMetadata(it)
            }

            val fileName = if (metadata != null) {
                metadata.filename() + ".gguf"
            } else {
                // Fallback to URI last path segment or timestamp
                uri.lastPathSegment?.substringAfterLast('/') ?: "model-${System.currentTimeMillis()}.gguf"
            }

            withContext(Dispatchers.Main) {
                Toast.makeText(context, "Importing $fileName...", Toast.LENGTH_SHORT).show()
            }

            requireContext().contentResolver.openInputStream(uri)?.use { input ->
                val importedFile = modelManager.importModel(fileName, input)
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "Imported ${importedFile.name}", Toast.LENGTH_SHORT).show()
                    refreshModels(modelManager.getSelectedModelPathSync())
                }
            }
        }
    }

    private fun showDeleteConfirmation(model: File) {
        AlertDialog.Builder(requireContext())
            .setTitle(R.string.delete_model)
            .setMessage("Are you sure you want to delete ${model.name}?")
            .setPositiveButton("Delete") { _, _ ->
                lifecycleScope.launch {
                    val currentSelected = modelManager.getSelectedModelPathSync()
                    if (currentSelected == model.absolutePath) {
                        modelManager.selectModel(null)
                    }
                    modelManager.deleteModel(model)
                    refreshModels(modelManager.getSelectedModelPathSync())
                    Toast.makeText(context, R.string.model_deleted, Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun com.arm.shugoai.gguf.GgufMetadata.filename(): String {
        return when {
            basic.name != null -> {
                val name = basic.name!!
                basic.sizeLabel?.let { size ->
                    "$name-$size"
                } ?: name
            }
            architecture?.architecture != null -> {
                val arch = architecture!!.architecture!!
                basic.uuid?.let { uuid ->
                    "$arch-$uuid"
                } ?: "$arch-${System.currentTimeMillis()}"
            }
            else -> {
                "model-${System.currentTimeMillis().toString(16)}"
            }
        }
    }
}
