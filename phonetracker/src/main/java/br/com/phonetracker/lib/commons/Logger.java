package br.com.phonetracker.lib.commons;

import android.util.Log;

public class Logger {
    private static String TAG = "phonetracker";

    public static void d (String msg) {
        Log.d(TAG, msg);
    }
    public static void w (String msg) {
        Log.w(TAG, msg);
    }
    public static void e (String msg, Throwable throwable) {
        Log.e(TAG, msg, throwable);
    }
    public static void e (String msg) {
        Log.e(TAG, msg);
    }
}
