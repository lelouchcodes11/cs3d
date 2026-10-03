package androidx.navigation.ui

import android.view.MenuItem
import androidx.navigation.NavController
import androidx.navigation.NavOptions
import com.google.android.material.navigation.NavigationBarView

fun NavigationBarView.setupWithNavController(navController: NavController) {
    setOnItemSelectedListener(NavigationBarView.OnItemSelectedListener { item ->
        NavigationUI.onNavDestinationSelected(item, navController)
    })
    navController.addOnDestinationChangedListener { _, destination, _ ->
        if (menu.findItem(destination.id) != null) setCheckedItem(destination.id)
    }
}

object NavigationUI {
    @JvmStatic
    fun onNavDestinationSelected(item: MenuItem, navController: NavController): Boolean {
        val start = navController.graph?.startDestinationId ?: 0
        val options = NavOptions.Builder()
            .setLaunchSingleTop(true)
            .setPopUpTo(start, false)
            .build()
        return try {
            navController.navigate(item.getItemId(), null, options)
            true
        } catch (_: Throwable) {
            false
        }
    }

    @JvmStatic
    fun setupWithNavController(bar: NavigationBarView, navController: NavController) {
        bar.setupWithNavController(navController)
    }
}

val androidx.fragment.app.Fragment.navController: NavController
    get() = androidx.navigation.fragment.NavHostFragment.findNavController(this)

fun androidx.fragment.app.Fragment.findNavController(): NavController = navController

fun android.app.Activity.findNavController(viewId: Int): NavController {
    val view = findViewById<android.view.View>(viewId) ?: throw IllegalArgumentException("No view $viewId")
    return Navigation.findNavController(view)
}

fun android.view.View.findNavController(): NavController = Navigation.findNavController(this)

object Navigation {
    @JvmStatic
    fun findNavController(view: android.view.View): NavController {
        var current: android.view.View? = view
        while (current != null) {
            val tag = current.getTag(NAV_TAG)
            if (tag is NavController) return tag
            current = current.getParent() as? android.view.View
        }
        throw IllegalStateException("View $view does not have a NavController")
    }

    const val NAV_TAG = 0x00ff0102
}
