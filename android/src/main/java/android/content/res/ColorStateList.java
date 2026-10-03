package android.content.res;

import android.os.Parcel;
import android.os.Parcelable;

import java.util.Arrays;

public class ColorStateList implements Parcelable {
    private static final int[][] EMPTY = new int[][]{new int[0]};

    private final int[][] mStateSpecs;
    private final int[] mColors;
    private final int mDefaultColor;

    public ColorStateList(int[][] states, int[] colors) {
        mStateSpecs = states;
        mColors = colors;
        int defaultColor = colors.length > 0 ? colors[0] : 0xFF000000;
        for (int i = 0; i < states.length; i++) {
            if (states[i].length == 0) {
                defaultColor = colors[i];
                break;
            }
        }
        mDefaultColor = defaultColor;
    }

    public static ColorStateList valueOf(int color) {
        return new ColorStateList(EMPTY, new int[]{color});
    }

    public ColorStateList withAlpha(int alpha) {
        int[] colors = new int[mColors.length];
        for (int i = 0; i < colors.length; i++) colors[i] = (mColors[i] & 0xFFFFFF) | (alpha << 24);
        return new ColorStateList(mStateSpecs, colors);
    }

    public boolean isStateful() {
        return mStateSpecs.length >= 1 && mStateSpecs[0].length > 0;
    }

    public boolean isOpaque() {
        for (int c : mColors) if ((c >>> 24) != 0xFF) return false;
        return true;
    }

    public int getColorForState(int[] stateSet, int defaultColor) {
        final int setLength = mStateSpecs.length;
        for (int i = 0; i < setLength; i++) {
            final int[] stateSpec = mStateSpecs[i];
            if (stateSetMatches(stateSpec, stateSet)) return mColors[i];
        }
        return defaultColor;
    }

    private static boolean stateSetMatches(int[] stateSpec, int[] stateSet) {
        if (stateSet == null) return stateSpec == null || stateSpec.length == 0;
        for (int stateSpecState : stateSpec) {
            if (stateSpecState == 0) return true;
            final boolean mustMatch = stateSpecState > 0;
            final int want = mustMatch ? stateSpecState : -stateSpecState;
            boolean found = false;
            for (int s : stateSet) {
                if (s == want) {
                    found = true;
                    break;
                }
            }
            if (mustMatch != found) return false;
        }
        return true;
    }

    public int getDefaultColor() {
        return mDefaultColor;
    }

    public int[][] getStates() {
        return mStateSpecs;
    }

    public int[] getColors() {
        return mColors;
    }

    @Override
    public String toString() {
        return "ColorStateList{mStateSpecs=" + Arrays.deepToString(mStateSpecs) + "mColors=" + Arrays.toString(mColors) + "mDefaultColor=" + mDefaultColor + '}';
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
    }
}
