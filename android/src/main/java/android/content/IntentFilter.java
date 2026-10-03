package android.content;

import java.util.ArrayList;
import java.util.List;

public class IntentFilter {
    private final List<String> mActions = new ArrayList<>();
    private final List<String> mCategories = new ArrayList<>();

    public IntentFilter() {
    }

    public IntentFilter(String action) {
        addAction(action);
    }

    public final void addAction(String action) {
        mActions.add(action);
    }

    public final void addCategory(String category) {
        mCategories.add(category);
    }

    public final int countActions() {
        return mActions.size();
    }

    public final String getAction(int index) {
        return mActions.get(index);
    }

    public final boolean hasAction(String action) {
        return mActions.contains(action);
    }

    public final boolean matchAction(String action) {
        return hasAction(action);
    }
}
