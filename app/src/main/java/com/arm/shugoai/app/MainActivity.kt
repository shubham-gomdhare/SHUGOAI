package com.arm.shugoai.app

import android.os.Bundle
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.addCallback
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.content.res.AppCompatResources
import androidx.core.view.GravityCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import androidx.drawerlayout.widget.DrawerLayout
import androidx.fragment.app.Fragment
import androidx.fragment.app.commit
import androidx.lifecycle.lifecycleScope
import com.arm.shugoai.app.manager.ModelManager
import com.arm.shugoai.app.ui.fragments.ChatFragment
import com.arm.shugoai.app.ui.fragments.HomeFragment
import com.arm.shugoai.app.ui.fragments.ModelManagerFragment
import com.google.android.material.appbar.AppBarLayout
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.navigation.NavigationView
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.io.File

class MainActivity : AppCompatActivity(), HomeFragment.HomeListener {

    private lateinit var drawerLayout: DrawerLayout
    private lateinit var appBarLayout: AppBarLayout
    private lateinit var toolbar: MaterialToolbar
    private lateinit var toolbarTitle: TextView
    private lateinit var statusSubtitleTv: TextView
    private lateinit var modelStatusTv: TextView
    private lateinit var loadingOverlay: View
    private lateinit var loadingText: TextView
    
    private lateinit var modelManager: ModelManager

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        modelManager = ModelManager.getInstance(this)

        // Find common views
        drawerLayout = findViewById(R.id.drawer_layout)
        appBarLayout = findViewById(R.id.app_bar)
        toolbar = findViewById(R.id.toolbar)
        toolbarTitle = findViewById(R.id.toolbar_title)
        statusSubtitleTv = findViewById(R.id.status_subtitle)
        loadingOverlay = findViewById(R.id.global_loading_overlay)
        loadingText = findViewById(R.id.global_loading_text)

        val navView = findViewById<NavigationView>(R.id.nav_view)
        val navHeader = navView.findViewById<LinearLayout>(R.id.nav_header)
        modelStatusTv = navHeader.findViewById(R.id.gguf)

        navHeader.findViewById<View>(R.id.btn_change_model).setOnClickListener {
            drawerLayout.closeDrawer(GravityCompat.START)
            showModelManager()
        }

        // Handle window insets
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main_content)) { _, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            appBarLayout.updatePadding(top = systemBars.top)
            navHeader.updatePadding(top = systemBars.top)
            insets
        }

        // Handle back press
        onBackPressedDispatcher.addCallback(this) {
            if (drawerLayout.isDrawerOpen(GravityCompat.START)) {
                drawerLayout.closeDrawer(GravityCompat.START)
            } else if (supportFragmentManager.backStackEntryCount > 0) {
                supportFragmentManager.popBackStack()
            } else {
                finish()
            }
        }

        toolbar.setNavigationOnClickListener {
            if (supportFragmentManager.backStackEntryCount > 0) {
                supportFragmentManager.popBackStack()
            } else {
                drawerLayout.openDrawer(GravityCompat.START)
            }
        }

        if (savedInstanceState == null) {
            showHome()
        }

        supportFragmentManager.addOnBackStackChangedListener {
            updateToolbarNavigationIcon()
        }
        updateToolbarNavigationIcon()

        lifecycleScope.launch {
            modelManager.selectedModelPath.collectLatest { path ->
                modelStatusTv.text = path?.let { File(it).name } ?: getString(R.string.no_model_selected)
            }
        }

        lifecycleScope.launch {
            modelManager.isCurrentlyLoading.collectLatest { isLoading ->
                loadingOverlay.visibility = if (isLoading) View.VISIBLE else View.GONE
            }
        }

        lifecycleScope.launch {
            modelManager.loadingText.collectLatest { text ->
                loadingText.text = text
            }
        }
    }

    override fun onSelectModelRequested() {
        showModelManager()
    }

    fun showModelManager() {
        navigateTo(ModelManagerFragment(), "ModelManager")
        toolbarTitle.text = getString(R.string.models_title)
    }

    private fun showHome() {
        val homeFragment = HomeFragment().apply {
            setHomeListener(this@MainActivity)
        }
        supportFragmentManager.commit {
            replace(R.id.feature_container, homeFragment, "Home")
        }
    }

    private fun updateToolbarNavigationIcon() {
        if (supportFragmentManager.backStackEntryCount > 0) {
            toolbar.navigationIcon = AppCompatResources.getDrawable(this, android.R.drawable.ic_menu_revert)
        } else {
            toolbar.navigationIcon = AppCompatResources.getDrawable(this, R.drawable.ic_menu_24)
            toolbarTitle.text = getString(R.string.app_name)
            statusSubtitleTv.visibility = View.GONE
        }
    }

    override fun onFeatureSelected(itemId: Int) {
        when (itemId) {
            MENU_ID_CHAT -> {
                navigateTo(ChatFragment(), "ChatFeature")
                toolbarTitle.text = getString(R.string.menu_chat)
            }
            else -> {
                val featureName = when(itemId) {
                    MENU_ID_PERMISSIONS -> getString(R.string.menu_permissions)
                    MENU_ID_PWD_VAULT -> getString(R.string.menu_pwd_vault)
                    MENU_ID_TODOS -> getString(R.string.menu_todos)
                    MENU_ID_MEETING -> getString(R.string.menu_meeting_recorder)
                    MENU_ID_REMINDERS -> getString(R.string.menu_reminders)
                    MENU_ID_THEMING -> getString(R.string.menu_theming)
                    else -> "Feature"
                }
                Toast.makeText(this, "$featureName coming soon!", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun navigateTo(fragment: Fragment, tag: String) {
        supportFragmentManager.commit {
            replace(R.id.feature_container, fragment, tag)
            addToBackStack(tag)
        }
    }

    companion object {
        const val MENU_ID_CHAT = 1
        const val MENU_ID_PERMISSIONS = 2
        const val MENU_ID_PWD_VAULT = 3
        const val MENU_ID_TODOS = 4
        const val MENU_ID_MEETING = 5
        const val MENU_ID_REMINDERS = 6
        const val MENU_ID_THEMING = 8
    }
}
