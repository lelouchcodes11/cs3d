package android.widget;

import android.content.Context;
import android.content.res.Resources;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import com.lagradost.desktop.runtime.res.FrameworkResources;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

public class ArrayAdapter<T> extends BaseAdapter implements Filterable, ThemedSpinnerAdapter {
    private final Context mContext;
    private final int mResource;
    private int mDropDownResource;
    private final int mFieldId;
    private final List<T> mObjects;
    private boolean mNotifyOnChange = true;
    private Resources.Theme mDropDownTheme;

    public ArrayAdapter(Context context, int resource) {
        this(context, resource, 0, new ArrayList<T>());
    }

    public ArrayAdapter(Context context, int resource, int textViewResourceId) {
        this(context, resource, textViewResourceId, new ArrayList<T>());
    }

    public ArrayAdapter(Context context, int resource, T[] objects) {
        this(context, resource, 0, new ArrayList<T>(Arrays.asList(objects)));
    }

    public ArrayAdapter(Context context, int resource, int textViewResourceId, T[] objects) {
        this(context, resource, textViewResourceId, new ArrayList<T>(Arrays.asList(objects)));
    }

    public ArrayAdapter(Context context, int resource, List<T> objects) {
        this(context, resource, 0, objects);
    }

    public ArrayAdapter(Context context, int resource, int textViewResourceId, List<T> objects) {
        mContext = context;
        mResource = resource;
        mDropDownResource = resource;
        mFieldId = textViewResourceId;
        mObjects = objects;
    }

    public static ArrayAdapter<CharSequence> createFromResource(Context context, int textArrayResId, int textViewResId) {
        CharSequence[] strings = context.getResources().getTextArray(textArrayResId);
        return new ArrayAdapter<CharSequence>(context, textViewResId, 0, new ArrayList<CharSequence>(Arrays.asList(strings)));
    }

    public void add(T object) {
        mObjects.add(object);
        if (mNotifyOnChange) notifyDataSetChanged();
    }

    public void addAll(Collection<? extends T> collection) {
        for (T item : collection) {
            if (item != null) mObjects.add(item);
        }
        if (mNotifyOnChange) notifyDataSetChanged();
    }

    @SafeVarargs
    public final void addAll(T... items) {
        Collections.addAll(mObjects, items);
        if (mNotifyOnChange) notifyDataSetChanged();
    }

    public void insert(T object, int index) {
        mObjects.add(index, object);
        if (mNotifyOnChange) notifyDataSetChanged();
    }

    public void remove(T object) {
        mObjects.remove(object);
        if (mNotifyOnChange) notifyDataSetChanged();
    }

    public void clear() {
        mObjects.clear();
        if (mNotifyOnChange) notifyDataSetChanged();
    }

    public void sort(Comparator<? super T> comparator) {
        mObjects.sort(comparator);
        if (mNotifyOnChange) notifyDataSetChanged();
    }

    @Override
    public void notifyDataSetChanged() {
        super.notifyDataSetChanged();
        mNotifyOnChange = true;
    }

    public void setNotifyOnChange(boolean notifyOnChange) {
        mNotifyOnChange = notifyOnChange;
    }

    public Context getContext() {
        return mContext;
    }

    @Override
    public int getCount() {
        return mObjects.size();
    }

    @Override
    public T getItem(int position) {
        return position >= 0 && position < mObjects.size() ? mObjects.get(position) : null;
    }

    public int getPosition(T item) {
        return mObjects.indexOf(item);
    }

    @Override
    public long getItemId(int position) {
        return position;
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        return createViewFromResource(position, convertView, parent, mResource);
    }

    private View createViewFromResource(int position, View convertView, ViewGroup parent, int resource) {
        View view = convertView != null ? convertView : LayoutInflater.from(mContext).inflate(resource, parent, false);
        TextView text;
        try {
            if (mFieldId == 0) {
                text = view instanceof TextView ? (TextView) view : (TextView) view.findViewById(FrameworkResources.ID_TEXT1);
            } else {
                text = (TextView) view.findViewById(mFieldId);
            }
        } catch (ClassCastException e) {
            throw new IllegalStateException("ArrayAdapter requires the resource ID to be a TextView", e);
        }
        T item = getItem(position);
        if (text != null) {
            text.setText(item instanceof CharSequence ? (CharSequence) item : (item != null ? item.toString() : ""));
        }
        return view;
    }

    public void setDropDownViewResource(int resource) {
        mDropDownResource = resource;
    }

    @Override
    public View getDropDownView(int position, View convertView, ViewGroup parent) {
        return createViewFromResource(position, convertView, parent, mDropDownResource);
    }

    @Override
    public void setDropDownViewTheme(Resources.Theme theme) {
        mDropDownTheme = theme;
    }

    @Override
    public Resources.Theme getDropDownViewTheme() {
        return mDropDownTheme;
    }

    @Override
    public Filter getFilter() {
        return new Filter() {
            @Override
            protected FilterResults performFiltering(CharSequence constraint) {
                FilterResults results = new FilterResults();
                List<T> filtered;
                if (constraint == null || constraint.length() == 0) {
                    filtered = new ArrayList<T>(mObjects);
                } else {
                    filtered = new ArrayList<T>();
                    String prefixString = constraint.toString().toLowerCase();
                    for (T value : mObjects) {
                        if (value != null && value.toString().toLowerCase().startsWith(prefixString)) {
                            filtered.add(value);
                        }
                    }
                }
                results.values = filtered;
                results.count = filtered.size();
                return results;
            }

            @Override
            protected void publishResults(CharSequence constraint, FilterResults results) {
                notifyDataSetChanged();
            }
        };
    }
}
