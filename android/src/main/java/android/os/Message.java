package android.os;

public final class Message {
    public int what;
    public int arg1;
    public int arg2;
    public Object obj;
    public Messenger replyTo;
    public int sendingUid = -1;

    long when;
    long seq;
    Handler target;
    Runnable callback;
    private Bundle data;

    public Message() {
    }

    public static Message obtain() {
        return new Message();
    }

    public static Message obtain(Message orig) {
        Message m = new Message();
        m.what = orig.what;
        m.arg1 = orig.arg1;
        m.arg2 = orig.arg2;
        m.obj = orig.obj;
        m.replyTo = orig.replyTo;
        m.target = orig.target;
        m.callback = orig.callback;
        if (orig.data != null) m.data = new Bundle(orig.data);
        return m;
    }

    public static Message obtain(Handler h) {
        Message m = new Message();
        m.target = h;
        return m;
    }

    public static Message obtain(Handler h, Runnable callback) {
        Message m = obtain(h);
        m.callback = callback;
        return m;
    }

    public static Message obtain(Handler h, int what) {
        Message m = obtain(h);
        m.what = what;
        return m;
    }

    public static Message obtain(Handler h, int what, Object obj) {
        Message m = obtain(h, what);
        m.obj = obj;
        return m;
    }

    public static Message obtain(Handler h, int what, int arg1, int arg2) {
        Message m = obtain(h, what);
        m.arg1 = arg1;
        m.arg2 = arg2;
        return m;
    }

    public static Message obtain(Handler h, int what, int arg1, int arg2, Object obj) {
        Message m = obtain(h, what, arg1, arg2);
        m.obj = obj;
        return m;
    }

    public void recycle() {
    }

    public void copyFrom(Message o) {
        what = o.what;
        arg1 = o.arg1;
        arg2 = o.arg2;
        obj = o.obj;
        replyTo = o.replyTo;
        data = o.data == null ? null : new Bundle(o.data);
    }

    public long getWhen() {
        return when;
    }

    public void setTarget(Handler target) {
        this.target = target;
    }

    public Handler getTarget() {
        return target;
    }

    public Runnable getCallback() {
        return callback;
    }

    public Bundle getData() {
        if (data == null) data = new Bundle();
        return data;
    }

    public Bundle peekData() {
        return data;
    }

    public void setData(Bundle data) {
        this.data = data;
    }

    public void sendToTarget() {
        target.sendMessage(this);
    }

    public boolean isAsynchronous() {
        return false;
    }

    public void setAsynchronous(boolean async) {
    }

    @Override
    public String toString() {
        return "{ what=" + what + " arg1=" + arg1 + " arg2=" + arg2 + " obj=" + obj + " }";
    }
}
