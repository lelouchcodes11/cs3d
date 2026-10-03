package android.view

import android.graphics.drawable.Drawable

// desktop: overlay drawables are tracked but not drawn (upstream only uses them to black out display cutouts)
open class ViewOverlay internal constructor(private val host: View) {
    private val drawables = ArrayList<Drawable>()

    open fun add(drawable: Drawable) {
        if (drawable !in drawables) drawables.add(drawable)
        host.invalidate()
    }

    open fun remove(drawable: Drawable) {
        drawables.remove(drawable)
        host.invalidate()
    }

    open fun clear() {
        drawables.clear()
        host.invalidate()
    }
}
