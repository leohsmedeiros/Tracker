package br.com.phonetracker.lib.commons;

import android.content.Context;
import android.content.SharedPreferences;
import com.google.gson.Gson;

public class TrackerSharedPreferences {
    private static String SHARED_PREFERENCES_NAME = "TrackerBackgroundService";

    public static <T> void save (Context context, T content) {
        SharedPreferences.Editor editor = context.getSharedPreferences(SHARED_PREFERENCES_NAME, Context.MODE_PRIVATE).edit();
        editor.remove(content.getClass().getName()).apply();
        editor.putString(content.getClass().getName(), new Gson().toJson(content)).apply();
    }

    public static <T> T load (Context context, Class<T> Tclass) {
        SharedPreferences prefs = context.getSharedPreferences(SHARED_PREFERENCES_NAME, Context.MODE_PRIVATE);
        String restoredText = prefs.getString(Tclass.getName(), null);
        return (restoredText != null) ? new Gson().fromJson(restoredText, Tclass) : null;
    }

    public static <T> void remove (Context context, Class<T> Tclass) {
        SharedPreferences.Editor editor = context.getSharedPreferences(SHARED_PREFERENCES_NAME, Context.MODE_PRIVATE).edit();
        editor.remove(Tclass.getName()).apply();
    }
}
