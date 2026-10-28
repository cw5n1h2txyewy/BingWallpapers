package io.github.bingwallpapers;

import org.json.JSONException;
import org.json.JSONObject;

/** Immutable metadata from Bing, with a strict image-host boundary. */
public final class Wallpaper {
    public final String date, title, copyright, base;

    public Wallpaper(JSONObject json) throws JSONException {
        // Bing CN starts at 16:00 UTC on the preceding day; enddate matches its displayed day.
        date = json.optString("enddate", json.getString("startdate"));
        title = json.optString("title", "Bing");
        copyright = json.getString("copyright");
        base = json.getString("urlbase");
        if (!date.matches("[0-9]{8}") || !base.matches("/th\\?id=OHR\\.[A-Za-z0-9_.-]+")) {
            throw new JSONException("Invalid Bing image date or URL");
        }
    }

    public String url(String size) { return "https://www.bing.com" + base + "_" + size + ".jpg"; }

    public JSONObject toJson() throws JSONException {
        return new JSONObject().put("startdate", date).put("title", title).put("copyright", copyright).put("urlbase", base);
    }
}
