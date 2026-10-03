package android.text;

import java.util.ArrayList;
import java.util.List;

public class SpannableStringBuilder implements CharSequence, GetChars, Spannable, Editable, Appendable {
    private final StringBuilder mText = new StringBuilder();
    private final SpanSet mSpans = new SpanSet();
    private InputFilter[] mFilters = new InputFilter[0];
    private final List<Runnable> mChangeListeners = new ArrayList<>();

    public SpannableStringBuilder() {
        this("");
    }

    public SpannableStringBuilder(CharSequence text) {
        this(text, 0, text == null ? 0 : text.length());
    }

    public SpannableStringBuilder(CharSequence text, int start, int end) {
        if (text != null) {
            mText.append(text, start, end);
            if (text instanceof Spanned) mSpans.copyFrom((Spanned) text, start, end, 0);
        }
    }

    public static SpannableStringBuilder valueOf(CharSequence source) {
        if (source instanceof SpannableStringBuilder) return (SpannableStringBuilder) source;
        return new SpannableStringBuilder(source);
    }

    /** Desktop: observe content changes (used by the TextView renderer) */
    public void addChangeListener(Runnable r) {
        mChangeListeners.add(r);
    }

    public void removeChangeListener(Runnable r) {
        mChangeListeners.remove(r);
    }

    private void changed() {
        for (Runnable r : new ArrayList<>(mChangeListeners)) r.run();
    }

    @Override
    public char charAt(int where) {
        return mText.charAt(where);
    }

    @Override
    public int length() {
        return mText.length();
    }

    @Override
    public SpannableStringBuilder insert(int where, CharSequence tb, int start, int end) {
        return replace(where, where, tb, start, end);
    }

    @Override
    public SpannableStringBuilder insert(int where, CharSequence tb) {
        return replace(where, where, tb, 0, tb.length());
    }

    @Override
    public SpannableStringBuilder delete(int start, int end) {
        return replace(start, end, "", 0, 0);
    }

    @Override
    public void clear() {
        replace(0, length(), "", 0, 0);
    }

    @Override
    public void clearSpans() {
        mSpans.entries.clear();
        changed();
    }

    @Override
    public SpannableStringBuilder append(CharSequence text) {
        if (text == null) text = "null";
        return replace(length(), length(), text, 0, text.length());
    }

    public SpannableStringBuilder append(CharSequence text, Object what, int flags) {
        int start = length();
        append(text);
        setSpan(what, start, length(), flags);
        return this;
    }

    @Override
    public SpannableStringBuilder append(CharSequence text, int start, int end) {
        if (text == null) text = "null";
        return replace(length(), length(), text, start, end);
    }

    @Override
    public SpannableStringBuilder append(char text) {
        return append(String.valueOf(text));
    }

    @Override
    public SpannableStringBuilder replace(int start, int end, CharSequence tb) {
        return replace(start, end, tb, 0, tb.length());
    }

    @Override
    public SpannableStringBuilder replace(int start, int end, CharSequence tb, int tbstart, int tbend) {
        for (InputFilter filter : mFilters) {
            CharSequence repl = filter.filter(tb, tbstart, tbend, this, start, end);
            if (repl != null) {
                tb = repl;
                tbstart = 0;
                tbend = repl.length();
            }
        }
        int newLen = tbend - tbstart;
        mText.replace(start, end, tb.subSequence(tbstart, tbend).toString());
        mSpans.replace(start, end, newLen);
        if (tb instanceof Spanned) mSpans.copyFrom((Spanned) tb, tbstart, tbend, start);
        changed();
        return this;
    }

    @Override
    public void setSpan(Object what, int start, int end, int flags) {
        if (start > end || end > length() || start < 0) {
            throw new IndexOutOfBoundsException("setSpan (" + start + " ... " + end + ") ends beyond length " + length());
        }
        mSpans.set(what, start, end, flags);
        changed();
    }

    @Override
    public void removeSpan(Object what) {
        mSpans.remove(what);
        changed();
    }

    @Override
    public int getSpanStart(Object what) {
        return mSpans.start(what);
    }

    @Override
    public int getSpanEnd(Object what) {
        return mSpans.end(what);
    }

    @Override
    public int getSpanFlags(Object what) {
        return mSpans.flags(what);
    }

    @Override
    public <T> T[] getSpans(int queryStart, int queryEnd, Class<T> kind) {
        return mSpans.get(queryStart, queryEnd, kind);
    }

    @Override
    public int nextSpanTransition(int start, int limit, Class type) {
        return mSpans.nextTransition(start, limit, type);
    }

    @Override
    public CharSequence subSequence(int start, int end) {
        return new SpannableStringBuilder(this, start, end);
    }

    @Override
    public void getChars(int start, int end, char[] dest, int destoff) {
        mText.getChars(start, end, dest, destoff);
    }

    @Override
    public String toString() {
        return mText.toString();
    }

    @Override
    public void setFilters(InputFilter[] filters) {
        mFilters = filters == null ? new InputFilter[0] : filters;
    }

    @Override
    public InputFilter[] getFilters() {
        return mFilters;
    }

    @Override
    public boolean equals(Object o) {
        if (o instanceof Spanned && toString().equals(o.toString())) {
            return true;
        }
        return false;
    }

    @Override
    public int hashCode() {
        return toString().hashCode();
    }
}
