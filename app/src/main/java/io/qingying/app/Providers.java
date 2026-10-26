package io.qingying.app;

import org.json.JSONArray;
import org.json.JSONObject;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

public final class Providers {
    public static String cmsUrl(String base, String search, int page) {
        String separator = base.contains("?") ? "&" : "?";
        try {
            return base + separator + "ac=detail&pg=" + page + (search.isEmpty() ? "" : "&wd=" + URLEncoder.encode(search, "UTF-8"));
        } catch (java.io.UnsupportedEncodingException e) { throw new IllegalStateException(e); }
    }
    public static List<Video> fetch(Repository.Source source, String query, int page) throws Exception {
        if(source.kind.equals("hongguo"))return Hongguo.search(query,page);
        if(source.kind.equals("cms")){
            Net.Response response=Net.get(cmsUrl(source.url,query,page),2*1024*1024,false,RecommendationFeeds.UA,source.url);
            if(response.code!=200)throw new java.io.IOException("HTTP "+response.code);return cms(response.text(),source.url,source.name);
        }
        return FeedParser.parse(Net.text(source.url), source.url, source.url);
    }
    public static List<Video> cms(String body, String source, String name) throws Exception {
        JSONArray list = new JSONObject(body).getJSONArray("list");
        List<Video> result = new ArrayList<>();
        for (int i = 0; i < list.length() && result.size() < 60; i++) {
            JSONObject j = list.getJSONObject(i);
            String type = j.optString("type_name");
            if (type.matches(".*(伦理|福利|写真|成人|情色).*")) continue;
            Video v = new Video(); v.id = FeedParser.id(source, j.optString("vod_id")); v.source = source;
            v.title = j.optString("vod_name"); v.poster = FeedParser.https(j.optString("vod_pic"));
            v.category = type; v.published = FeedParser.date(j.optString("vod_pubdate")); v.year = j.optString("vod_year");
            if ((type + j.optString("vod_remarks")).toLowerCase(java.util.Locale.ROOT).matches(".*(4k|2160|uhd).*")) v.qualityHint = "片源标注 4K";
            v.credit = name; v.description = j.optString("vod_content", j.optString("vod_blurb"));
            v.description += "\n\n" + j.optString("vod_area") + " · " + j.optString("vod_year") + " · " + j.optString("vod_remarks");
            try {
                v.updated = LocalDateTime.parse(j.optString("vod_time"), DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))
                    .toInstant(ZoneOffset.ofHours(8)).toString();
            } catch (Exception ignored) { }
            String[] groups = j.optString("vod_play_url").split("\\$\\$\\$");
            String[] names = j.optString("vod_play_from").split("\\$\\$\\$");
            for (int g = 0; g < groups.length && g < 6; g++) {
                String[] episodes = groups[g].split("#");
                for (int e = Math.max(0, episodes.length - 600); e < episodes.length; e++) {
                    int split = episodes[e].indexOf('$'); if (split < 0) continue;
                    String url = FeedParser.https(episodes[e].substring(split + 1));
                    if (!directMedia(url)) continue;
                    String episode = episodes[e].substring(0, split);
                    Video.Stream stream = new Video.Stream(url, episode + " · " + (g < names.length ? names[g] : "线路 " + (g+1)), 0);
                    stream.episode = episode.matches("(?i)(HD.*|BD.*|4K|2160p|1080p|720p|正片|蓝光|超清|高清)") ? "" : episode; v.streams.add(stream);
                }
            }
            if (!v.title.isEmpty() && !v.streams.isEmpty()) result.add(v);
        }
        return result;
    }
    static boolean directMedia(String url){
        try{return !FeedParser.https(url).isEmpty()&&java.net.URI.create(url).getPath().toLowerCase(java.util.Locale.ROOT).matches(".*\\.(m3u8|mpd|mp4|m4v|webm|mkv)$");}catch(Exception ignored){return false;}
    }
    public static List<Video> demo() {
        Video v = new Video(); v.source = "builtin"; v.id = "sintel-4k"; v.title = "Sintel · 辛特尔";
        v.description = "Blender 开放动画短片。此公开 DASH 演示源提供多档画质，最高 3840 × 1636，适合检查设备的 4K 解码和网络表现。";
        v.credit = "© Blender Foundation · durian.blender.org · CC BY 3.0 / 演示源：Shaka Player";
        v.page = "https://durian.blender.org/"; v.poster = "https://storage.googleapis.com/shaka-asset-icons/sintel.png";
        v.category = "动画片"; v.published = "2010-09-27T00:00:00Z";
        Video.Stream stream = new Video.Stream("https://storage.googleapis.com/shaka-demo-assets/sintel-mp4-only/dash.mpd", "自适应 · 最高 4K", 1636);
        stream.width = 3840; v.streams.add(stream);
        return java.util.Collections.singletonList(v);
    }
}
