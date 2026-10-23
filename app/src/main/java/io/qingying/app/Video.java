package io.qingying.app;

import org.json.JSONArray;
import org.json.JSONObject;
import java.util.ArrayList;
import java.util.List;

public final class Video {
    public String id = "", title = "", description = "", poster = "", published = "";
    public String source = "", credit = "", page = "", updated = "", category = "", lastStream = "", lastEpisode = "";
    public long discovered, position, duration;
    public boolean favorite;
    public String cloudFileId = "", trendGroup = "", year = "", qualityHint = "";
    public int trendRank;
    public double trendScore;
    public final List<Stream> streams = new ArrayList<>();

    public static final class Stream {
        public String url, label, state = "unknown", episode = "", mimeType = "";
        public int height, width;
        public long checked;
        public Stream(String url, String label, int height) {
            this.url = url; this.label = label; this.height = height;
        }
        JSONObject json() throws Exception {
            return new JSONObject().put("url", url).put("label", label).put("height", height)
                .put("state", state).put("checked", checked).put("episode", episode).put("width", width).put("mimeType", mimeType);
        }
    }

    public boolean is4k() { for (Stream s : streams) if (s.height >= 2160 || s.width >= 3840) return true; return false; }
    public boolean isCloud() { return !cloudFileId.isEmpty(); }
    public boolean isRecommendation() { return !trendGroup.isEmpty(); }
    public boolean reachable() { for (Stream s : streams) if (s.state.equals("ok")) return true; return false; }
    public boolean missing() {
        if (streams.isEmpty()) return false;
        for (Stream s : streams) if (!s.state.equals("missing")) return false;
        return true;
    }
    public long checked() { long t = 0; for (Stream s : streams) t = Math.max(t, s.checked); return t; }
    public String status() {
        if (reachable()) return "链接可达";
        if (missing()) return "资源已失效";
        return checked() == 0 ? "待检查" : "暂时不可达";
    }
    public String dateLabel() { return published.length() >= 10 ? published.substring(0, 10) : "发布日期未提供"; }
    public JSONObject json() throws Exception {
        JSONArray array = new JSONArray();
        // Cloud playback URLs expire and can contain access tokens. Never persist them.
        if (isCloud()) {
            Stream local = new Stream("", "网盘原画", 0);
            for (Stream s : streams) { local.width = Math.max(local.width, s.width); local.height = Math.max(local.height, s.height); }
            array.put(local.json());
        } else for (Stream s : streams) array.put(s.json());
        return new JSONObject().put("id", id).put("title", title).put("description", description)
            .put("poster", poster).put("published", published).put("source", source).put("credit", credit)
            .put("page", page).put("updated", updated).put("category", category).put("lastStream", isCloud()?"":lastStream).put("lastEpisode", lastEpisode)
            .put("discovered", discovered).put("favorite", favorite)
            .put("position", position).put("duration", duration).put("streams", array)
            .put("cloudFileId", cloudFileId).put("trendGroup", trendGroup).put("trendRank", trendRank)
            .put("trendScore", trendScore).put("year", year).put("qualityHint", qualityHint);
    }
    public static Video from(JSONObject j) {
        Video v = new Video();
        v.id = j.optString("id"); v.title = j.optString("title"); v.description = j.optString("description");
        v.poster = j.optString("poster"); v.published = j.optString("published"); v.source = j.optString("source");
        v.credit = j.optString("credit"); v.page = j.optString("page"); v.discovered = j.optLong("discovered");
        v.updated = j.optString("updated"); v.category = j.optString("category"); v.lastStream = j.optString("lastStream");
        v.lastEpisode = j.optString("lastEpisode");
        v.favorite = j.optBoolean("favorite"); v.position = j.optLong("position"); v.duration = j.optLong("duration");
        v.cloudFileId = j.optString("cloudFileId"); v.trendGroup = j.optString("trendGroup");
        v.trendRank = j.optInt("trendRank"); v.trendScore = j.optDouble("trendScore", 0);
        v.year = j.optString("year"); v.qualityHint = j.optString("qualityHint");
        JSONArray a = j.optJSONArray("streams");
        if (a != null) for (int i = 0; i < a.length(); i++) {
            JSONObject s = a.optJSONObject(i); if (s == null) continue;
            Stream st = new Stream(s.optString("url"), s.optString("label", "原画"), s.optInt("height"));
            st.width = s.optInt("width");
            st.mimeType = s.optString("mimeType");
            st.state = s.optString("state", "unknown"); st.checked = s.optLong("checked"); st.episode = s.optString("episode"); v.streams.add(st);
        }
        return v;
    }
}
