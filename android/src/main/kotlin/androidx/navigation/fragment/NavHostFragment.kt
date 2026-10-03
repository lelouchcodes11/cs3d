package androidx.navigation.fragment

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.TextView
import androidx.activity.OnBackPressedCallback
import androidx.fragment.app.Fragment
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavController
import androidx.navigation.ui.Navigation

/** Hosts the navigation graph declared on the FragmentContainerView (`app:navGraph`). */
open class NavHostFragment : Fragment() {
    lateinit var navController: NavController
        private set
    private var containerId: Int = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        navController = NavController(requireContext())
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        val frame = FrameLayout(requireContext())
        frame.setId(android.view.View.generateViewId())
        containerId = frame.getId()
        return frame
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        view.setTag(Navigation.NAV_TAG, navController)
        val graphId = getArguments()?.getInt("android-nav-graph") ?: 0
        if (graphId != 0) navController.setGraph(graphId)
        navController.host = { entry -> show(entry) }
        if (getArguments()?.getBoolean("android-nav-default-host") == true) {
            val callback = object : OnBackPressedCallback(true) {
                override fun handleOnBackPressed() {
                    if (!navController.popBackStack()) isEnabled = false
                }
            }
            requireActivity().onBackPressedDispatcher.addCallback(getViewLifecycleOwner(), callback)
        }
        // onViewCreated runs before this view is added to its parent, so the first destination
        // is shown on the next loop, once findViewById can see the container.
        android.os.Handler(android.os.Looper.getMainLooper()).post {
            (view.getParent() as? View)?.setTag(Navigation.NAV_TAG, navController)
            navController.currentBackStackEntry?.let { show(it) }
        }
    }

    private fun show(entry: NavBackStackEntry) {
        if (entry.destination.navigatorName == "activity") {
            val name = entry.destination.className ?: return
            val intent = android.content.Intent().setClassName(requireContext(), name)
            try {
                requireContext().startActivity(intent)
            } catch (t: Throwable) {
                android.util.Log.e("NavHostFragment", "Cannot start $name", t)
            }
            return
        }
        val name = entry.destination.className
        val fragment = if (!name.isNullOrEmpty()) {
            try {
                Fragment.instantiate(requireContext(), name).also { it.setArguments(entry.arguments) }
            } catch (_: Throwable) {
                MissingDestinationFragment.newInstance(name)
            }
        } else {
            MissingDestinationFragment.newInstance(entry.destination.label ?: entry.destination.navigatorName)
        }
        getChildFragmentManager().beginTransaction().replace(containerId, fragment).commitNow()
    }

    companion object {
        @JvmStatic
        fun findNavController(fragment: Fragment): NavController {
            var current: Fragment? = fragment
            while (current != null) {
                if (current is NavHostFragment) return current.navController
                current = current.getParentFragment()
            }
            throw IllegalStateException("Fragment $fragment is not attached to a NavHostFragment")
        }
    }
}

fun Fragment.findNavController(): NavController = NavHostFragment.findNavController(this)

/** Shown when a graph destination has not been ported yet. Replaced automatically once that class exists. */
class MissingDestinationFragment : Fragment() {
    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        val text = TextView(requireContext())
        text.setText(getArguments()?.getString("name") ?: "")
        text.setPadding(48, 48, 48, 48)
        return text
    }

    companion object {
        fun newInstance(name: String) = MissingDestinationFragment().apply {
            setArguments(Bundle().apply { putString("name", name) })
        }
    }
}
