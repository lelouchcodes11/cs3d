package android.os;

public interface Parcelable {
    int PARCELABLE_WRITE_RETURN_VALUE = 0x0001;
    int CONTENTS_FILE_DESCRIPTOR = 0x0001;

    int describeContents();

    void writeToParcel(Parcel dest, int flags);

    interface Creator<T> {
        T createFromParcel(Parcel source);

        T[] newArray(int size);
    }

    interface ClassLoaderCreator<T> extends Creator<T> {
        T createFromParcel(Parcel source, ClassLoader loader);
    }
}
