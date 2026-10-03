package android.text;

public interface InputFilter {
    CharSequence filter(CharSequence source, int start, int end, Spanned dest, int dstart, int dend);

    class AllCaps implements InputFilter {
        public AllCaps() {
        }

        @Override
        public CharSequence filter(CharSequence source, int start, int end, Spanned dest, int dstart, int dend) {
            String s = source.subSequence(start, end).toString();
            String upper = s.toUpperCase();
            return upper.equals(s) ? null : upper;
        }
    }

    class LengthFilter implements InputFilter {
        private final int mMax;

        public LengthFilter(int max) {
            mMax = max;
        }

        @Override
        public CharSequence filter(CharSequence source, int start, int end, Spanned dest, int dstart, int dend) {
            int keep = mMax - (dest.length() - (dend - dstart));
            if (keep <= 0) return "";
            if (keep >= end - start) return null;
            return source.subSequence(start, start + keep);
        }

        public int getMax() {
            return mMax;
        }
    }
}
