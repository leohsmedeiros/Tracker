package br.com.phonetracker.lib.interfaces;

import org.json.JSONException;
import org.json.JSONObject;

public interface ITrackedInfo {
    JSONObject getJson () throws JSONException;
}
