package android.text;

import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.List;

/** Span bookkeeping shared by SpannableString and SpannableStringBuilder */
final class SpanSet {
    static final class Entry {
        final Object span;
        int start;
        int end;
        int flags;

        Entry(Object span, int start, int end, int flags) {
            this.span = span;
            this.start = start;
            this.end = end;
            this.flags = flags;
        }
    }

    final List<Entry> entries = new ArrayList<>();

    Entry find(Object span) {
        for (Entry e : entries) if (e.span == span) return e;
        return null;
    }

    void set(Object what, int start, int end, int flags) {
        Entry e = find(what);
        if (e != null) {
            e.start = start;
            e.end = end;
            e.flags = flags;
        } else {
            entries.add(new Entry(what, start, end, flags));
        }
    }

    void remove(Object what) {
        entries.removeIf(e -> e.span == what);
    }

    @SuppressWarnings("unchecked")
    <T> T[] get(int queryStart, int queryEnd, Class<T> kind) {
        List<T> out = new ArrayList<>();
        for (Entry e : entries) {
            if (kind != null && !kind.isInstance(e.span)) continue;
            if (e.start > queryEnd || e.end < queryStart) continue;
            if (e.start != e.end && queryStart != queryEnd) {
                if (e.start == queryEnd || e.end == queryStart) continue;
            }
            out.add((T) e.span);
        }
        T[] arr = (T[]) Array.newInstance(kind == null ? Object.class : kind, out.size());
        return out.toArray(arr);
    }

    int start(Object tag) {
        Entry e = find(tag);
        return e == null ? -1 : e.start;
    }

    int end(Object tag) {
        Entry e = find(tag);
        return e == null ? -1 : e.end;
    }

    int flags(Object tag) {
        Entry e = find(tag);
        return e == null ? 0 : e.flags;
    }

    int nextTransition(int start, int limit, Class kind) {
        for (Entry e : entries) {
            if (kind != null && !kind.isInstance(e.span)) continue;
            if (e.start > start && e.start < limit) limit = e.start;
            if (e.end > start && e.end < limit) limit = e.end;
        }
        return limit;
    }

    /**
     * Update span positions for a replacement of [start, end) by newLen characters.
     * The high nibble of the flags describes the span start and the low nibble the span end:
     * MARK (1) stays in place when text is inserted at it, POINT (2) moves after the inserted text.
     */
    void replace(int start, int end, int newLen) {
        int delta = newLen - (end - start);
        List<Entry> dead = new ArrayList<>();
        for (Entry e : entries) {
            boolean startIsPoint = ((e.flags & 0xF0) >> 4) == 2;
            boolean endIsPoint = (e.flags & 0x0F) == 2;
            e.start = move(e.start, start, end, newLen, delta, startIsPoint);
            e.end = move(e.end, start, end, newLen, delta, endIsPoint);
            if (e.end < e.start) e.end = e.start;
            if (e.start == e.end && end > start
                    && (e.flags & Spanned.SPAN_POINT_MARK_MASK) == Spanned.SPAN_EXCLUSIVE_EXCLUSIVE) {
                dead.add(e);
            }
        }
        entries.removeAll(dead);
    }

    private static int move(int p, int start, int end, int newLen, int delta, boolean isPoint) {
        if (p < start) return p;
        if (p > end) return p + delta;
        if (start == end) return isPoint ? p + newLen : p;
        if (p == end) return end + delta;
        if (p == start) return start;
        return isPoint ? start + newLen : start;
    }

    void copyFrom(Spanned src, int srcStart, int srcEnd, int dstOffset) {
        Object[] spans = src.getSpans(srcStart, srcEnd, Object.class);
        for (Object span : spans) {
            int st = Math.max(srcStart, src.getSpanStart(span));
            int en = Math.min(srcEnd, src.getSpanEnd(span));
            entries.add(new Entry(span, st - srcStart + dstOffset, en - srcStart + dstOffset, src.getSpanFlags(span)));
        }
    }
}
