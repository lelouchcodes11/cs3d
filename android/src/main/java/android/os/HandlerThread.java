package android.os;

public class HandlerThread extends Thread {
    private Looper mLooper;
    private Handler mHandler;
    private final int mPriority;

    public HandlerThread(String name) {
        super(name);
        mPriority = Process.THREAD_PRIORITY_DEFAULT;
        setDaemon(true);
    }

    public HandlerThread(String name, int priority) {
        super(name);
        mPriority = priority;
        setDaemon(true);
    }

    protected void onLooperPrepared() {
    }

    @Override
    public void run() {
        Looper.prepare();
        synchronized (this) {
            mLooper = Looper.myLooper();
            notifyAll();
        }
        onLooperPrepared();
        Looper.loop();
    }

    public Looper getLooper() {
        if (!isAlive()) return null;
        synchronized (this) {
            while (isAlive() && mLooper == null) {
                try {
                    wait();
                } catch (InterruptedException ignored) {
                }
            }
        }
        return mLooper;
    }

    public Handler getThreadHandler() {
        if (mHandler == null) mHandler = new Handler(getLooper());
        return mHandler;
    }

    public boolean quit() {
        Looper looper = getLooper();
        if (looper != null) {
            looper.quit();
            return true;
        }
        return false;
    }

    public boolean quitSafely() {
        return quit();
    }

    public int getThreadId() {
        return (int) threadId();
    }
}
