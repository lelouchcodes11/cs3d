package androidx.appcompat.widget;

import android.content.Context;
import android.util.AttributeSet;
import android.widget.LinearLayout;

public class SearchViewPlatform extends LinearLayout {
    public interface OnQueryTextListener {
        boolean onQueryTextSubmit(String query);
        boolean onQueryTextChange(String newText);
    }

    public interface OnCloseListener {
        boolean onClose();
    }

    public SearchViewPlatform(Context context) {
        super(context);
    }

    public SearchViewPlatform(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public SearchViewPlatform(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }
}
