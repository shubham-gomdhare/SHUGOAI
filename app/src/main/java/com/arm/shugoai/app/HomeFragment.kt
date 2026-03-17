package com.arm.shugoai.app

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView

class HomeFragment : Fragment() {

    interface HomeListener {
        fun onFeatureSelected(itemId: Int)
    }

    private var listener: HomeListener? = null

    fun setHomeListener(listener: HomeListener) {
        this.listener = listener
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_home, container, false)
        val recyclerView = view.findViewById<RecyclerView>(R.id.menu_grid)

        val menuItems = listOf(
            MenuItem(MainActivity.MENU_ID_CHAT, R.string.menu_chat, R.drawable.ic_menu_24),
            MenuItem(MainActivity.MENU_ID_PERMISSIONS, R.string.menu_permissions, R.drawable.outline_folder_open_24),
            MenuItem(MainActivity.MENU_ID_PWD_VAULT, R.string.menu_pwd_vault, R.drawable.outline_folder_open_24),
            MenuItem(MainActivity.MENU_ID_TODOS, R.string.menu_todos, R.drawable.outline_folder_open_24),
            MenuItem(MainActivity.MENU_ID_MEETING, R.string.menu_meeting_recorder, R.drawable.outline_folder_open_24),
            MenuItem(MainActivity.MENU_ID_REMINDERS, R.string.menu_reminders, R.drawable.outline_folder_open_24),
            MenuItem(MainActivity.MENU_ID_MULTI_MODEL, R.string.menu_multi_model, R.drawable.outline_folder_open_24),
            MenuItem(MainActivity.MENU_ID_THEMING, R.string.menu_theming, R.drawable.outline_folder_open_24)
        )

        recyclerView.layoutManager = GridLayoutManager(context, 2)
        recyclerView.adapter = MenuAdapter(menuItems) { item ->
            listener?.onFeatureSelected(item.id)
        }

        return view
    }
}
