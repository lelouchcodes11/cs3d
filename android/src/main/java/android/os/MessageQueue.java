package android.os;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.PriorityQueue;

public final class MessageQueue {
    private final Looper mLooper;
    private final PriorityQueue<Message> mMessages = new PriorityQueue<>((a, b) -> {
        int c = Long.compare(a.when, b.when);
        return c != 0 ? c : Long.compare(a.seq, b.seq);
    });
    private long mSeq = 0;
    private boolean mQuitting = false;

    MessageQueue(Looper looper) {
        mLooper = looper;
    }

    boolean enqueueMessage(Message msg, long when) {
        synchronized (this) {
            if (mQuitting) return false;
            msg.when = when;
            msg.seq = mSeq++;
            mMessages.add(msg);
            notifyAll();
        }
        if (mLooper.isMain()) mLooper.scheduleMain(when);
        return true;
    }

    boolean hasMessages(Handler h, int what, Object object) {
        synchronized (this) {
            for (Message m : mMessages) {
                if (m.target == h && m.what == what && (object == null || m.obj == object)) return true;
            }
        }
        return false;
    }

    boolean hasCallbacks(Handler h, Runnable r, Object object) {
        synchronized (this) {
            for (Message m : mMessages) {
                if (m.target == h && m.callback == r && (object == null || m.obj == object)) return true;
            }
        }
        return false;
    }

    void removeMessages(Handler h, int what, Object object) {
        synchronized (this) {
            mMessages.removeIf(m -> m.target == h && m.callback == null && m.what == what && (object == null || m.obj == object));
        }
    }

    void removeCallbacks(Handler h, Runnable r, Object object) {
        synchronized (this) {
            mMessages.removeIf(m -> m.target == h && m.callback == r && (object == null || m.obj == object));
        }
    }

    void removeCallbacksAndMessages(Handler h, Object object) {
        synchronized (this) {
            mMessages.removeIf(m -> m.target == h && (object == null || m.obj == object));
        }
    }

    void quit() {
        synchronized (this) {
            mQuitting = true;
            mMessages.clear();
            notifyAll();
        }
    }

    /** Main looper: dispatch every message that is due now (runs on the EDT) */
    void processDueMessages() {
        long now = SystemClock.uptimeMillis();
        List<Message> due = new ArrayList<>();
        synchronized (this) {
            while (!mMessages.isEmpty() && mMessages.peek().when <= now) {
                due.add(mMessages.poll());
            }
        }
        for (Message m : due) {
            dispatch(m);
        }
    }

    /** Background looper: block and dispatch until quit */
    void runLoop() {
        while (true) {
            Message next;
            synchronized (this) {
                while (true) {
                    if (mQuitting) return;
                    Message head = mMessages.peek();
                    long now = SystemClock.uptimeMillis();
                    if (head != null && head.when <= now) {
                        next = mMessages.poll();
                        break;
                    }
                    try {
                        if (head == null) wait();
                        else wait(Math.max(1, head.when - now));
                    } catch (InterruptedException e) {
                        return;
                    }
                }
            }
            dispatch(next);
        }
    }

    private void dispatch(Message m) {
        try {
            if (m.target != null) m.target.dispatchMessage(m);
            else if (m.callback != null) m.callback.run();
        } catch (Throwable t) {
            android.util.Log.e("Looper", "Uncaught exception in " + mLooper + ": " + android.util.Log.getStackTraceString(t));
        }
    }

    public boolean isIdle() {
        synchronized (this) {
            Message head = mMessages.peek();
            return head == null || SystemClock.uptimeMillis() < head.when;
        }
    }
}
