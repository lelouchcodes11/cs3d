package android.text;

public final class SpannedString implements Spanned, GetChars {
    private final SpannableString mInner;

    public SpannedString(CharSequence source) {
        mInner = new SpannableString(source);
    }

    public static SpannedString valueOf(CharSequence source) {
        if (source instanceof SpannedString) return (SpannedString) source;
        return new SpannedString(source);
    }

    @Override public int length() { return mInner.length(); }
    @Override public char charAt(int i) { return mInner.charAt(i); }
    @Override public String toString() { return mInner.toString(); }
    @Override public void getChars(int start, int end, char[] dest, int off) { mInner.getChars(start, end, dest, off); }
    @Override public CharSequence subSequence(int start, int end) { return new SpannedString(mInner.subSequence(start, end)); }
    @Override public <T> T[] getSpans(int start, int end, Class<T> type) { return mInner.getSpans(start, end, type); }
    @Override public int getSpanStart(Object what) { return mInner.getSpanStart(what); }
    @Override public int getSpanEnd(Object what) { return mInner.getSpanEnd(what); }
    @Override public int getSpanFlags(Object what) { return mInner.getSpanFlags(what); }
    @Override public int nextSpanTransition(int start, int limit, Class type) { return mInner.nextSpanTransition(start, limit, type); }
}
