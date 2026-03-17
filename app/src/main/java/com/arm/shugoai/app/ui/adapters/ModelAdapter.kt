package com.arm.shugoai.app.ui.adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.RadioButton
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.arm.shugoai.app.R
import com.google.android.material.card.MaterialCardView
import java.io.File

class ModelAdapter(
    private var models: List<File>,
    private var selectedModelPath: String?,
    private val onModelSelected: (File) -> Unit,
    private val onModelLongClick: (File) -> Unit
) : RecyclerView.Adapter<ModelAdapter.ModelViewHolder>() {

    fun updateModels(newModels: List<File>, newSelectedPath: String?) {
        models = newModels
        selectedModelPath = newSelectedPath
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ModelViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_model, parent, false)
        return ModelViewHolder(view)
    }

    override fun onBindViewHolder(holder: ModelViewHolder, position: Int) {
        val model = models[position]
        holder.bind(model, model.absolutePath == selectedModelPath)
    }

    override fun getItemCount(): Int = models.size

    inner class ModelViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        private val card: MaterialCardView = view.findViewById(R.id.card_model)
        private val nameTv: TextView = view.findViewById(R.id.tv_model_name)
        private val infoTv: TextView = view.findViewById(R.id.tv_model_info)
        private val radioButton: RadioButton = view.findViewById(R.id.rb_selected)

        fun bind(model: File, isSelected: Boolean) {
            nameTv.text = model.name
            val sizeMb = model.length() / (1024 * 1024)
            infoTv.text = "${sizeMb} MB"
            radioButton.isChecked = isSelected
            card.strokeWidth = if (isSelected) 4 else 0

            card.setOnClickListener {
                onModelSelected(model)
            }

            card.setOnLongClickListener {
                onModelLongClick(model)
                true
            }
        }
    }
}
