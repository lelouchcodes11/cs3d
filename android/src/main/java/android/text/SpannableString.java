package android.text;

public class SpannableString implements Spannable, GetChars {
    private final String mText;
    private final SpanSet mSpans = new SpanSet();

    public SpannableString(CharSequence source) {
        mText = source == null ? "" : source.toString();
        if (source instanceof Spanned) mSpans.copyFrom((Spanned) source, 0, mText.length(), 0);
    }

    private SpannableString(CharSequence source, int start, int end) {
        mText = source.subSequence(start, end).toString();
        if (source instanceof Spanned) mSpans.copyFrom((Spanned) source, start, end, 0);
    }

    public static SpannableString valueOf(CharSequence source) {
        if (source instanceof SpannableString) return (SpannableString) source;
        return new SpannableString(source);
    }

    @Override public int length() { return mText.length(); }
    @Override public char charAt(int i) { return mText.charAt(i); }
    @Override public String toString() { return mText; }
    @Override public void getChars(int start, int end, char[] dest, int off) { mText.getChars(start, end, dest, off); }
    @Override public CharSequence subSequence(int start, int end) { return new SpannableString(this, start, end); }
    @Override public void setSpan(Object what, int start, int end, int flags) { mSpans.set(what, start, end, flags); }
    @Override public void removeSpan(Object what) { mSpans.remove(what); }
    @Override public <T> T[] getSpans(int start, int end, Class<T> type) { return mSpans.get(start, end, type); }
    @Override public int getSpanStart(Object what) { return mSpans.start(what); }
    @Override public int getSpanEnd(Object what) { return mSpans.end(what); }
    @Override public int getSpanFlags(Object what) { return mSpans.flags(what); }
    @Override public int nextSpanTransition(int start, int limit, Class type) { return mSpans.nextTransition(start, limit, type); }

    @Override
    public boolean equals(Object o) {
        return o instanceof Spanned && toString().equals(o.toString());
    }

    @Override
    public int hashCode() {
        return mText.hashCode();
    }
}
