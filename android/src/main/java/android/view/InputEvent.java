package android.view;

public abstract class InputEvent {
    InputEvent() {
    }

    public abstract int getDeviceId();

    public abstract long getEventTime();

    public int getSource() {
        return 0;
    }

    public void recycle() {
    }
}
