package android.database;

import java.io.Closeable;

public interface Cursor extends Closeable {
    int getCount();

    int getPosition();

    boolean moveToFirst();

    boolean moveToNext();

    boolean moveToPosition(int position);

    int getColumnIndex(String columnName);

    int getColumnIndexOrThrow(String columnName);

    String getString(int columnIndex);

    int getInt(int columnIndex);

    long getLong(int columnIndex);

    boolean isNull(int columnIndex);

    @Override
    void close();

    boolean isClosed();
}
