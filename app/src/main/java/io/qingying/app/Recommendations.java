package io.qingying.app;

import org.json.JSONArray;
import org.json.JSONObject;
import java.util.ArrayList;
import java.util.List;

public final class Recommendations {
    public static final String TV = "tv_hot", MOVIE = "movie_hot_gaia", ANIME = "tv_animation", SHORT = "short_hot";
    public static final String[] GROUPS = { TV, MOVIE, ANIME, SHORT };
    public static String label(String group) { return SHORT.equals(group)?"热播短剧":ANIME.equals(group)?"动漫推荐":MOVIE.equals(group)?"热门电影":"热播剧集"; }
    public static List<Video> fetch(String group) throws Exception {
        String page = "https://m.douban.com/subject_collection/" + group;
        Net.Response response = Net.get("https://m.douban.com/rexxar/api/v2/subject_collection/"+group+"/items?start=0&count=30",
            1024*1024, false, "Mozilla/5.0 (Linux; Android 8.0) AppleWebKit/537.36 Chrome/120.0.0.0 Mobile Safari/537.36", page);
        if (response.code != 200) throw new Exception("热榜暂不可用（HTTP "+response.code+"）");
        return parse(response.text(), group);
    }
    private static String clean(String text){return text==null||text.equals("null")?"":text.replaceAll("<[^>]*>"," ").replaceAll("\\s+"," ").trim();}
    public static boolean domestic(Video video){return video.region.matches(".*(中国|中國|大陆|大陸|香港|台湾|台灣|澳门|澳門).*");}
    /** Stable partition: preserve the provider's original rank inside each regional group. */
    public static List<Video> domesticFirst(List<Video> items){
        List<Video> ordered=new ArrayList<>(items);ordered.sort(java.util.Comparator.comparingInt(v->domestic(v)?0:1));return ordered;
    }
    /** Round-robin feeds, merge matching titles and retain the source's own ranking/credit. */
    public static List<Video> combine(List<Video> items,String provider){
        java.util.Map<String,List<Video>> feeds=new java.util.LinkedHashMap<>();
        for(Video item:items)if(provider.isEmpty()||provider.equals(item.trendProvider))feeds.computeIfAbsent(item.trendFeed,k->new ArrayList<>()).add(item);
        List<Video> merged=new ArrayList<>();
        for(int rank=0;rank<30;rank++)for(List<Video> feed:feeds.values()){
            if(rank>=feed.size())continue;Video item=feed.get(rank),match=null;
            for(Video existing:merged)if(CatalogLogic.sameTitle(existing,item)){match=existing;break;}
            if(match==null){try{merged.add(Video.from(item.json()));}catch(Exception ignored){}continue;}
            if(match.poster.isEmpty())match.poster=item.poster;if(match.description.isEmpty()||item.description.length()>match.description.length())match.description=item.description;
            if(match.hongguoSeriesId.isEmpty())match.hongguoSeriesId=Hongguo.seriesId(item);
            if(match.year.isEmpty())match.year=item.year;if(match.region.isEmpty())match.region=item.region;
            if(item.trendProvider.equals("豆瓣")&&item.trendScore>0)match.trendScore=item.trendScore;
            if(!match.credit.contains(item.credit))match.credit+="；"+item.credit;
        }
        return domesticFirst(merged);
    }
    public static List<Video> parse(String body, String group) throws Exception {
        JSONArray items = new JSONObject(body).getJSONArray("subject_collection_items");
        List<Video> result = new ArrayList<>();
        for(int i=0;i<items.length()&&i<30;i++) {
            JSONObject j=items.getJSONObject(i); String title=j.optString("title"); if(title.isEmpty())continue;
            Video v=new Video();v.id="trend:"+group+":"+j.optString("id");v.title=title;v.source="trend";
            v.trendProvider="豆瓣";v.trendFeed=group;v.trendGroup=group;v.trendRank=i+1;v.year=j.optString("year");v.category=label(group);v.credit="豆瓣 · "+label(group);
            JSONObject pic=j.optJSONObject("pic");
            if(pic!=null){v.poster=FeedParser.https(pic.optString("large"));if(v.poster.isEmpty())v.poster=FeedParser.https(pic.optString("normal"));}
            JSONObject cover=j.optJSONObject("cover");if(v.poster.isEmpty()&&cover!=null)v.poster=FeedParser.https(cover.optString("url"));
            JSONObject rating=j.optJSONObject("rating");if(rating!=null)v.trendScore=rating.optDouble("value",0);
            v.description=clean(j.optString("comment"));
            if(v.description.isEmpty())v.description=clean(j.optString("description"));
            if(v.description.isEmpty())v.description=clean(j.optString("intro"));
            String subtitle=clean(j.optString("card_subtitle"));
            if(v.description.isEmpty()){
                String[] parts=subtitle.split("\\s*/\\s*");
                // Some movie lists supply genre/cast information rather than a plot synopsis.
                v.description=parts.length>2?parts[2]+(parts.length>3?" · "+parts[3]+" 导演":"")+(parts.length>4?" · "+parts[4]+" 主演":""):clean(j.optString("info",subtitle));
            }
            JSONArray countries=j.optJSONArray("countries");
            if(countries!=null){for(int n=0;n<countries.length();n++){if(n>0)v.region+=" / ";v.region+=countries.optString(n);}}
            else {String[] parts=subtitle.split("\\s*/\\s*");if(parts.length>1)v.region=parts[1];}
            if(v.poster.isEmpty()){JSONArray photos=j.optJSONArray("photos");if(photos!=null)v.poster=FeedParser.https(photos.optString(0));}
            v.page="https://movie.douban.com/subject/"+j.optString("id")+"/";
            result.add(v);
        }
        if(result.isEmpty())throw new Exception("热榜暂无数据");return result;
    }
}
