package android.app;

import android.content.Context;
import android.content.Intent;
import android.view.ContextThemeWrapper;

public class ActivityBase extends ContextThemeWrapper {
    public ActivityBase() {
        super();
    }

    public ActivityBase(Context base, int themeResId) {
        super(base, themeResId);
    }

    protected void onNewIntent(Intent intent) {}
}
