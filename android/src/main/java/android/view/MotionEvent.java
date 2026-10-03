package android.view;

import android.os.Parcel;
import android.os.Parcelable;

public final class MotionEvent extends InputEvent implements Parcelable {
    public static final int ACTION_MASK = 0xff;
    public static final int ACTION_DOWN = 0;
    public static final int ACTION_UP = 1;
    public static final int ACTION_MOVE = 2;
    public static final int ACTION_CANCEL = 3;
    public static final int ACTION_OUTSIDE = 4;
    public static final int ACTION_POINTER_DOWN = 5;
    public static final int ACTION_POINTER_UP = 6;
    public static final int ACTION_HOVER_MOVE = 7;
    public static final int ACTION_SCROLL = 8;
    public static final int ACTION_HOVER_ENTER = 9;
    public static final int ACTION_HOVER_EXIT = 10;
    public static final int ACTION_BUTTON_PRESS = 11;
    public static final int ACTION_BUTTON_RELEASE = 12;
    public static final int ACTION_POINTER_INDEX_MASK = 0xff00;
    public static final int ACTION_POINTER_INDEX_SHIFT = 8;
    public static final int BUTTON_PRIMARY = 1;
    public static final int BUTTON_SECONDARY = 1 << 1;
    public static final int BUTTON_TERTIARY = 1 << 2;
    public static final int TOOL_TYPE_FINGER = 1;
    public static final int TOOL_TYPE_MOUSE = 3;
    public static final int AXIS_X = 0;
    public static final int AXIS_Y = 1;
    public static final int AXIS_VSCROLL = 9;
    public static final int AXIS_HSCROLL = 10;

    private long mDownTime;
    private long mEventTime;
    private int mAction;
    private float mX;
    private float mY;
    private int mMetaState;
    private int mSource;
    private float mVScroll;

    private MotionEvent() {
    }

    public static MotionEvent obtain(long downTime, long eventTime, int action, float x, float y, int metaState) {
        MotionEvent ev = new MotionEvent();
        ev.mDownTime = downTime;
        ev.mEventTime = eventTime;
        ev.mAction = action;
        ev.mX = x;
        ev.mY = y;
        ev.mMetaState = metaState;
        return ev;
    }

    public static MotionEvent obtain(long downTime, long eventTime, int action, float x, float y, float pressure, float size,
                                     int metaState, float xPrecision, float yPrecision, int deviceId, int edgeFlags) {
        return obtain(downTime, eventTime, action, x, y, metaState);
    }

    public static MotionEvent obtain(MotionEvent other) {
        MotionEvent ev = obtain(other.mDownTime, other.mEventTime, other.mAction, other.mX, other.mY, other.mMetaState);
        ev.mSource = other.mSource;
        ev.mVScroll = other.mVScroll;
        return ev;
    }

    public static MotionEvent obtainNoHistory(MotionEvent other) {
        return obtain(other);
    }

    /** Desktop: scroll wheel event */
    public static MotionEvent obtainScroll(long eventTime, float x, float y, float vscroll) {
        MotionEvent ev = obtain(eventTime, eventTime, ACTION_SCROLL, x, y, 0);
        ev.mVScroll = vscroll;
        return ev;
    }

    @Override
    public void recycle() {
    }

    public final int getAction() { return mAction; }
    public final int getActionMasked() { return mAction & ACTION_MASK; }
    public final int getActionIndex() { return (mAction & ACTION_POINTER_INDEX_MASK) >> ACTION_POINTER_INDEX_SHIFT; }
    public final void setAction(int action) { mAction = action; }
    public final float getX() { return mX; }
    public final float getY() { return mY; }
    public final float getX(int pointerIndex) { return mX; }
    public final float getY(int pointerIndex) { return mY; }
    public final float getRawX() { return mX; }
    public final float getRawY() { return mY; }
    public final float getPressure() { return 1f; }
    public final float getSize() { return 1f; }
    public final int getPointerCount() { return 1; }
    public final int getPointerId(int pointerIndex) { return 0; }
    public final int findPointerIndex(int pointerId) { return pointerId == 0 ? 0 : -1; }
    public final int getToolType(int pointerIndex) { return TOOL_TYPE_FINGER; }
    public final int getMetaState() { return mMetaState; }
    public final int getButtonState() { return BUTTON_PRIMARY; }
    public final long getDownTime() { return mDownTime; }
    @Override public final long getEventTime() { return mEventTime; }
    @Override public final int getDeviceId() { return 0; }
    @Override public final int getSource() { return mSource; }
    public final void setSource(int source) { mSource = source; }
    public final int getHistorySize() { return 0; }
    public final int getEdgeFlags() { return 0; }
    public final float getAxisValue(int axis) {
        switch (axis) {
            case AXIS_X: return mX;
            case AXIS_Y: return mY;
            case AXIS_VSCROLL: return mVScroll;
            default: return 0;
        }
    }

    public final void setLocation(float x, float y) {
        mX = x;
        mY = y;
    }

    public final void offsetLocation(float deltaX, float deltaY) {
        mX += deltaX;
        mY += deltaY;
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel out, int flags) {
    }

    @Override
    public String toString() {
        return "MotionEvent { action=" + mAction + ", x=" + mX + ", y=" + mY + " }";
    }
}
