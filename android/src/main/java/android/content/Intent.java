package android.content;

import android.net.Uri;
import android.os.Bundle;
import android.os.Parcel;
import android.os.Parcelable;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.Set;

public class Intent implements Parcelable, Cloneable {
    public static final String ACTION_MAIN = "android.intent.action.MAIN";
    public static final String ACTION_VIEW = "android.intent.action.VIEW";
    public static final String ACTION_SEND = "android.intent.action.SEND";
    public static final String ACTION_SEND_MULTIPLE = "android.intent.action.SEND_MULTIPLE";
    public static final String ACTION_SENDTO = "android.intent.action.SENDTO";
    public static final String ACTION_EDIT = "android.intent.action.EDIT";
    public static final String ACTION_PICK = "android.intent.action.PICK";
    public static final String ACTION_CHOOSER = "android.intent.action.CHOOSER";
    public static final String ACTION_GET_CONTENT = "android.intent.action.GET_CONTENT";
    public static final String ACTION_OPEN_DOCUMENT = "android.intent.action.OPEN_DOCUMENT";
    public static final String ACTION_OPEN_DOCUMENT_TREE = "android.intent.action.OPEN_DOCUMENT_TREE";
    public static final String ACTION_CREATE_DOCUMENT = "android.intent.action.CREATE_DOCUMENT";
    public static final String ACTION_INSTALL_PACKAGE = "android.intent.action.INSTALL_PACKAGE";
    public static final String ACTION_WEB_SEARCH = "android.intent.action.WEB_SEARCH";
    public static final String ACTION_SEARCH = "android.intent.action.SEARCH";
    public static final String ACTION_BOOT_COMPLETED = "android.intent.action.BOOT_COMPLETED";

    public static final String CATEGORY_DEFAULT = "android.intent.category.DEFAULT";
    public static final String CATEGORY_BROWSABLE = "android.intent.category.BROWSABLE";
    public static final String CATEGORY_LAUNCHER = "android.intent.category.LAUNCHER";
    public static final String CATEGORY_OPENABLE = "android.intent.category.OPENABLE";
    public static final String CATEGORY_APP_BROWSER = "android.intent.category.APP_BROWSER";

    public static final String EXTRA_TEXT = "android.intent.extra.TEXT";
    public static final String EXTRA_SUBJECT = "android.intent.extra.SUBJECT";
    public static final String EXTRA_TITLE = "android.intent.extra.TITLE";
    public static final String EXTRA_STREAM = "android.intent.extra.STREAM";
    public static final String EXTRA_INTENT = "android.intent.extra.INTENT";
    public static final String EXTRA_MIME_TYPES = "android.intent.extra.MIME_TYPES";
    public static final String EXTRA_ALLOW_MULTIPLE = "android.intent.extra.ALLOW_MULTIPLE";
    public static final String EXTRA_LOCAL_ONLY = "android.intent.extra.LOCAL_ONLY";
    public static final String EXTRA_EMAIL = "android.intent.extra.EMAIL";
    public static final String EXTRA_REFERRER = "android.intent.extra.REFERRER";
    public static final String EXTRA_NOT_UNKNOWN_SOURCE = "android.intent.extra.NOT_UNKNOWN_SOURCE";

    public static final int FLAG_GRANT_READ_URI_PERMISSION = 0x00000001;
    public static final int FLAG_GRANT_WRITE_URI_PERMISSION = 0x00000002;
    public static final int FLAG_GRANT_PERSISTABLE_URI_PERMISSION = 0x00000040;
    public static final int FLAG_GRANT_PREFIX_URI_PERMISSION = 0x00000080;
    public static final int FLAG_ACTIVITY_NO_HISTORY = 0x40000000;
    public static final int FLAG_ACTIVITY_SINGLE_TOP = 0x20000000;
    public static final int FLAG_ACTIVITY_NEW_TASK = 0x10000000;
    public static final int FLAG_ACTIVITY_MULTIPLE_TASK = 0x08000000;
    public static final int FLAG_ACTIVITY_CLEAR_TOP = 0x04000000;
    public static final int FLAG_ACTIVITY_FORWARD_RESULT = 0x02000000;
    public static final int FLAG_ACTIVITY_PREVIOUS_IS_TOP = 0x01000000;
    public static final int FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS = 0x00800000;
    public static final int FLAG_ACTIVITY_BROUGHT_TO_FRONT = 0x00400000;
    public static final int FLAG_ACTIVITY_RESET_TASK_IF_NEEDED = 0x00200000;
    public static final int FLAG_ACTIVITY_NO_ANIMATION = 0x00010000;
    public static final int FLAG_ACTIVITY_CLEAR_TASK = 0x00008000;
    public static final int FLAG_ACTIVITY_TASK_ON_HOME = 0x00004000;
    public static final int FLAG_ACTIVITY_REORDER_TO_FRONT = 0x00020000;
    public static final int FLAG_ACTIVITY_NEW_DOCUMENT = 0x00080000;
    public static final int FLAG_RECEIVER_FOREGROUND = 0x10000000;

    private String mAction;
    private Uri mData;
    private String mType;
    private String mPackage;
    private ComponentName mComponent;
    private int mFlags;
    private Set<String> mCategories;
    private Bundle mExtras;
    private ClipData mClipData;
    private Intent mSelector;

    public Intent() {
    }

    public Intent(Intent o) {
        mAction = o.mAction;
        mData = o.mData;
        mType = o.mType;
        mPackage = o.mPackage;
        mComponent = o.mComponent;
        mFlags = o.mFlags;
        if (o.mCategories != null) mCategories = new LinkedHashSet<>(o.mCategories);
        if (o.mExtras != null) mExtras = new Bundle(o.mExtras);
        mClipData = o.mClipData;
    }

    public Intent(String action) {
        mAction = action;
    }

    public Intent(String action, Uri uri) {
        mAction = action;
        mData = uri;
    }

    public Intent(Context packageContext, Class<?> cls) {
        mComponent = new ComponentName(packageContext, cls);
    }

    public Intent(String action, Uri uri, Context packageContext, Class<?> cls) {
        mAction = action;
        mData = uri;
        mComponent = new ComponentName(packageContext, cls);
    }

    public static Intent createChooser(Intent target, CharSequence title) {
        Intent intent = new Intent(ACTION_CHOOSER);
        intent.putExtra(EXTRA_INTENT, target);
        if (title != null) intent.putExtra(EXTRA_TITLE, title);
        return intent;
    }

    public static Intent makeMainActivity(ComponentName mainActivity) {
        Intent intent = new Intent(ACTION_MAIN);
        intent.setComponent(mainActivity);
        intent.addCategory(CATEGORY_LAUNCHER);
        return intent;
    }

    public static Intent makeRestartActivityTask(ComponentName mainActivity) {
        Intent intent = makeMainActivity(mainActivity);
        intent.addFlags(FLAG_ACTIVITY_NEW_TASK | FLAG_ACTIVITY_CLEAR_TASK);
        return intent;
    }

    @Override
    public Object clone() {
        return new Intent(this);
    }

    public Intent cloneFilter() {
        Intent i = new Intent();
        i.mAction = mAction;
        i.mData = mData;
        i.mType = mType;
        i.mPackage = mPackage;
        i.mComponent = mComponent;
        if (mCategories != null) i.mCategories = new LinkedHashSet<>(mCategories);
        return i;
    }

    public String getAction() { return mAction; }
    public Uri getData() { return mData; }
    public String getDataString() { return mData == null ? null : mData.toString(); }
    public String getScheme() { return mData == null ? null : mData.getScheme(); }
    public String getType() { return mType; }
    public String getPackage() { return mPackage; }
    public ComponentName getComponent() { return mComponent; }
    public int getFlags() { return mFlags; }
    public Set<String> getCategories() { return mCategories; }
    public boolean hasCategory(String category) { return mCategories != null && mCategories.contains(category); }
    public ClipData getClipData() { return mClipData; }
    public Intent getSelector() { return mSelector; }
    public Bundle getExtras() { return mExtras == null ? null : new Bundle(mExtras); }
    public boolean hasExtra(String name) { return mExtras != null && mExtras.containsKey(name); }

    public Intent setAction(String action) { mAction = action; return this; }
    public Intent setData(Uri data) { mData = data; mType = null; return this; }
    public Intent setDataAndNormalize(Uri data) { return setData(data); }
    public Intent setType(String type) { mData = null; mType = type; return this; }
    public Intent setTypeAndNormalize(String type) { return setType(type); }
    public Intent setDataAndType(Uri data, String type) { mData = data; mType = type; return this; }
    public Intent setDataAndTypeAndNormalize(Uri data, String type) { return setDataAndType(data, type); }
    public Intent setPackage(String packageName) { mPackage = packageName; return this; }
    public Intent setComponent(ComponentName component) { mComponent = component; return this; }
    public Intent setClassName(Context packageContext, String className) { mComponent = new ComponentName(packageContext, className); return this; }
    public Intent setClassName(String packageName, String className) { mComponent = new ComponentName(packageName, className); return this; }
    public Intent setClass(Context packageContext, Class<?> cls) { mComponent = new ComponentName(packageContext, cls); return this; }
    public Intent setFlags(int flags) { mFlags = flags; return this; }
    public Intent addFlags(int flags) { mFlags |= flags; return this; }
    public void removeFlags(int flags) { mFlags &= ~flags; }
    public void setClipData(ClipData clip) { mClipData = clip; }
    public void setSelector(Intent selector) { mSelector = selector; }

    public Intent addCategory(String category) {
        if (mCategories == null) mCategories = new LinkedHashSet<>();
        mCategories.add(category);
        return this;
    }

    public void removeCategory(String category) {
        if (mCategories != null) mCategories.remove(category);
    }

    private Bundle extras() {
        if (mExtras == null) mExtras = new Bundle();
        return mExtras;
    }

    public Intent putExtra(String name, boolean value) { extras().putBoolean(name, value); return this; }
    public Intent putExtra(String name, byte value) { extras().putByte(name, value); return this; }
    public Intent putExtra(String name, char value) { extras().putChar(name, value); return this; }
    public Intent putExtra(String name, short value) { extras().putShort(name, value); return this; }
    public Intent putExtra(String name, int value) { extras().putInt(name, value); return this; }
    public Intent putExtra(String name, long value) { extras().putLong(name, value); return this; }
    public Intent putExtra(String name, float value) { extras().putFloat(name, value); return this; }
    public Intent putExtra(String name, double value) { extras().putDouble(name, value); return this; }
    public Intent putExtra(String name, String value) { extras().putString(name, value); return this; }
    public Intent putExtra(String name, CharSequence value) { extras().putCharSequence(name, value); return this; }
    public Intent putExtra(String name, Parcelable value) { extras().putParcelable(name, value); return this; }
    public Intent putExtra(String name, Parcelable[] value) { extras().putParcelableArray(name, value); return this; }
    public Intent putExtra(String name, Serializable value) { extras().putSerializable(name, value); return this; }
    public Intent putExtra(String name, boolean[] value) { extras().putBooleanArray(name, value); return this; }
    public Intent putExtra(String name, byte[] value) { extras().putByteArray(name, value); return this; }
    public Intent putExtra(String name, int[] value) { extras().putIntArray(name, value); return this; }
    public Intent putExtra(String name, long[] value) { extras().putLongArray(name, value); return this; }
    public Intent putExtra(String name, float[] value) { extras().putFloatArray(name, value); return this; }
    public Intent putExtra(String name, double[] value) { extras().putDoubleArray(name, value); return this; }
    public Intent putExtra(String name, String[] value) { extras().putStringArray(name, value); return this; }
    public Intent putExtra(String name, Bundle value) { extras().putBundle(name, value); return this; }
    public Intent putExtras(Intent src) { if (src.mExtras != null) extras().putAll(src.mExtras); return this; }
    public Intent putExtras(Bundle extras) { extras().putAll(extras); return this; }
    public Intent putParcelableArrayListExtra(String name, ArrayList<? extends Parcelable> value) { extras().putParcelableArrayList(name, value); return this; }
    public Intent putIntegerArrayListExtra(String name, ArrayList<Integer> value) { extras().putIntegerArrayList(name, value); return this; }
    public Intent putStringArrayListExtra(String name, ArrayList<String> value) { extras().putStringArrayList(name, value); return this; }
    public void removeExtra(String name) { if (mExtras != null) mExtras.remove(name); }
    public Intent replaceExtras(Bundle extras) { mExtras = extras == null ? null : new Bundle(extras); return this; }

    public boolean getBooleanExtra(String name, boolean defaultValue) { return mExtras == null ? defaultValue : mExtras.getBoolean(name, defaultValue); }
    public byte getByteExtra(String name, byte defaultValue) { return mExtras == null ? defaultValue : mExtras.getByte(name, defaultValue); }
    public short getShortExtra(String name, short defaultValue) { return mExtras == null ? defaultValue : mExtras.getShort(name, defaultValue); }
    public char getCharExtra(String name, char defaultValue) { return mExtras == null ? defaultValue : mExtras.getChar(name, defaultValue); }
    public int getIntExtra(String name, int defaultValue) { return mExtras == null ? defaultValue : mExtras.getInt(name, defaultValue); }
    public long getLongExtra(String name, long defaultValue) { return mExtras == null ? defaultValue : mExtras.getLong(name, defaultValue); }
    public float getFloatExtra(String name, float defaultValue) { return mExtras == null ? defaultValue : mExtras.getFloat(name, defaultValue); }
    public double getDoubleExtra(String name, double defaultValue) { return mExtras == null ? defaultValue : mExtras.getDouble(name, defaultValue); }
    public String getStringExtra(String name) { return mExtras == null ? null : mExtras.getString(name); }
    public CharSequence getCharSequenceExtra(String name) { return mExtras == null ? null : mExtras.getCharSequence(name); }
    public <T extends Parcelable> T getParcelableExtra(String name) { return mExtras == null ? null : mExtras.getParcelable(name); }
    public <T> T getParcelableExtra(String name, Class<T> clazz) { return mExtras == null ? null : mExtras.getParcelable(name, clazz); }
    public Parcelable[] getParcelableArrayExtra(String name) { return mExtras == null ? null : mExtras.getParcelableArray(name); }
    public <T extends Parcelable> ArrayList<T> getParcelableArrayListExtra(String name) { return mExtras == null ? null : mExtras.getParcelableArrayList(name); }
    public Serializable getSerializableExtra(String name) { return mExtras == null ? null : mExtras.getSerializable(name); }
    public ArrayList<Integer> getIntegerArrayListExtra(String name) { return mExtras == null ? null : mExtras.getIntegerArrayList(name); }
    public ArrayList<String> getStringArrayListExtra(String name) { return mExtras == null ? null : mExtras.getStringArrayList(name); }
    public boolean[] getBooleanArrayExtra(String name) { return mExtras == null ? null : mExtras.getBooleanArray(name); }
    public byte[] getByteArrayExtra(String name) { return mExtras == null ? null : mExtras.getByteArray(name); }
    public int[] getIntArrayExtra(String name) { return mExtras == null ? null : mExtras.getIntArray(name); }
    public long[] getLongArrayExtra(String name) { return mExtras == null ? null : mExtras.getLongArray(name); }
    public float[] getFloatArrayExtra(String name) { return mExtras == null ? null : mExtras.getFloatArray(name); }
    public double[] getDoubleArrayExtra(String name) { return mExtras == null ? null : mExtras.getDoubleArray(name); }
    public String[] getStringArrayExtra(String name) { return mExtras == null ? null : mExtras.getStringArray(name); }
    public Bundle getBundleExtra(String name) { return mExtras == null ? null : mExtras.getBundle(name); }

    public ComponentName resolveActivity(android.content.pm.PackageManager pm) {
        if (mComponent != null) return mComponent;
        if (pm == null) return null;
        android.content.pm.ResolveInfo info = pm.resolveActivity(this, 0);
        if (info != null) {
            String pkg = info.activityInfo != null && info.activityInfo.applicationInfo != null
                ? info.activityInfo.applicationInfo.packageName : "com.desktop";
            String cls = info.activityInfo != null ? info.activityInfo.name : "Activity";
            return new ComponentName(pkg, cls);
        }
        if (ACTION_WEB_SEARCH.equals(mAction) || ACTION_VIEW.equals(mAction)) {
            return new ComponentName("com.desktop.browser", "BrowserActivity");
        }
        return null;
    }

    public String toUri(int flags) {
        return mData != null ? mData.toString() : "intent:#Intent;action=" + mAction + ";end";
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
    }

    @Override
    public String toString() {
        StringBuilder b = new StringBuilder("Intent { ");
        if (mAction != null) b.append("act=").append(mAction).append(' ');
        if (mCategories != null) b.append("cat=").append(mCategories).append(' ');
        if (mData != null) b.append("dat=").append(mData).append(' ');
        if (mType != null) b.append("typ=").append(mType).append(' ');
        if (mFlags != 0) b.append("flg=0x").append(Integer.toHexString(mFlags)).append(' ');
        if (mPackage != null) b.append("pkg=").append(mPackage).append(' ');
        if (mComponent != null) b.append("cmp=").append(mComponent.flattenToShortString()).append(' ');
        if (mExtras != null) b.append("(has extras) ");
        return b.append('}').toString();
    }
}
