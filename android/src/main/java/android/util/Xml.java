package android.util;

import org.kxml2.io.KXmlParser;
import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;
import org.xmlpull.v1.XmlSerializer;

import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.io.StringReader;

public class Xml {
    public static final String FEATURE_RELAXED = "http://xmlpull.org/v1/doc/features.html#relaxed";

    public enum Encoding {
        US_ASCII("US-ASCII"), UTF_8("UTF-8"), UTF_16("UTF-16"), ISO_8859_1("ISO-8859-1");

        final String expatName;

        Encoding(String expatName) {
            this.expatName = expatName;
        }
    }

    public static XmlPullParser newPullParser() {
        try {
            KXmlParser parser = new KXmlParser();
            try {
                // Supported by the libcore KXmlParser, not by the kxml2 release; doctypes are skipped either way
                parser.setFeature(XmlPullParser.FEATURE_PROCESS_DOCDECL, true);
            } catch (XmlPullParserException ignored) {
            }
            parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, true);
            return parser;
        } catch (XmlPullParserException e) {
            throw new AssertionError(e);
        }
    }

    public static XmlSerializer newSerializer() {
        return new org.kxml2.io.KXmlSerializer();
    }

    public static void parse(String xml, org.xml.sax.ContentHandler contentHandler) throws org.xml.sax.SAXException {
        try {
            javax.xml.parsers.SAXParserFactory f = javax.xml.parsers.SAXParserFactory.newInstance();
            f.setNamespaceAware(true);
            org.xml.sax.XMLReader reader = f.newSAXParser().getXMLReader();
            reader.setContentHandler(contentHandler);
            reader.parse(new org.xml.sax.InputSource(new StringReader(xml)));
        } catch (IOException | javax.xml.parsers.ParserConfigurationException e) {
            throw new AssertionError(e);
        }
    }

    public static void parse(Reader in, org.xml.sax.ContentHandler contentHandler) throws IOException, org.xml.sax.SAXException {
        try {
            javax.xml.parsers.SAXParserFactory f = javax.xml.parsers.SAXParserFactory.newInstance();
            f.setNamespaceAware(true);
            org.xml.sax.XMLReader reader = f.newSAXParser().getXMLReader();
            reader.setContentHandler(contentHandler);
            reader.parse(new org.xml.sax.InputSource(in));
        } catch (javax.xml.parsers.ParserConfigurationException e) {
            throw new AssertionError(e);
        }
    }

    public static void parse(InputStream in, Encoding encoding, org.xml.sax.ContentHandler contentHandler) throws IOException, org.xml.sax.SAXException {
        parse(new java.io.InputStreamReader(in, encoding.expatName), contentHandler);
    }

    /** The parser itself when it implements AttributeSet (resource parsers do), else an adapter */
    public static AttributeSet asAttributeSet(XmlPullParser parser) {
        return (parser instanceof AttributeSet) ? (AttributeSet) parser : new XmlPullAttributes(parser);
    }

    public static Encoding findEncodingByName(String encodingName) throws java.io.UnsupportedEncodingException {
        if (encodingName == null) return Encoding.UTF_8;
        for (Encoding e : Encoding.values()) if (e.expatName.equalsIgnoreCase(encodingName)) return e;
        throw new java.io.UnsupportedEncodingException(encodingName);
    }

    public static class XmlPullAttributes implements AttributeSet {
        public final XmlPullParser mParser;

        XmlPullAttributes(XmlPullParser parser) {
            mParser = parser;
        }

        @Override
        public int getAttributeCount() {
            return mParser.getAttributeCount();
        }

        @Override
        public String getAttributeNamespace(int index) {
            return mParser.getAttributeNamespace(index);
        }

        @Override
        public String getAttributeName(int index) {
            return mParser.getAttributeName(index);
        }

        @Override
        public String getAttributeValue(int index) {
            return mParser.getAttributeValue(index);
        }

        @Override
        public String getAttributeValue(String namespace, String name) {
            String v = mParser.getAttributeValue(namespace, name);
            if (v == null && namespace != null) {
                // Plain text layouts may use a different prefix, match by local name
                for (int i = 0; i < mParser.getAttributeCount(); i++) {
                    if (name.equals(mParser.getAttributeName(i))) return mParser.getAttributeValue(i);
                }
            }
            return v;
        }

        @Override
        public String getPositionDescription() {
            return mParser.getPositionDescription();
        }
    }
}
