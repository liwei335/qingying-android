package io.qingying.app;

import org.json.JSONArray;
import org.json.JSONObject;
import java.util.ArrayList;
import java.util.List;

public final class Recommendations {
    public static final String TV = "tv_hot", MOVIE = "movie_hot_gaia", ANIME = "tv_animation";
    public static final String[] GROUPS = { TV, MOVIE, ANIME };
    public static String label(String group) { return ANIME.equals(group)?"动漫热榜":MOVIE.equals(group)?"热门电影":"热播剧集"; }
    public static List<Video> fetch(String group) throws Exception {
        String page = "https://m.douban.com/subject_collection/" + group;
        Net.Response response = Net.get("https://m.douban.com/rexxar/api/v2/subject_collection/"+group+"/items?start=0&count=30",
            1024*1024, false, "Mozilla/5.0 (Linux; Android 8.0) AppleWebKit/537.36 Chrome/120.0.0.0 Mobile Safari/537.36", page);
        if (response.code != 200) throw new Exception("热榜暂不可用（HTTP "+response.code+"）");
        return parse(response.text(), group);
    }
    public static List<Video> parse(String body, String group) throws Exception {
        JSONArray items = new JSONObject(body).getJSONArray("subject_collection_items");
        List<Video> result = new ArrayList<>();
        for(int i=0;i<items.length()&&i<30;i++) {
            JSONObject j=items.getJSONObject(i); String title=j.optString("title"); if(title.isEmpty())continue;
            Video v=new Video();v.id="trend:"+group+":"+j.optString("id");v.title=title;v.source="trend";
            v.trendGroup=group;v.trendRank=i+1;v.year=j.optString("year");v.category=label(group);v.credit="豆瓣 · "+label(group);
            JSONObject pic=j.optJSONObject("pic");if(pic!=null)v.poster=FeedParser.https(pic.optString("normal",pic.optString("large")));
            JSONObject rating=j.optJSONObject("rating");if(rating!=null)v.trendScore=rating.optDouble("value",0);
            v.page="https://movie.douban.com/subject/"+j.optString("id")+"/";
            result.add(v);
        }
        if(result.isEmpty())throw new Exception("热榜暂无数据");return result;
    }
}
