package androidx.viewpager2.adapter

import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.recyclerview.widget.RecyclerView

/** One fragment per page. The item view is a FrameLayout that hosts the fragment's view. */
abstract class FragmentStateAdapter : RecyclerView.Adapter<FragmentStateAdapter.FragmentViewHolder> {
    class FragmentViewHolder(val container: FrameLayout) : RecyclerView.ViewHolder(container)

    private val host: Any

    constructor(activity: FragmentActivity) : super() {
        host = activity
    }
    constructor(fragment: Fragment) : super() {
        host = fragment
    }
    constructor(manager: androidx.fragment.app.FragmentManager, lifecycle: androidx.lifecycle.Lifecycle) : super() {
        host = manager
    }

    abstract fun createFragment(position: Int): Fragment

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): FragmentViewHolder {
        val frame = FrameLayout(parent.getContext())
        frame.setLayoutParams(ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        frame.setId(android.view.View.generateViewId())
        return FragmentViewHolder(frame)
    }

    override fun onBindViewHolder(holder: FragmentViewHolder, position: Int) {
        val fragment = createFragment(position)
        val fm = when (host) {
            is Fragment -> host.getChildFragmentManager()
            is FragmentActivity -> host.getSupportFragmentManager()
            is androidx.fragment.app.FragmentManager -> host
            else -> return
        }
        holder.container.post {
            if (fm.findFragmentById(holder.container.getId()) != null) return@post
            fm.beginTransaction().replace(holder.container.getId(), fragment).commit()
        }
    }

    companion object {
        const val GRACE_WINDOW_TIME_MS = 10_000L
    }
}
