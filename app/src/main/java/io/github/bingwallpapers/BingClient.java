package io.github.bingwallpapers;

import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import org.json.*;

/** HTTPS-only Bing access with bounded downloads and explicit HTTP errors. */
public final class BingClient {
    private BingClient() {}

    public static void fetch(String url, OutputStream target, int limit) throws IOException {
        HttpURLConnection connection = (HttpURLConnection) new URL(url).openConnection();
        connection.setConnectTimeout(15000);
        connection.setReadTimeout(30000);
        connection.setRequestProperty("User-Agent", "BingWallpapers/1.0 (Android)");
        try {
            int code = connection.getResponseCode();
            if (code != 200) throw new IOException("Bing 请求失败：HTTP " + code);
            try (InputStream input = connection.getInputStream()) {
                byte[] buffer = new byte[32768];
                int count, total = 0;
                while ((count = input.read(buffer)) != -1) {
                    if (Thread.currentThread().isInterrupted()) throw new InterruptedIOException("请求已取消");
                    total += count;
                    if (total > limit) throw new IOException("Bing 响应超过大小限制");
                    target.write(buffer, 0, count);
                }
            }
        } finally { connection.disconnect(); }
    }

    public static String metadata(String market) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        fetch("https://www.bing.com/HPImageArchive.aspx?format=js&idx=0&n=8&mkt=" + market, output, 1024 * 1024);
        return output.toString(StandardCharsets.UTF_8);
    }

    public static List<Wallpaper> parse(String raw) throws JSONException {
        JSONArray array = new JSONObject(raw).getJSONArray("images");
        List<Wallpaper> images = new ArrayList<>();
        for (int i = 0; i < array.length(); i++) images.add(new Wallpaper(array.getJSONObject(i)));
        if (images.isEmpty()) throw new JSONException("Bing 暂未返回图片");
        return images;
    }
}
