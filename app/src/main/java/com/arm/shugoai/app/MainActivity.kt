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
import com.google.android.material.appbar.AppBarLayout
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.navigation.NavigationView

class MainActivity : AppCompatActivity(), HomeFragment.HomeListener {

    // Android views
    private lateinit var drawerLayout: DrawerLayout
    private lateinit var appBarLayout: AppBarLayout
    private lateinit var toolbar: MaterialToolbar
    private lateinit var toolbarTitle: TextView
    private lateinit var statusSubtitleTv: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Find common views
        drawerLayout = findViewById(R.id.drawer_layout)
        appBarLayout = findViewById(R.id.app_bar)
        toolbar = findViewById(R.id.toolbar)
        toolbarTitle = findViewById(R.id.toolbar_title)
        statusSubtitleTv = findViewById(R.id.status_subtitle)

        val navView = findViewById<NavigationView>(R.id.nav_view)
        val navHeader = navView.findViewById<LinearLayout>(R.id.nav_header)

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
            val homeFragment = HomeFragment().apply {
                setHomeListener(this@MainActivity)
            }
            supportFragmentManager.commit {
                replace(R.id.feature_container, homeFragment)
            }
        }

        supportFragmentManager.addOnBackStackChangedListener {
            updateToolbarNavigationIcon()
        }
        updateToolbarNavigationIcon() // Initial check
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
                    MENU_ID_MULTI_MODEL -> getString(R.string.menu_multi_model)
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
        const val MENU_ID_MULTI_MODEL = 7
        const val MENU_ID_THEMING = 8
    }
}
