package com.lagradost.desktop.sandbox;

import java.util.function.Supplier;

/**
 * The gate through which the app's own code does something on behalf of an extension that the extension sandbox would stop the extension itself from
 * doing (open a link in the browser, copy text to the clipboard, show a page in the web view). While [#run] is executing on a thread, the sandbox treats
 * that thread as the app's, whatever extension code is further down the call stack.
 *
 * It lives in this module because the Android shims here (web view, intents) need it and the sandbox itself is in the app module.
 */
public final class SandboxGate {
    private SandboxGate() {}

    private static final ThreadLocal<int[]> DEPTH = ThreadLocal.withInitial(() -> new int[1]);

    /** True while the current thread is inside [#run] */
    public static boolean isTrusted() {
        return DEPTH.get()[0] > 0;
    }

    /** Runs [action] as the app's own code */
    public static <T> T run(Supplier<T> action) {
        int[] depth = DEPTH.get();
        depth[0]++;
        try {
            return action.get();
        } finally {
            depth[0]--;
        }
    }

    public static void run(Runnable action) {
        int[] depth = DEPTH.get();
        depth[0]++;
        try {
            action.run();
        } finally {
            depth[0]--;
        }
    }
}
