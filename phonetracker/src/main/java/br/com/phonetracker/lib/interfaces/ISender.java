package br.com.phonetracker.lib.interfaces;

import android.content.Context;

import org.json.JSONObject;

public interface ISender {
    void connect (Context context);
    boolean isConnected ();
    void disconnect ();
    void send (JSONObject object);
}
