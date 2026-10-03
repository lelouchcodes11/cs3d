package android.content.pm;

public class ResolveInfo {
    public ActivityInfo activityInfo;
    public int priority;
    public int match;
    public boolean isDefault;
    public CharSequence nonLocalizedLabel;
    public int icon;

    public CharSequence loadLabel(PackageManager pm) {
        if (nonLocalizedLabel != null) return nonLocalizedLabel;
        return activityInfo != null ? activityInfo.loadLabel(pm) : "";
    }

    public static class ActivityInfo extends PackageItemInfo {
        public ApplicationInfo applicationInfo;
    }
}
