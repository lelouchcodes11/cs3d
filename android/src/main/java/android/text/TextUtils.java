package android.text;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

public class TextUtils {
    public enum TruncateAt {START, MIDDLE, END, MARQUEE, END_SMALL}

    public static final int CAP_MODE_CHARACTERS = InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS;
    public static final int CAP_MODE_WORDS = InputType.TYPE_TEXT_FLAG_CAP_WORDS;
    public static final int CAP_MODE_SENTENCES = InputType.TYPE_TEXT_FLAG_CAP_SENTENCES;

    private TextUtils() {
    }

    public static boolean isEmpty(CharSequence str) {
        return str == null || str.length() == 0;
    }

    public static boolean isDigitsOnly(CharSequence str) {
        final int len = str.length();
        for (int cp, i = 0; i < len; i += Character.charCount(cp)) {
            cp = Character.codePointAt(str, i);
            if (!Character.isDigit(cp)) return false;
        }
        return true;
    }

    public static boolean isGraphic(CharSequence str) {
        for (int i = 0; i < str.length(); i++) {
            if (!Character.isWhitespace(str.charAt(i)) && !Character.isISOControl(str.charAt(i))) return true;
        }
        return false;
    }

    public static boolean equals(CharSequence a, CharSequence b) {
        if (a == b) return true;
        int length;
        if (a != null && b != null && (length = a.length()) == b.length()) {
            if (a instanceof String && b instanceof String) return a.equals(b);
            for (int i = 0; i < length; i++) if (a.charAt(i) != b.charAt(i)) return false;
            return true;
        }
        return false;
    }

    public static String join(CharSequence delimiter, Object[] tokens) {
        StringBuilder sb = new StringBuilder();
        boolean first = true;
        for (Object token : tokens) {
            if (!first) sb.append(delimiter);
            first = false;
            sb.append(token);
        }
        return sb.toString();
    }

    public static String join(CharSequence delimiter, Iterable tokens) {
        StringBuilder sb = new StringBuilder();
        Iterator<?> it = tokens.iterator();
        if (it.hasNext()) {
            sb.append(it.next());
            while (it.hasNext()) {
                sb.append(delimiter);
                sb.append(it.next());
            }
        }
        return sb.toString();
    }

    public static String[] split(String text, String expression) {
        if (text.length() == 0) return new String[0];
        return text.split(expression, -1);
    }

    public static String[] split(String text, Pattern pattern) {
        if (text.length() == 0) return new String[0];
        return pattern.split(text, -1);
    }

    public static CharSequence concat(CharSequence... text) {
        SpannableStringBuilder sb = new SpannableStringBuilder();
        for (CharSequence t : text) sb.append(t);
        return sb;
    }

    public static int getTrimmedLength(CharSequence s) {
        int len = s.length();
        int start = 0;
        while (start < len && s.charAt(start) <= ' ') start++;
        int end = len;
        while (end > start && s.charAt(end - 1) <= ' ') end--;
        return end - start;
    }

    public static CharSequence ellipsize(CharSequence text, TextPaint p, float avail, TruncateAt where) {
        if (p == null || p.measureText(text.toString()) <= avail) return text;
        String s = text.toString();
        int n = s.length();
        while (n > 0 && p.measureText(s.substring(0, n) + "…") > avail) n--;
        return s.substring(0, n) + "…";
    }

    public static String htmlEncode(String s) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '<': sb.append("&lt;"); break;
                case '>': sb.append("&gt;"); break;
                case '&': sb.append("&amp;"); break;
                case '\'': sb.append("&#39;"); break;
                case '"': sb.append("&quot;"); break;
                default: sb.append(c);
            }
        }
        return sb.toString();
    }

    public static int indexOf(CharSequence s, char ch) {
        return s.toString().indexOf(ch);
    }

    public static int indexOf(CharSequence s, char ch, int start) {
        return s.toString().indexOf(ch, start);
    }

    public static int indexOf(CharSequence s, CharSequence needle) {
        return s.toString().indexOf(needle.toString());
    }

    public static int lastIndexOf(CharSequence s, char ch) {
        return s.toString().lastIndexOf(ch);
    }

    public static String substring(CharSequence source, int start, int end) {
        return source.toString().substring(start, end);
    }

    public static CharSequence stringOrSpannedString(CharSequence source) {
        if (source == null) return null;
        if (source instanceof SpannedString) return source;
        if (source instanceof Spanned) return new SpannedString(source);
        return source.toString();
    }

    public static int getLayoutDirectionFromLocale(Locale locale) {
        return 0;
    }

    public static int getCapsMode(CharSequence cs, int off, int reqModes) {
        return 0;
    }

    public static void copySpansFrom(Spanned source, int start, int end, Class kind, Spannable dest, int destoff) {
        if (kind == null) kind = Object.class;
        Object[] spans = source.getSpans(start, end, kind);
        for (Object span : spans) {
            int st = Math.max(source.getSpanStart(span), start);
            int en = Math.min(source.getSpanEnd(span), end);
            dest.setSpan(span, st - start + destoff, en - start + destoff, source.getSpanFlags(span));
        }
    }

    public interface StringSplitter extends Iterable<String> {
        void setString(String string);
    }

    public static class SimpleStringSplitter implements StringSplitter, Iterator<String> {
        private String mString;
        private int mPosition;
        private int mLength;
        private final char mDelimiter;

        public SimpleStringSplitter(char delimiter) {
            mDelimiter = delimiter;
        }

        public void setString(String string) {
            mString = string;
            mPosition = 0;
            mLength = mString.length();
        }

        public Iterator<String> iterator() {
            return this;
        }

        public boolean hasNext() {
            return mPosition < mLength;
        }

        public String next() {
            int end = mString.indexOf(mDelimiter, mPosition);
            if (end == -1) end = mLength;
            String nextString = mString.substring(mPosition, end);
            mPosition = end + 1;
            return nextString;
        }
    }

    public static List<String> splitToList(String text, String regex) {
        List<String> l = new ArrayList<>();
        for (String s : split(text, regex)) l.add(s);
        return l;
    }
}
