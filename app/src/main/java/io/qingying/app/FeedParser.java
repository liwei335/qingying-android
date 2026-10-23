package io.qingying.app;

import org.json.JSONArray;
import org.json.JSONObject;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Data-only subscriptions. Does not load remote Java, JavaScript or TVBox spiders. */
public final class FeedParser {
    public static final int LIMIT = 120;
    public static String https(String value) {
        try {
            URI uri = new URI(value.trim());
            if ("https".equalsIgnoreCase(uri.getScheme()) && uri.getHost() != null && uri.getUserInfo() == null)
                return uri.toASCIIString();
        } catch (Exception ignored) { }
        return "";
    }
    public static String id(String source, String key) {
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256").digest((source + "\n" + key).getBytes(StandardCharsets.UTF_8));
            StringBuilder b = new StringBuilder();
            for (byte x : hash) b.append(String.format(Locale.ROOT, "%02x", x & 255));
            return b.toString();
        } catch (Exception e) { throw new IllegalStateException(e); }
    }
    public static String date(String value) {
        try { return Instant.parse(value).toString(); } catch (Exception ignored) { }
        try { return OffsetDateTime.parse(value).toInstant().toString(); } catch (Exception ignored) { }
        try { return LocalDate.parse(value).atStartOfDay().toInstant(ZoneOffset.UTC).toString(); }
        catch (Exception ignored) { return ""; }
    }
    public static List<Video> parse(String body, String source, String base) throws Exception {
        String text = body.replace("\uFEFF", "").trim();
        if (text.startsWith("#EXTM3U")) return m3u(text, source, base);
        JSONArray a = text.startsWith("[") ? new JSONArray(text) : new JSONObject(text).getJSONArray("videos");
        List<Video> list = new ArrayList<>();
        for (int i = 0; i < a.length() && list.size() < LIMIT; i++) {
            JSONObject j = a.optJSONObject(i); if (j == null) continue;
            Video v = new Video(); v.source = source; v.title = j.optString("title").trim();
            v.description = j.optString("description"); v.poster = https(j.optString("poster"));
            v.published = date(j.optString("published")); v.credit = j.optString("credit"); v.page = https(j.optString("page"));
            JSONArray streams = j.optJSONArray("streams");
            if (streams == null) streams = new JSONArray().put(new JSONObject().put("url", j.optString("url")));
            for (int k = 0; k < streams.length() && k < 12; k++) {
                JSONObject s = streams.optJSONObject(k); if (s == null) continue;
                String url = https(s.optString("url"));
                if (!url.isEmpty()) {
                    Video.Stream stream = new Video.Stream(url, s.optString("label", "原画"), Math.max(0, s.optInt("height")));
                    stream.width = Math.max(0, s.optInt("width")); v.streams.add(stream);
                }
            }
            if (v.title.isEmpty() || v.streams.isEmpty()) continue;
            v.id = id(source, j.optString("id", v.streams.get(0).url));
            list.add(v);
        }
        return list;
    }
    private static List<Video> m3u(String body, String source, String base) throws Exception {
        List<Video> list = new ArrayList<>();
        // HLS manifests are media, not catalogs: keep one playable item.
        if (body.contains("#EXT-X-")) {
            Video v = new Video(); v.source = source; v.id = id(source, base); v.title = "HLS 视频";
            v.streams.add(new Video.Stream(https(base), "自适应", 0)); list.add(v); return list;
        }
        String title = "", poster = "";
        Pattern logo = Pattern.compile("tvg-logo=\"([^\"]*)\"");
        for (String raw : body.split("\r?\n")) {
            String line = raw.trim();
            if (line.startsWith("#EXTINF:")) {
                int comma = -1; boolean quoted = false;
                for (int i = 0; i < line.length(); i++) {
                    if (line.charAt(i) == '"') quoted = !quoted;
                    if (line.charAt(i) == ',' && !quoted) { comma = i; break; }
                }
                title = comma >= 0 ? line.substring(comma + 1).trim() : "未命名视频";
                Matcher m = logo.matcher(line); poster = m.find() ? https(m.group(1)) : "";
            } else if (!line.isEmpty() && !line.startsWith("#")) {
                String url;
                try { url = https(new URI(base).resolve(line).toString()); } catch (Exception e) { continue; }
                if (url.isEmpty()) continue;
                Video v = new Video(); v.source = source; v.id = id(source, url);
                v.title = title.isEmpty() ? "未命名视频" : title; v.poster = poster;
                v.streams.add(new Video.Stream(url, "自适应 / 原画", 0)); list.add(v);
                title = ""; poster = ""; if (list.size() == LIMIT) break;
            }
        }
        return list;
    }
}
