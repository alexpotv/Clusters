package com.example.clusterapp.plugin;

import org.json.JSONException;
import org.json.JSONObject;

public final class RepoInfo {
    public final String name;
    public final String url;

    public RepoInfo(String name, String url) {
        this.name = name;
        this.url  = url;
    }

    public JSONObject toJson() throws JSONException {
        return new JSONObject().put("name", name).put("url", url);
    }

    public static RepoInfo fromJson(JSONObject obj) throws JSONException {
        return new RepoInfo(obj.getString("name"), obj.getString("url"));
    }
}
