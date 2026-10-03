package android.graphics;

/**
 * 3x3 affine/perspective matrix with Android semantics (pre/post concatenation, row-major values).
 */
public class Matrix {
    public static final int MSCALE_X = 0;
    public static final int MSKEW_X = 1;
    public static final int MTRANS_X = 2;
    public static final int MSKEW_Y = 3;
    public static final int MSCALE_Y = 4;
    public static final int MTRANS_Y = 5;
    public static final int MPERSP_0 = 6;
    public static final int MPERSP_1 = 7;
    public static final int MPERSP_2 = 8;

    public enum ScaleToFit {FILL, START, CENTER, END}

    final float[] m = new float[9];

    public Matrix() {
        reset();
    }

    public Matrix(Matrix src) {
        set(src);
    }

    public boolean isIdentity() {
        return m[0] == 1 && m[1] == 0 && m[2] == 0 && m[3] == 0 && m[4] == 1 && m[5] == 0 && m[6] == 0 && m[7] == 0 && m[8] == 1;
    }

    public void set(Matrix src) {
        if (src == null) reset();
        else System.arraycopy(src.m, 0, m, 0, 9);
    }

    public void reset() {
        for (int i = 0; i < 9; i++) m[i] = 0;
        m[0] = m[4] = m[8] = 1;
    }

    public void getValues(float[] values) {
        System.arraycopy(m, 0, values, 0, 9);
    }

    public void setValues(float[] values) {
        System.arraycopy(values, 0, m, 0, 9);
    }

    /** Values for Skia (org.jetbrains.skia.Matrix33 uses the same row-major order) */
    public float[] values() {
        return m.clone();
    }

    private static float[] mul(float[] a, float[] b) {
        float[] r = new float[9];
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 3; col++) {
                r[row * 3 + col] = a[row * 3] * b[col] + a[row * 3 + 1] * b[3 + col] + a[row * 3 + 2] * b[6 + col];
            }
        }
        return r;
    }

    private void pre(float[] other) {
        float[] r = mul(m, other);
        System.arraycopy(r, 0, m, 0, 9);
    }

    private void post(float[] other) {
        float[] r = mul(other, m);
        System.arraycopy(r, 0, m, 0, 9);
    }

    private static float[] translate(float dx, float dy) {
        return new float[]{1, 0, dx, 0, 1, dy, 0, 0, 1};
    }

    private static float[] scale(float sx, float sy, float px, float py) {
        return new float[]{sx, 0, px - sx * px, 0, sy, py - sy * py, 0, 0, 1};
    }

    private static float[] rotate(float degrees, float px, float py) {
        double rad = Math.toRadians(degrees);
        float c = (float) Math.cos(rad), s = (float) Math.sin(rad);
        return new float[]{c, -s, px - c * px + s * py, s, c, py - s * px - c * py, 0, 0, 1};
    }

    private static float[] skew(float kx, float ky, float px, float py) {
        return new float[]{1, kx, -kx * py, ky, 1, -ky * px, 0, 0, 1};
    }

    public void setTranslate(float dx, float dy) { System.arraycopy(translate(dx, dy), 0, m, 0, 9); }
    public void setScale(float sx, float sy, float px, float py) { System.arraycopy(scale(sx, sy, px, py), 0, m, 0, 9); }
    public void setScale(float sx, float sy) { setScale(sx, sy, 0, 0); }
    public void setRotate(float degrees, float px, float py) { System.arraycopy(rotate(degrees, px, py), 0, m, 0, 9); }
    public void setRotate(float degrees) { setRotate(degrees, 0, 0); }
    public void setSkew(float kx, float ky, float px, float py) { System.arraycopy(skew(kx, ky, px, py), 0, m, 0, 9); }
    public void setSkew(float kx, float ky) { setSkew(kx, ky, 0, 0); }

    public boolean setConcat(Matrix a, Matrix b) {
        float[] r = mul(a.m, b.m);
        System.arraycopy(r, 0, m, 0, 9);
        return true;
    }

    public boolean preTranslate(float dx, float dy) { pre(translate(dx, dy)); return true; }
    public boolean preScale(float sx, float sy, float px, float py) { pre(scale(sx, sy, px, py)); return true; }
    public boolean preScale(float sx, float sy) { return preScale(sx, sy, 0, 0); }
    public boolean preRotate(float degrees, float px, float py) { pre(rotate(degrees, px, py)); return true; }
    public boolean preRotate(float degrees) { return preRotate(degrees, 0, 0); }
    public boolean preSkew(float kx, float ky) { pre(skew(kx, ky, 0, 0)); return true; }
    public boolean preConcat(Matrix other) { pre(other.m); return true; }
    public boolean postTranslate(float dx, float dy) { post(translate(dx, dy)); return true; }
    public boolean postScale(float sx, float sy, float px, float py) { post(scale(sx, sy, px, py)); return true; }
    public boolean postScale(float sx, float sy) { return postScale(sx, sy, 0, 0); }
    public boolean postRotate(float degrees, float px, float py) { post(rotate(degrees, px, py)); return true; }
    public boolean postRotate(float degrees) { return postRotate(degrees, 0, 0); }
    public boolean postSkew(float kx, float ky) { post(skew(kx, ky, 0, 0)); return true; }
    public boolean postConcat(Matrix other) { post(other.m); return true; }

    public boolean invert(Matrix inverse) {
        float[] a = m;
        float det = a[0] * (a[4] * a[8] - a[5] * a[7]) - a[1] * (a[3] * a[8] - a[5] * a[6]) + a[2] * (a[3] * a[7] - a[4] * a[6]);
        if (det == 0) return false;
        float invDet = 1f / det;
        float[] r = new float[]{
                (a[4] * a[8] - a[5] * a[7]) * invDet, (a[2] * a[7] - a[1] * a[8]) * invDet, (a[1] * a[5] - a[2] * a[4]) * invDet,
                (a[5] * a[6] - a[3] * a[8]) * invDet, (a[0] * a[8] - a[2] * a[6]) * invDet, (a[2] * a[3] - a[0] * a[5]) * invDet,
                (a[3] * a[7] - a[4] * a[6]) * invDet, (a[1] * a[6] - a[0] * a[7]) * invDet, (a[0] * a[4] - a[1] * a[3]) * invDet
        };
        if (inverse != null) System.arraycopy(r, 0, inverse.m, 0, 9);
        return true;
    }

    public void mapPoints(float[] dst, int dstIndex, float[] src, int srcIndex, int pointCount) {
        for (int i = 0; i < pointCount; i++) {
            float x = src[srcIndex + i * 2], y = src[srcIndex + i * 2 + 1];
            float w = m[6] * x + m[7] * y + m[8];
            if (w == 0) w = 1;
            dst[dstIndex + i * 2] = (m[0] * x + m[1] * y + m[2]) / w;
            dst[dstIndex + i * 2 + 1] = (m[3] * x + m[4] * y + m[5]) / w;
        }
    }

    public void mapPoints(float[] dst, float[] src) {
        mapPoints(dst, 0, src, 0, dst.length >> 1);
    }

    public void mapPoints(float[] pts) {
        mapPoints(pts, 0, pts, 0, pts.length >> 1);
    }

    public boolean mapRect(RectF dst, RectF src) {
        float[] pts = new float[]{src.left, src.top, src.right, src.top, src.right, src.bottom, src.left, src.bottom};
        mapPoints(pts);
        float minX = Math.min(Math.min(pts[0], pts[2]), Math.min(pts[4], pts[6]));
        float maxX = Math.max(Math.max(pts[0], pts[2]), Math.max(pts[4], pts[6]));
        float minY = Math.min(Math.min(pts[1], pts[3]), Math.min(pts[5], pts[7]));
        float maxY = Math.max(Math.max(pts[1], pts[3]), Math.max(pts[5], pts[7]));
        dst.set(minX, minY, maxX, maxY);
        return m[1] == 0 && m[3] == 0;
    }

    public boolean mapRect(RectF rect) {
        return mapRect(rect, rect);
    }

    public boolean setRectToRect(RectF src, RectF dst, ScaleToFit stf) {
        if (src.isEmpty()) {
            reset();
            return false;
        }
        float sx = dst.width() / src.width();
        float sy = dst.height() / src.height();
        if (stf == ScaleToFit.FILL) {
            reset();
            m[0] = sx;
            m[4] = sy;
            m[2] = dst.left - src.left * sx;
            m[5] = dst.top - src.top * sy;
            return true;
        }
        float s = Math.min(sx, sy);
        float tx = dst.left - src.left * s;
        float ty = dst.top - src.top * s;
        if (stf == ScaleToFit.CENTER || stf == ScaleToFit.END) {
            float diffX = dst.width() - src.width() * s;
            float diffY = dst.height() - src.height() * s;
            if (stf == ScaleToFit.CENTER) {
                diffX /= 2;
                diffY /= 2;
            }
            tx += diffX;
            ty += diffY;
        }
        reset();
        m[0] = s;
        m[4] = s;
        m[2] = tx;
        m[5] = ty;
        return true;
    }

    @Override
    public boolean equals(Object obj) {
        return obj instanceof Matrix && java.util.Arrays.equals(m, ((Matrix) obj).m);
    }

    @Override
    public int hashCode() {
        return java.util.Arrays.hashCode(m);
    }

    @Override
    public String toString() {
        return "Matrix" + java.util.Arrays.toString(m);
    }
}
