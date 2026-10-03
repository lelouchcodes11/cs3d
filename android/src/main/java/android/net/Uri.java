package android.net;

import android.os.Parcel;
import android.os.Parcelable;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;

/**
 * Immutable URI reference with the semantics of android.net.Uri: components are kept encoded and
 * decoded on access, parsing is lenient and never throws.
 */
public abstract class Uri implements Parcelable, Comparable<Uri> {
    private static final String NOT_CACHED = "NOT CACHED";
    public static final Uri EMPTY = new StringUri("");

    private Uri() {
    }

    public abstract boolean isHierarchical();

    public boolean isOpaque() {
        return !isHierarchical();
    }

    public abstract boolean isRelative();

    public boolean isAbsolute() {
        return !isRelative();
    }

    public abstract String getScheme();

    public abstract String getSchemeSpecificPart();

    public abstract String getEncodedSchemeSpecificPart();

    public abstract String getAuthority();

    public abstract String getEncodedAuthority();

    public abstract String getUserInfo();

    public abstract String getEncodedUserInfo();

    public abstract String getHost();

    public abstract int getPort();

    public abstract String getPath();

    public abstract String getEncodedPath();

    public abstract String getQuery();

    public abstract String getEncodedQuery();

    public abstract String getFragment();

    public abstract String getEncodedFragment();

    public abstract List<String> getPathSegments();

    public abstract String getLastPathSegment();

    public abstract Builder buildUpon();

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof Uri)) return false;
        return toString().equals(o.toString());
    }

    @Override
    public int hashCode() {
        return toString().hashCode();
    }

    @Override
    public int compareTo(Uri other) {
        return toString().compareTo(other.toString());
    }

    @Override
    public abstract String toString();

    public String toSafeString() {
        return toString();
    }

    public Set<String> getQueryParameterNames() {
        if (isOpaque()) throw new UnsupportedOperationException("This isn't a hierarchical URI.");
        String query = getEncodedQuery();
        if (query == null) return Collections.emptySet();
        Set<String> names = new LinkedHashSet<>();
        int start = 0;
        do {
            int next = query.indexOf('&', start);
            int end = (next == -1) ? query.length() : next;
            int separator = query.indexOf('=', start);
            if (separator > end || separator == -1) separator = end;
            String name = query.substring(start, separator);
            names.add(decode(name));
            start = end + 1;
        } while (start < query.length());
        return Collections.unmodifiableSet(names);
    }

    public List<String> getQueryParameters(String key) {
        if (isOpaque()) throw new UnsupportedOperationException("This isn't a hierarchical URI.");
        if (key == null) throw new NullPointerException("key");
        String query = getEncodedQuery();
        if (query == null) return Collections.emptyList();
        String encodedKey = encode(key, null);
        ArrayList<String> values = new ArrayList<>();
        int start = 0;
        do {
            int nextAmpersand = query.indexOf('&', start);
            int end = nextAmpersand != -1 ? nextAmpersand : query.length();
            int separator = query.indexOf('=', start);
            if (separator > end || separator == -1) separator = end;
            if (separator - start == encodedKey.length() && query.regionMatches(start, encodedKey, 0, encodedKey.length())) {
                if (separator == end) values.add("");
                else values.add(decode(query.substring(separator + 1, end)));
            }
            if (nextAmpersand != -1) start = nextAmpersand + 1;
            else break;
        } while (true);
        return Collections.unmodifiableList(values);
    }

    public String getQueryParameter(String key) {
        if (isOpaque()) throw new UnsupportedOperationException("This isn't a hierarchical URI.");
        if (key == null) throw new NullPointerException("key");
        final String query = getEncodedQuery();
        if (query == null) return null;
        final String encodedKey = encode(key, null);
        final int length = query.length();
        int start = 0;
        do {
            int nextAmpersand = query.indexOf('&', start);
            int end = nextAmpersand != -1 ? nextAmpersand : length;
            int separator = query.indexOf('=', start);
            if (separator > end || separator == -1) separator = end;
            if (separator - start == encodedKey.length() && query.regionMatches(start, encodedKey, 0, encodedKey.length())) {
                if (separator == end) return "";
                String encodedValue = query.substring(separator + 1, end);
                return decode(encodedValue, true);
            }
            if (nextAmpersand != -1) start = nextAmpersand + 1;
            else break;
        } while (true);
        return null;
    }

    public boolean getBooleanQueryParameter(String key, boolean defaultValue) {
        String flag = getQueryParameter(key);
        if (flag == null) return defaultValue;
        flag = flag.toLowerCase(Locale.ROOT);
        return (!"false".equals(flag) && !"0".equals(flag));
    }

    public Uri normalizeScheme() {
        String scheme = getScheme();
        if (scheme == null) return this;
        String lowerScheme = scheme.toLowerCase(Locale.ROOT);
        if (scheme.equals(lowerScheme)) return this;
        return buildUpon().scheme(lowerScheme).build();
    }

    public static Uri parse(String uriString) {
        return new StringUri(uriString == null ? "" : uriString);
    }

    public static Uri fromFile(File file) {
        if (file == null) throw new NullPointerException("file");
        String path = file.getAbsolutePath().replace(File.separatorChar, '/');
        if (!path.startsWith("/")) path = "/" + path;
        return new Builder().scheme("file").authority("").path(path).build();
    }

    public static Uri fromParts(String scheme, String ssp, String fragment) {
        if (scheme == null) throw new NullPointerException("scheme");
        if (ssp == null) throw new NullPointerException("ssp");
        StringBuilder sb = new StringBuilder(scheme).append(':').append(encode(ssp, null));
        if (fragment != null) sb.append('#').append(encode(fragment, null));
        return parse(sb.toString());
    }

    public static Uri withAppendedPath(Uri baseUri, String pathSegment) {
        Builder builder = baseUri.buildUpon();
        builder = builder.appendEncodedPath(pathSegment);
        return builder.build();
    }

    // ------------------------------------------------------------------ encoding

    private static final char[] HEX_DIGITS = "0123456789ABCDEF".toCharArray();
    private static final String DEFAULT_ENCODING = "UTF-8";

    public static String encode(String s) {
        return encode(s, null);
    }

    public static String encode(String s, String allow) {
        if (s == null) return null;
        StringBuilder encoded = null;
        int oldLength = s.length();
        int current = 0;
        while (current < oldLength) {
            int nextToEncode = current;
            while (nextToEncode < oldLength && isAllowed(s.charAt(nextToEncode), allow)) nextToEncode++;
            if (nextToEncode == oldLength) {
                if (current == 0) return s;
                encoded.append(s, current, oldLength);
                return encoded.toString();
            }
            if (encoded == null) encoded = new StringBuilder();
            if (nextToEncode > current) encoded.append(s, current, nextToEncode);
            current = nextToEncode;
            int nextAllowed = current + 1;
            while (nextAllowed < oldLength && !isAllowed(s.charAt(nextAllowed), allow)) nextAllowed++;
            String toEncode = s.substring(current, nextAllowed);
            byte[] bytes = toEncode.getBytes(StandardCharsets.UTF_8);
            for (byte b : bytes) {
                encoded.append('%');
                encoded.append(HEX_DIGITS[(b & 0xf0) >> 4]);
                encoded.append(HEX_DIGITS[b & 0xf]);
            }
            current = nextAllowed;
        }
        return encoded == null ? s : encoded.toString();
    }

    private static boolean isAllowed(char c, String allow) {
        return (c >= 'A' && c <= 'Z') || (c >= 'a' && c <= 'z') || (c >= '0' && c <= '9')
                || "_-!.~'()*".indexOf(c) != -1 || (allow != null && allow.indexOf(c) != -1);
    }

    public static String decode(String s) {
        return decode(s, false);
    }

    static String decode(String s, boolean convertPlus) {
        if (s == null) return null;
        if (s.indexOf('%') < 0 && (!convertPlus || s.indexOf('+') < 0)) return s;
        StringBuilder out = new StringBuilder(s.length());
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        for (int i = 0; i < s.length(); ) {
            char c = s.charAt(i);
            if (c == '%') {
                bytes.reset();
                while (i < s.length() && s.charAt(i) == '%') {
                    if (i + 2 >= s.length()) break;
                    int hi = Character.digit(s.charAt(i + 1), 16);
                    int lo = Character.digit(s.charAt(i + 2), 16);
                    if (hi < 0 || lo < 0) break;
                    bytes.write((hi << 4) | lo);
                    i += 3;
                }
                if (bytes.size() > 0) {
                    out.append(new String(bytes.toByteArray(), StandardCharsets.UTF_8));
                } else {
                    // invalid escape, keep it as is (Android replaces with U+FFFD, keeping is friendlier)
                    out.append(c);
                    i++;
                }
            } else if (convertPlus && c == '+') {
                out.append(' ');
                i++;
            } else {
                out.append(c);
                i++;
            }
        }
        return out.toString();
    }

    // ------------------------------------------------------------------ implementation

    private static final class StringUri extends Uri {
        private final String uriString;
        private int cachedSsi = -2;
        private int cachedFsi = -2;

        StringUri(String uriString) {
            this.uriString = uriString;
        }

        private int findSchemeSeparator() {
            if (cachedSsi == -2) {
                int ssi = uriString.indexOf(':');
                int slash = uriString.indexOf('/');
                int q = uriString.indexOf('?');
                int hash = uriString.indexOf('#');
                if (ssi == -1 || (slash != -1 && slash < ssi) || (q != -1 && q < ssi) || (hash != -1 && hash < ssi)) ssi = -1;
                cachedSsi = ssi;
            }
            return cachedSsi;
        }

        private int findFragmentSeparator() {
            if (cachedFsi == -2) cachedFsi = uriString.indexOf('#', Math.max(0, findSchemeSeparator()));
            return cachedFsi;
        }

        @Override
        public boolean isHierarchical() {
            int ssi = findSchemeSeparator();
            if (ssi == -1) return true;
            if (uriString.length() == ssi + 1) return false;
            return uriString.charAt(ssi + 1) == '/';
        }

        @Override
        public boolean isRelative() {
            return findSchemeSeparator() == -1;
        }

        @Override
        public String getScheme() {
            int ssi = findSchemeSeparator();
            return ssi == -1 ? null : uriString.substring(0, ssi);
        }

        @Override
        public String getEncodedSchemeSpecificPart() {
            int ssi = findSchemeSeparator();
            int fsi = findFragmentSeparator();
            return fsi == -1 ? uriString.substring(ssi + 1) : uriString.substring(ssi + 1, fsi);
        }

        @Override
        public String getSchemeSpecificPart() {
            return decode(getEncodedSchemeSpecificPart());
        }

        @Override
        public String getEncodedAuthority() {
            if (!isHierarchical()) return null;
            String ssp = getEncodedSchemeSpecificPart();
            if (ssp.length() > 1 && ssp.charAt(0) == '/' && ssp.charAt(1) == '/') {
                int end = 2;
                while (end < ssp.length()) {
                    char c = ssp.charAt(end);
                    if (c == '/' || c == '\\' || c == '?' || c == '#') break;
                    end++;
                }
                return ssp.substring(2, end);
            }
            return null;
        }

        @Override
        public String getAuthority() {
            return decode(getEncodedAuthority());
        }

        @Override
        public String getEncodedUserInfo() {
            String authority = getEncodedAuthority();
            if (authority == null) return null;
            int end = authority.lastIndexOf('@');
            return end == -1 ? null : authority.substring(0, end);
        }

        @Override
        public String getUserInfo() {
            return decode(getEncodedUserInfo());
        }

        @Override
        public String getHost() {
            String authority = getEncodedAuthority();
            if (authority == null) return null;
            int userInfoSeparator = authority.lastIndexOf('@');
            int portSeparator = findPortSeparator(authority);
            String encodedHost = portSeparator == -1
                    ? authority.substring(userInfoSeparator + 1)
                    : authority.substring(userInfoSeparator + 1, portSeparator);
            return decode(encodedHost);
        }

        private static int findPortSeparator(String authority) {
            int portSeparator = authority.lastIndexOf(':');
            int lastBracket = authority.lastIndexOf(']');
            if (portSeparator < lastBracket) return -1;
            if (portSeparator != -1) {
                for (int i = portSeparator + 1; i < authority.length(); i++) {
                    if (!Character.isDigit(authority.charAt(i))) return -1;
                }
            }
            return portSeparator;
        }

        @Override
        public int getPort() {
            String authority = getEncodedAuthority();
            if (authority == null) return -1;
            int portSeparator = findPortSeparator(authority);
            if (portSeparator == -1) return -1;
            String portString = decode(authority.substring(portSeparator + 1));
            try {
                return Integer.parseInt(portString);
            } catch (NumberFormatException e) {
                return -1;
            }
        }

        @Override
        public String getEncodedPath() {
            if (!isHierarchical()) return null;
            int ssi = findSchemeSeparator();
            int pathStart;
            if (ssi > -1) {
                pathStart = ssi + 1;
            } else {
                pathStart = 0;
            }
            int length = uriString.length();
            if (pathStart + 1 < length && uriString.charAt(pathStart) == '/' && uriString.charAt(pathStart + 1) == '/') {
                pathStart += 2;
                while (pathStart < length) {
                    char c = uriString.charAt(pathStart);
                    if (c == '?' || c == '#') return "";
                    if (c == '/' || c == '\\') break;
                    pathStart++;
                }
            }
            int pathEnd = pathStart;
            while (pathEnd < length) {
                char c = uriString.charAt(pathEnd);
                if (c == '?' || c == '#') break;
                pathEnd++;
            }
            return uriString.substring(pathStart, pathEnd);
        }

        @Override
        public String getPath() {
            return decode(getEncodedPath());
        }

        @Override
        public String getEncodedQuery() {
            int qsi = uriString.indexOf('?', Math.max(0, findSchemeSeparator()));
            if (qsi == -1) return null;
            int fsi = findFragmentSeparator();
            if (fsi == -1) return uriString.substring(qsi + 1);
            if (fsi < qsi) return null;
            return uriString.substring(qsi + 1, fsi);
        }

        @Override
        public String getQuery() {
            return decode(getEncodedQuery());
        }

        @Override
        public String getEncodedFragment() {
            int fsi = findFragmentSeparator();
            return fsi == -1 ? null : uriString.substring(fsi + 1);
        }

        @Override
        public String getFragment() {
            return decode(getEncodedFragment());
        }

        @Override
        public List<String> getPathSegments() {
            String path = getEncodedPath();
            if (path == null) return Collections.emptyList();
            List<String> segments = new ArrayList<>();
            for (String s : path.split("/")) {
                if (!s.isEmpty()) segments.add(decode(s));
            }
            return Collections.unmodifiableList(segments);
        }

        @Override
        public String getLastPathSegment() {
            List<String> segments = getPathSegments();
            return segments.isEmpty() ? null : segments.get(segments.size() - 1);
        }

        @Override
        public Builder buildUpon() {
            if (isHierarchical()) {
                return new Builder().scheme(getScheme()).encodedAuthority(getEncodedAuthority())
                        .encodedPath(getEncodedPath()).encodedQuery(getEncodedQuery()).encodedFragment(getEncodedFragment());
            }
            return new Builder().scheme(getScheme()).encodedOpaquePart(getEncodedSchemeSpecificPart()).encodedFragment(getEncodedFragment());
        }

        @Override
        public String toString() {
            return uriString;
        }
    }

    public static final class Builder {
        private String scheme;
        private String opaquePart;
        private String authority;
        private StringBuilder path;
        private String query;
        private String fragment;

        public Builder() {
        }

        public Builder scheme(String scheme) {
            this.scheme = scheme;
            return this;
        }

        public Builder opaquePart(String opaquePart) {
            this.opaquePart = encode(opaquePart, null);
            return this;
        }

        public Builder encodedOpaquePart(String opaquePart) {
            this.opaquePart = opaquePart;
            return this;
        }

        public Builder authority(String authority) {
            this.opaquePart = null;
            this.authority = authority == null ? null : encode(authority, "@:[]");
            return this;
        }

        public Builder encodedAuthority(String authority) {
            this.opaquePart = null;
            this.authority = authority;
            return this;
        }

        public Builder path(String path) {
            this.opaquePart = null;
            this.path = path == null ? null : new StringBuilder(encode(path, "/"));
            return this;
        }

        public Builder encodedPath(String path) {
            this.opaquePart = null;
            this.path = path == null ? null : new StringBuilder(path);
            return this;
        }

        public Builder appendPath(String newSegment) {
            return appendEncodedPath(encode(newSegment, null));
        }

        public Builder appendEncodedPath(String newSegment) {
            this.opaquePart = null;
            if (path == null) path = new StringBuilder();
            if (newSegment == null || newSegment.isEmpty()) return this;
            boolean endsWithSlash = path.length() > 0 && path.charAt(path.length() - 1) == '/';
            boolean startsWithSlash = newSegment.charAt(0) == '/';
            if (endsWithSlash && startsWithSlash) path.append(newSegment, 1, newSegment.length());
            else if (!endsWithSlash && !startsWithSlash) path.append('/').append(newSegment);
            else path.append(newSegment);
            return this;
        }

        public Builder query(String query) {
            this.opaquePart = null;
            this.query = query == null ? null : encode(query, "=&");
            return this;
        }

        public Builder encodedQuery(String query) {
            this.opaquePart = null;
            this.query = query;
            return this;
        }

        public Builder fragment(String fragment) {
            this.fragment = fragment == null ? null : encode(fragment, null);
            return this;
        }

        public Builder encodedFragment(String fragment) {
            this.fragment = fragment;
            return this;
        }

        public Builder appendQueryParameter(String key, String value) {
            this.opaquePart = null;
            String encodedParameter = encode(key, null) + "=" + encode(value, null);
            if (query == null || query.isEmpty()) query = encodedParameter;
            else query = query + "&" + encodedParameter;
            return this;
        }

        public Builder clearQuery() {
            this.query = null;
            return this;
        }

        public Uri build() {
            StringBuilder sb = new StringBuilder();
            if (scheme != null) sb.append(scheme).append(':');
            if (opaquePart != null) {
                sb.append(opaquePart);
            } else {
                if (authority != null) sb.append("//").append(authority);
                if (path != null && path.length() > 0) {
                    if (authority != null && path.charAt(0) != '/') sb.append('/');
                    sb.append(path);
                }
                if (query != null) sb.append('?').append(query);
            }
            if (fragment != null) sb.append('#').append(fragment);
            return new StringUri(sb.toString());
        }

        @Override
        public String toString() {
            return build().toString();
        }
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel out, int flags) {
        out.writeString(toString());
    }

    public static final Parcelable.Creator<Uri> CREATOR = new Parcelable.Creator<Uri>() {
        public Uri createFromParcel(Parcel in) {
            return parse(in.readString());
        }

        public Uri[] newArray(int size) {
            return new Uri[size];
        }
    };

    static boolean equalsHelper(Object a, Object b) {
        return Objects.equals(a, b);
    }
}
