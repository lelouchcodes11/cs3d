package com.lagradost.desktop.runtime.res;

import android.content.res.XmlResourceParser;
import android.util.TypedValue;
import org.xmlpull.v1.XmlPullParserException;

import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/**
 * Parser for Android compiled (binary) XML as found in APKs/.cs3 files (layouts, drawables, xml).
 * Implements XmlResourceParser, including typed attribute access through the resource id map.
 */
public final class AXmlResourceParser implements XmlResourceParser {
    private static final int RES_STRING_POOL_TYPE = 0x0001;
    private static final int RES_XML_TYPE = 0x0003;
    private static final int RES_XML_START_NAMESPACE_TYPE = 0x0100;
    private static final int RES_XML_END_NAMESPACE_TYPE = 0x0101;
    private static final int RES_XML_START_ELEMENT_TYPE = 0x0102;
    private static final int RES_XML_END_ELEMENT_TYPE = 0x0103;
    private static final int RES_XML_CDATA_TYPE = 0x0104;
    private static final int RES_XML_RESOURCE_MAP_TYPE = 0x0180;

    public static final String ANDROID_NS = "http://schemas.android.com/apk/res/android";

    /** Resources this parser was opened from, used to resolve references in attributes */
    public android.content.res.Resources resources;

    private final ByteBuffer buf;
    private final StringPool strings;
    private int[] resourceIds = new int[0];

    private int event = START_DOCUMENT;
    private int depth = 0;
    private int lineNumber = 0;
    private int pos;
    private final int end;

    // current element
    private String name;
    private String namespace;
    private String text;
    private int[] attrNs = new int[0];
    private int[] attrName = new int[0];
    private int[] attrRaw = new int[0];
    private int[] attrType = new int[0];
    private int[] attrData = new int[0];
    private int attrCount = 0;
    private int idIndex = -1;
    private int classIndex = -1;
    private int styleIndex = -1;
    private boolean pendingEndAfterEmpty = false;

    private final java.util.ArrayList<String[]> nsStack = new java.util.ArrayList<>();

    public AXmlResourceParser(byte[] data) throws IOException {
        buf = ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN);
        int type = buf.getShort(0) & 0xffff;
        if (type != RES_XML_TYPE) throw new IOException("Not a binary XML file (type 0x" + Integer.toHexString(type) + ")");
        int headerSize = buf.getShort(2) & 0xffff;
        end = Math.min(buf.getInt(4), data.length);
        int p = headerSize;
        StringPool pool = null;
        while (p < end) {
            int ct = buf.getShort(p) & 0xffff;
            int cs = buf.getInt(p + 4);
            if (ct == RES_STRING_POOL_TYPE) {
                pool = new StringPool(buf, p);
            } else if (ct == RES_XML_RESOURCE_MAP_TYPE) {
                int hs = buf.getShort(p + 2) & 0xffff;
                int n = (cs - hs) / 4;
                resourceIds = new int[n];
                for (int i = 0; i < n; i++) resourceIds[i] = buf.getInt(p + hs + i * 4);
            } else if (ct >= RES_XML_START_NAMESPACE_TYPE && ct <= RES_XML_CDATA_TYPE) {
                break;
            }
            if (cs <= 0) break;
            p += cs;
        }
        strings = pool == null ? new StringPool() : pool;
        pos = p;
    }

    public static AXmlResourceParser fromStream(InputStream in) throws IOException {
        return new AXmlResourceParser(in.readAllBytes());
    }

    private String str(int index) {
        return index < 0 ? null : strings.get(index);
    }

    // ------------------------------------------------------------------ events

    @Override
    public int next() throws XmlPullParserException, IOException {
        if (pendingEndAfterEmpty) {
            pendingEndAfterEmpty = false;
        }
        if (event == END_TAG) {
            depth--;
        }
        if (event == END_DOCUMENT) return END_DOCUMENT;
        while (pos < end) {
            int type = buf.getShort(pos) & 0xffff;
            int headerSize = buf.getShort(pos + 2) & 0xffff;
            int size = buf.getInt(pos + 4);
            if (size <= 0) break;
            int chunk = pos;
            pos += size;
            if (type < RES_XML_START_NAMESPACE_TYPE || type > RES_XML_CDATA_TYPE) continue;
            lineNumber = buf.getInt(chunk + 8);
            int ext = chunk + headerSize;
            switch (type) {
                case RES_XML_START_NAMESPACE_TYPE: {
                    nsStack.add(new String[]{str(buf.getInt(ext)), str(buf.getInt(ext + 4))});
                    continue;
                }
                case RES_XML_END_NAMESPACE_TYPE: {
                    if (!nsStack.isEmpty()) nsStack.remove(nsStack.size() - 1);
                    continue;
                }
                case RES_XML_START_ELEMENT_TYPE: {
                    namespace = str(buf.getInt(ext));
                    name = str(buf.getInt(ext + 4));
                    int attributeStart = buf.getShort(ext + 8) & 0xffff;
                    int attributeSize = buf.getShort(ext + 10) & 0xffff;
                    attrCount = buf.getShort(ext + 12) & 0xffff;
                    idIndex = (buf.getShort(ext + 14) & 0xffff) - 1;
                    classIndex = (buf.getShort(ext + 16) & 0xffff) - 1;
                    styleIndex = (buf.getShort(ext + 18) & 0xffff) - 1;
                    attrNs = new int[attrCount];
                    attrName = new int[attrCount];
                    attrRaw = new int[attrCount];
                    attrType = new int[attrCount];
                    attrData = new int[attrCount];
                    int a = ext + attributeStart;
                    for (int i = 0; i < attrCount; i++) {
                        attrNs[i] = buf.getInt(a);
                        attrName[i] = buf.getInt(a + 4);
                        attrRaw[i] = buf.getInt(a + 8);
                        attrType[i] = buf.get(a + 15) & 0xff;
                        attrData[i] = buf.getInt(a + 16);
                        a += attributeSize == 0 ? 20 : attributeSize;
                    }
                    text = null;
                    depth++;
                    event = START_TAG;
                    return event;
                }
                case RES_XML_END_ELEMENT_TYPE: {
                    namespace = str(buf.getInt(ext));
                    name = str(buf.getInt(ext + 4));
                    attrCount = 0;
                    event = END_TAG;
                    return event;
                }
                case RES_XML_CDATA_TYPE: {
                    text = str(buf.getInt(ext));
                    event = TEXT;
                    return event;
                }
                default:
            }
        }
        event = END_DOCUMENT;
        return event;
    }

    @Override
    public int nextToken() throws XmlPullParserException, IOException {
        return next();
    }

    @Override
    public int getEventType() {
        return event;
    }

    @Override
    public int nextTag() throws XmlPullParserException, IOException {
        int e = next();
        if (e == TEXT && isWhitespace()) e = next();
        if (e != START_TAG && e != END_TAG) throw new XmlPullParserException("expected start or end tag", this, null);
        return e;
    }

    @Override
    public String nextText() throws XmlPullParserException, IOException {
        if (event != START_TAG) throw new XmlPullParserException("parser must be on START_TAG to read next text", this, null);
        int e = next();
        if (e == TEXT) {
            String result = text;
            e = next();
            if (e != END_TAG) throw new XmlPullParserException("event TEXT must be immediately followed by END_TAG", this, null);
            return result;
        } else if (e == END_TAG) {
            return "";
        }
        throw new XmlPullParserException("parser must be on START_TAG or TEXT to read text", this, null);
    }

    @Override
    public void require(int type, String namespace, String name) throws XmlPullParserException {
        if (type != event || (namespace != null && !namespace.equals(getNamespace())) || (name != null && !name.equals(getName()))) {
            throw new XmlPullParserException("expected " + TYPES[type], this, null);
        }
    }

    @Override public String getName() { return event == START_TAG || event == END_TAG ? name : null; }
    @Override public String getNamespace() { return namespace == null ? "" : namespace; }
    @Override public String getText() { return event == TEXT ? text : null; }
    @Override public char[] getTextCharacters(int[] holderForStartAndLength) {
        String t = getText();
        if (t == null) return null;
        holderForStartAndLength[0] = 0;
        holderForStartAndLength[1] = t.length();
        return t.toCharArray();
    }
    @Override public int getDepth() { return depth; }
    @Override public int getLineNumber() { return lineNumber; }
    @Override public int getColumnNumber() { return -1; }
    @Override public String getPositionDescription() { return "Binary XML file line #" + lineNumber; }
    @Override public boolean isWhitespace() { return text == null || text.trim().isEmpty(); }
    @Override public boolean isEmptyElementTag() { return false; }
    @Override public String getPrefix() { return null; }
    @Override public boolean isAttributeDefault(int index) { return false; }
    @Override public String getInputEncoding() { return null; }
    @Override public void defineEntityReplacementText(String entityName, String replacementText) {}
    @Override public void setFeature(String name, boolean state) {}
    @Override public boolean getFeature(String name) { return false; }
    @Override public void setProperty(String name, Object value) {}
    @Override public Object getProperty(String name) { return null; }
    @Override public void setInput(Reader in) { throw new UnsupportedOperationException(); }
    @Override public void setInput(InputStream inputStream, String inputEncoding) { throw new UnsupportedOperationException(); }
    @Override public int getNamespaceCount(int depth) { return nsStack.size(); }
    @Override public String getNamespacePrefix(int pos) { return nsStack.get(pos)[0]; }
    @Override public String getNamespaceUri(int pos) { return nsStack.get(pos)[1]; }

    @Override
    public String getNamespace(String prefix) {
        for (int i = nsStack.size() - 1; i >= 0; i--) {
            if (prefix == null ? nsStack.get(i)[0] == null : prefix.equals(nsStack.get(i)[0])) return nsStack.get(i)[1];
        }
        return null;
    }

    @Override
    public void close() {
    }

    // ------------------------------------------------------------------ attributes

    @Override public int getAttributeCount() { return event == START_TAG ? attrCount : -1; }
    @Override public String getAttributeNamespace(int index) { return str(attrNs[index]) == null ? "" : str(attrNs[index]); }
    @Override public String getAttributePrefix(int index) { return null; }
    @Override public String getAttributeType(int index) { return "CDATA"; }

    @Override
    public String getAttributeName(int index) {
        String n = str(attrName[index]);
        if ((n == null || n.isEmpty()) && attrName[index] < resourceIds.length) {
            return "0x" + Integer.toHexString(resourceIds[attrName[index]]);
        }
        return n;
    }

    @Override
    public int getAttributeNameResource(int index) {
        int n = attrName[index];
        return n >= 0 && n < resourceIds.length ? resourceIds[n] : 0;
    }

    /** Typed attribute data type (TypedValue.TYPE_*) */
    public int getAttributeDataType(int index) {
        return attrType[index];
    }

    /** Typed attribute raw data */
    public int getAttributeData(int index) {
        return attrData[index];
    }

    /** true if the attribute has a raw string value */
    public boolean hasRawValue(int index) {
        return attrRaw[index] != -1;
    }

    public TypedValue getTypedValue(int index) {
        TypedValue tv = new TypedValue();
        tv.type = attrType[index];
        tv.data = attrData[index];
        if (attrType[index] == TypedValue.TYPE_STRING) tv.string = str(attrData[index]);
        else if (attrRaw[index] != -1) tv.string = str(attrRaw[index]);
        if (attrType[index] == TypedValue.TYPE_REFERENCE) tv.resourceId = attrData[index];
        return tv;
    }

    @Override
    public String getAttributeValue(int index) {
        if (attrRaw[index] != -1) return str(attrRaw[index]);
        int type = attrType[index];
        int data = attrData[index];
        switch (type) {
            case TypedValue.TYPE_STRING: return str(data);
            case TypedValue.TYPE_REFERENCE: return "@" + data;
            case TypedValue.TYPE_ATTRIBUTE: return "?" + data;
            case TypedValue.TYPE_INT_BOOLEAN: return data != 0 ? "true" : "false";
            case TypedValue.TYPE_INT_HEX: return "0x" + Integer.toHexString(data);
            case TypedValue.TYPE_FLOAT: return Float.toString(Float.intBitsToFloat(data));
            case TypedValue.TYPE_DIMENSION: return complexToString(data, false);
            case TypedValue.TYPE_FRACTION: return complexToString(data, true);
            case TypedValue.TYPE_NULL: return null;
            default:
                if (type >= TypedValue.TYPE_FIRST_COLOR_INT && type <= TypedValue.TYPE_LAST_COLOR_INT) {
                    return String.format("#%08x", data);
                }
                return Integer.toString(data);
        }
    }

    private static final String[] DIMEN_UNITS = {"px", "dip", "sp", "pt", "in", "mm", "", ""};
    private static final String[] FRACTION_UNITS = {"%", "%p", "", "", "", "", "", ""};

    private static String complexToString(int data, boolean fraction) {
        float value = TypedValue.complexToFloat(data);
        int unit = data & TypedValue.COMPLEX_UNIT_MASK;
        return (fraction ? value * 100 : value) + (fraction ? FRACTION_UNITS[unit] : DIMEN_UNITS[unit]);
    }

    private int find(String ns, String n) {
        if (n == null) return -1;
        for (int i = 0; i < attrCount; i++) {
            String an = str(attrName[i]);
            if (n.equals(an)) {
                String ans = str(attrNs[i]);
                if (ns == null || ns.equals(ans) || ans == null || ans.isEmpty()) return i;
            }
        }
        // Fallback: namespace mismatch (e.g. app vs res-auto)
        for (int i = 0; i < attrCount; i++) if (n.equals(str(attrName[i]))) return i;
        return -1;
    }

    @Override
    public String getAttributeValue(String namespace, String name) {
        int i = find(namespace, name);
        return i < 0 ? null : getAttributeValue(i);
    }

    /** Index of the attribute with the given name resource id, or -1 */
    public int findByResourceId(int resId) {
        for (int i = 0; i < attrCount; i++) if (getAttributeNameResource(i) == resId) return i;
        return -1;
    }

    /** Index of the attribute by local name (any namespace), or -1 */
    public int findByName(String name) {
        return find(null, name);
    }

    @Override
    public boolean getAttributeBooleanValue(String namespace, String attribute, boolean defaultValue) {
        int i = find(namespace, attribute);
        return i < 0 ? defaultValue : getAttributeBooleanValue(i, defaultValue);
    }

    @Override
    public boolean getAttributeBooleanValue(int index, boolean defaultValue) {
        if (attrType[index] >= TypedValue.TYPE_FIRST_INT && attrType[index] <= TypedValue.TYPE_LAST_INT) return attrData[index] != 0;
        String v = getAttributeValue(index);
        return v == null ? defaultValue : Boolean.parseBoolean(v);
    }

    @Override
    public int getAttributeResourceValue(String namespace, String attribute, int defaultValue) {
        int i = find(namespace, attribute);
        return i < 0 ? defaultValue : getAttributeResourceValue(i, defaultValue);
    }

    @Override
    public int getAttributeResourceValue(int index, int defaultValue) {
        return attrType[index] == TypedValue.TYPE_REFERENCE ? attrData[index] : defaultValue;
    }

    @Override
    public int getAttributeIntValue(String namespace, String attribute, int defaultValue) {
        int i = find(namespace, attribute);
        return i < 0 ? defaultValue : getAttributeIntValue(i, defaultValue);
    }

    @Override
    public int getAttributeIntValue(int index, int defaultValue) {
        if (attrType[index] >= TypedValue.TYPE_FIRST_INT && attrType[index] <= TypedValue.TYPE_LAST_INT) return attrData[index];
        try {
            return Integer.parseInt(getAttributeValue(index));
        } catch (Exception e) {
            return defaultValue;
        }
    }

    @Override
    public int getAttributeUnsignedIntValue(String namespace, String attribute, int defaultValue) {
        return getAttributeIntValue(namespace, attribute, defaultValue);
    }

    @Override
    public int getAttributeUnsignedIntValue(int index, int defaultValue) {
        return getAttributeIntValue(index, defaultValue);
    }

    @Override
    public float getAttributeFloatValue(String namespace, String attribute, float defaultValue) {
        int i = find(namespace, attribute);
        return i < 0 ? defaultValue : getAttributeFloatValue(i, defaultValue);
    }

    @Override
    public float getAttributeFloatValue(int index, float defaultValue) {
        if (attrType[index] == TypedValue.TYPE_FLOAT) return Float.intBitsToFloat(attrData[index]);
        try {
            return Float.parseFloat(getAttributeValue(index));
        } catch (Exception e) {
            return defaultValue;
        }
    }

    @Override
    public int getAttributeListValue(String namespace, String attribute, String[] options, int defaultValue) {
        int i = find(namespace, attribute);
        return i < 0 ? defaultValue : getAttributeListValue(i, options, defaultValue);
    }

    @Override
    public int getAttributeListValue(int index, String[] options, int defaultValue) {
        String v = getAttributeValue(index);
        if (v == null) return defaultValue;
        for (int j = 0; j < options.length; j++) if (options[j].equals(v)) return j;
        return defaultValue;
    }

    @Override public String getIdAttribute() { return idIndex >= 0 ? getAttributeValue(idIndex) : null; }
    @Override public String getClassAttribute() { return classIndex >= 0 ? getAttributeValue(classIndex) : null; }
    @Override public int getIdAttributeResourceValue(int defaultValue) { return idIndex >= 0 ? getAttributeResourceValue(idIndex, defaultValue) : defaultValue; }
    @Override public int getStyleAttribute() { return styleIndex >= 0 ? attrData[styleIndex] : 0; }

    // ------------------------------------------------------------------ string pool

    public static final class StringPool {
        private final String[] cache;
        private final ByteBuffer buf;
        private final int stringsStart;
        private final int[] offsets;
        private final boolean utf8;

        StringPool() {
            cache = new String[0];
            buf = null;
            stringsStart = 0;
            offsets = new int[0];
            utf8 = true;
        }

        public StringPool(ByteBuffer buf, int chunkStart) {
            this.buf = buf;
            int headerSize = buf.getShort(chunkStart + 2) & 0xffff;
            int count = buf.getInt(chunkStart + 8);
            int flags = buf.getInt(chunkStart + 16);
            stringsStart = chunkStart + buf.getInt(chunkStart + 20);
            utf8 = (flags & 0x100) != 0;
            offsets = new int[count];
            for (int i = 0; i < count; i++) offsets[i] = buf.getInt(chunkStart + headerSize + i * 4);
            cache = new String[count];
        }

        public int size() {
            return offsets.length;
        }

        public String get(int index) {
            if (index < 0 || index >= offsets.length) return null;
            String s = cache[index];
            if (s != null) return s;
            int p = stringsStart + offsets[index];
            if (utf8) {
                int u16len = buf.get(p++) & 0xff;
                if ((u16len & 0x80) != 0) p++;
                int u8len = buf.get(p++) & 0xff;
                if ((u8len & 0x80) != 0) u8len = ((u8len & 0x7f) << 8) | (buf.get(p++) & 0xff);
                byte[] bytes = new byte[u8len];
                for (int i = 0; i < u8len; i++) bytes[i] = buf.get(p + i);
                s = new String(bytes, java.nio.charset.StandardCharsets.UTF_8);
            } else {
                int len = buf.getShort(p) & 0xffff;
                p += 2;
                if ((len & 0x8000) != 0) {
                    len = ((len & 0x7fff) << 16) | (buf.getShort(p) & 0xffff);
                    p += 2;
                }
                char[] chars = new char[len];
                for (int i = 0; i < len; i++) chars[i] = buf.getChar(p + i * 2);
                s = new String(chars);
            }
            cache[index] = s;
            return s;
        }
    }
}
