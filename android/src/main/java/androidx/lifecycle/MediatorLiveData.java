package androidx.lifecycle;

import java.util.LinkedHashMap;
import java.util.Map;

public class MediatorLiveData<T> extends MutableLiveData<T> {
    private final Map<LiveData<?>, Source<?>> mSources = new LinkedHashMap<>();

    public MediatorLiveData() {
        super();
    }

    public MediatorLiveData(T value) {
        super(value);
    }

    public <S> void addSource(LiveData<S> source, Observer<? super S> onChanged) {
        if (source == null) {
            throw new NullPointerException("source cannot be null");
        }
        Source<S> e = new Source<>(source, onChanged);
        @SuppressWarnings("unchecked")
        Source<S> existing = (Source<S>) mSources.get(source);
        if (existing != null && existing.mObserver != onChanged) {
            throw new IllegalArgumentException("This source was already added with the different observer");
        }
        if (existing != null) {
            return;
        }
        mSources.put(source, e);
        if (hasActiveObservers()) {
            e.plug();
        }
    }

    public <S> void removeSource(LiveData<S> toRemote) {
        Source<?> source = mSources.remove(toRemote);
        if (source != null) {
            source.unplug();
        }
    }

    @Override
    protected void onActive() {
        for (Source<?> source : mSources.values()) {
            source.plug();
        }
    }

    @Override
    protected void onInactive() {
        for (Source<?> source : mSources.values()) {
            source.unplug();
        }
    }

    private static class Source<V> implements Observer<V> {
        final LiveData<V> mLiveData;
        final Observer<? super V> mObserver;
        int mVersion = START_VERSION;

        Source(LiveData<V> liveData, final Observer<? super V> observer) {
            mLiveData = liveData;
            mObserver = observer;
        }

        void plug() {
            mLiveData.observeForever(this);
        }

        void unplug() {
            mLiveData.removeObserver(this);
        }

        @Override
        public void onChanged(V v) {
            if (mVersion != mLiveData.getVersion()) {
                mVersion = mLiveData.getVersion();
                mObserver.onChanged(v);
            }
        }
    }
}
