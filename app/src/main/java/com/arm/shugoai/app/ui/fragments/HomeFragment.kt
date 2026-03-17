package com.arm.shugoai.app.ui.fragments

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.arm.shugoai.app.MainActivity
import com.arm.shugoai.app.R
import com.arm.shugoai.app.manager.ModelManager
import com.arm.shugoai.app.model.MenuItem
import com.arm.shugoai.app.ui.adapters.MenuAdapter
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class HomeFragment : Fragment() {

    interface HomeListener {
        fun onFeatureSelected(itemId: Int)
        fun onSelectModelRequested()
    }

    private var listener: HomeListener? = null
    private lateinit var modelManager: ModelManager

    fun setHomeListener(listener: HomeListener) {
        this.listener = listener
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_home, container, false)
        modelManager = ModelManager.getInstance(requireContext())

        val recyclerView = view.findViewById<RecyclerView>(R.id.menu_grid)
        val welcomeContainer = view.findViewById<View>(R.id.welcome_container)
        val btnSelectModel = view.findViewById<View>(R.id.btn_select_model_welcome)

        val menuItems = listOf(
            MenuItem(MainActivity.MENU_ID_CHAT, R.string.menu_chat, R.drawable.ic_menu_24),
            MenuItem(MainActivity.MENU_ID_PERMISSIONS, R.string.menu_permissions, R.drawable.outline_folder_open_24),
            MenuItem(MainActivity.MENU_ID_PWD_VAULT, R.string.menu_pwd_vault, R.drawable.outline_folder_open_24),
            MenuItem(MainActivity.MENU_ID_TODOS, R.string.menu_todos, R.drawable.outline_folder_open_24),
            MenuItem(MainActivity.MENU_ID_MEETING, R.string.menu_meeting_recorder, R.drawable.outline_folder_open_24),
            MenuItem(MainActivity.MENU_ID_REMINDERS, R.string.menu_reminders, R.drawable.outline_folder_open_24),
            MenuItem(MainActivity.MENU_ID_THEMING, R.string.menu_theming, R.drawable.outline_folder_open_24)
        )

        recyclerView.layoutManager = GridLayoutManager(context, 2)
        recyclerView.adapter = MenuAdapter(menuItems) { item ->
            listener?.onFeatureSelected(item.id)
        }

        btnSelectModel.setOnClickListener {
            listener?.onSelectModelRequested()
        }

        viewLifecycleOwner.lifecycleScope.launch {
            modelManager.selectedModelPath.collectLatest { path ->
                if (path == null) {
                    welcomeContainer.visibility = View.VISIBLE
                    recyclerView.visibility = View.GONE
                } else {
                    welcomeContainer.visibility = View.GONE
                    recyclerView.visibility = View.VISIBLE
                }
            }
        }

        return view
    }
}
