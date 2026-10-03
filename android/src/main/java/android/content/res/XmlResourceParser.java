package android.content.res;

import android.util.AttributeSet;
import org.xmlpull.v1.XmlPullParser;

public interface XmlResourceParser extends XmlPullParser, AttributeSet, AutoCloseable {
    @Override
    String getAttributeNamespace(int index);

    @Override
    int getAttributeCount();

    @Override
    String getAttributeName(int index);

    @Override
    String getAttributeValue(int index);

    @Override
    String getAttributeValue(String namespace, String name);

    @Override
    String getPositionDescription();

    @Override
    void close();
}
