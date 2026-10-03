package androidx.core.view

import android.view.MenuItem

inline var MenuItem.isVisible: Boolean
    get() = isVisible()
    set(value) {
        setVisible(value)
    }

inline var MenuItem.isEnabled: Boolean
    get() = isEnabled()
    set(value) {
        setEnabled(value)
    }

inline var MenuItem.isChecked: Boolean
    get() = isChecked()
    set(value) {
        setChecked(value)
    }

inline var MenuItem.actionView: android.view.View?
    get() = getActionView()
    set(value) {
        setActionView(value)
    }

