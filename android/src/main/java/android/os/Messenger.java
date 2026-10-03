package android.os;

public final class Messenger {
    private final Handler mTarget;

    public Messenger(Handler target) {
        mTarget = target;
    }

    public void send(Message message) {
        mTarget.sendMessage(message);
    }
}
