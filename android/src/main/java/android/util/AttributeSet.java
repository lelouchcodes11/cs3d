package android.util;

public interface AttributeSet {
    int getAttributeCount();

    default String getAttributeNamespace(int index) {
        return null;
    }

    String getAttributeName(int index);

    String getAttributeValue(int index);

    String getAttributeValue(String namespace, String name);

    default String getPositionDescription() {
        return "";
    }

    default int getAttributeNameResource(int index) {
        return 0;
    }

    default int getAttributeListValue(String namespace, String attribute, String[] options, int defaultValue) {
        String v = getAttributeValue(namespace, attribute);
        if (v == null) return defaultValue;
        for (int i = 0; i < options.length; i++) if (options[i].equals(v)) return i;
        return defaultValue;
    }

    default boolean getAttributeBooleanValue(String namespace, String attribute, boolean defaultValue) {
        String v = getAttributeValue(namespace, attribute);
        return v == null ? defaultValue : Boolean.parseBoolean(v);
    }

    default int getAttributeResourceValue(String namespace, String attribute, int defaultValue) {
        return defaultValue;
    }

    default int getAttributeIntValue(String namespace, String attribute, int defaultValue) {
        String v = getAttributeValue(namespace, attribute);
        if (v == null) return defaultValue;
        try {
            return v.startsWith("0x") ? (int) Long.parseLong(v.substring(2), 16) : Integer.parseInt(v);
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    default int getAttributeUnsignedIntValue(String namespace, String attribute, int defaultValue) {
        return getAttributeIntValue(namespace, attribute, defaultValue);
    }

    default float getAttributeFloatValue(String namespace, String attribute, float defaultValue) {
        String v = getAttributeValue(namespace, attribute);
        if (v == null) return defaultValue;
        try {
            return Float.parseFloat(v);
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    default int getAttributeListValue(int index, String[] options, int defaultValue) {
        return defaultValue;
    }

    default boolean getAttributeBooleanValue(int index, boolean defaultValue) {
        String v = getAttributeValue(index);
        return v == null ? defaultValue : Boolean.parseBoolean(v);
    }

    default int getAttributeResourceValue(int index, int defaultValue) {
        return defaultValue;
    }

    default int getAttributeIntValue(int index, int defaultValue) {
        try {
            return Integer.parseInt(getAttributeValue(index));
        } catch (Exception e) {
            return defaultValue;
        }
    }

    default int getAttributeUnsignedIntValue(int index, int defaultValue) {
        return getAttributeIntValue(index, defaultValue);
    }

    default float getAttributeFloatValue(int index, float defaultValue) {
        try {
            return Float.parseFloat(getAttributeValue(index));
        } catch (Exception e) {
            return defaultValue;
        }
    }

    default String getIdAttribute() {
        return getAttributeValue(null, "id");
    }

    default String getClassAttribute() {
        return getAttributeValue(null, "class");
    }

    default int getIdAttributeResourceValue(int defaultValue) {
        return defaultValue;
    }

    default int getStyleAttribute() {
        return 0;
    }
}
