package android.util;

import com.lagradost.desktop.runtime.LogBuffer;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.net.UnknownHostException;

public final class Log {
    public static final int VERBOSE = 2;
    public static final int DEBUG = 3;
    public static final int INFO = 4;
    public static final int WARN = 5;
    public static final int ERROR = 6;
    public static final int ASSERT = 7;

    private Log() {
    }

    public static int v(String tag, String msg) { return println(VERBOSE, tag, msg); }
    public static int v(String tag, String msg, Throwable tr) { return println(VERBOSE, tag, msg + '\n' + getStackTraceString(tr)); }
    public static int d(String tag, String msg) { return println(DEBUG, tag, msg); }
    public static int d(String tag, String msg, Throwable tr) { return println(DEBUG, tag, msg + '\n' + getStackTraceString(tr)); }
    public static int i(String tag, String msg) { return println(INFO, tag, msg); }
    public static int i(String tag, String msg, Throwable tr) { return println(INFO, tag, msg + '\n' + getStackTraceString(tr)); }
    public static int w(String tag, String msg) { return println(WARN, tag, msg); }
    public static int w(String tag, String msg, Throwable tr) { return println(WARN, tag, msg + '\n' + getStackTraceString(tr)); }
    public static int w(String tag, Throwable tr) { return println(WARN, tag, getStackTraceString(tr)); }
    public static int e(String tag, String msg) { return println(ERROR, tag, msg); }
    public static int e(String tag, String msg, Throwable tr) { return println(ERROR, tag, msg + '\n' + getStackTraceString(tr)); }
    public static int wtf(String tag, String msg) { return println(ASSERT, tag, msg); }
    public static int wtf(String tag, Throwable tr) { return println(ASSERT, tag, getStackTraceString(tr)); }
    public static int wtf(String tag, String msg, Throwable tr) { return println(ASSERT, tag, msg + '\n' + getStackTraceString(tr)); }

    public static boolean isLoggable(String tag, int level) {
        return level >= DEBUG;
    }

    public static String getStackTraceString(Throwable tr) {
        if (tr == null) return "";
        // Same behaviour as Android: UnknownHostException stack traces are suppressed
        Throwable t = tr;
        while (t != null) {
            if (t instanceof UnknownHostException) return "";
            t = t.getCause();
        }
        StringWriter sw = new StringWriter();
        PrintWriter pw = new PrintWriter(sw);
        tr.printStackTrace(pw);
        pw.flush();
        return sw.toString();
    }

    public static int println(int priority, String tag, String msg) {
        LogBuffer.INSTANCE.add(priority, tag, msg);
        return msg == null ? 0 : msg.length();
    }
}
