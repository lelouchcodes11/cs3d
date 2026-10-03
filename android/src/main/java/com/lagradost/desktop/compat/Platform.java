package com.lagradost.desktop.compat;

import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

/**
 * Getters whose Android return value is a Java platform type. Property shims read through here
 * so upstream Kotlin sees {@code T!} and may treat the value as nullable or non-null, as it does
 * against android.jar.
 */
public final class Platform {
    private Platform() {}

    public static ViewGroup.LayoutParams layoutParams(View view) {
        return view.getLayoutParams();
    }

    public static CharSequence text(TextView view) {
        return view.getText();
    }
}
