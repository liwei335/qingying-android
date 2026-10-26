package io.qingying.app;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Official web playback. The site's /video/public/ MP4 URLs are short previews, not full episodes. */
public final class Hongguo {
    public static final String SOURCE="https://hongguoduanju.com/";
    public static String seriesId(Video video){
        if(video.hongguoSeriesId.matches("[0-9]+"))return video.hongguoSeriesId;
        try{URI page=URI.create(video.page);if(!officialUrl(video.page))return "";
            Matcher id=Pattern.compile("(?:^|&)series_id=([0-9]+)(?:&|$)").matcher(page.getRawQuery()==null?"":page.getRawQuery());if(id.find())return id.group(1);
        }catch(Exception ignored){}return "";
    }
    public static boolean officialUrl(String url){
        try{URI uri=URI.create(url);return "https".equals(uri.getScheme())&&(uri.getHost().equals("hongguoduanju.com")||uri.getHost().equals("www.hongguoduanju.com"))&&uri.getUserInfo()==null&&(uri.getPort()==-1||uri.getPort()==443);}catch(Exception e){return false;}
    }
    public static String savedPlayerUrl(String url,String series){
        if(!series.matches("[0-9]+")||!officialUrl(url))return "";
        URI uri=URI.create(url);return uri.getPath().matches("/player/"+series+"(?:/[0-9]+)?")?SOURCE.substring(0,SOURCE.length()-1)+uri.getPath():"";
    }
    public static String playbackUrl(Video video){
        String id=seriesId(video);if(id.isEmpty())throw new IllegalArgumentException("红果剧集标识无效");
        String saved=savedPlayerUrl(video.lastStream,id);return saved.isEmpty()?SOURCE+"player/"+id:saved;
    }
    public static boolean preview(String url){return url!=null&&url.contains("/video/public/");}
    public static Video catalog(Video wanted){
        String id=seriesId(wanted);if(id.isEmpty())throw new IllegalArgumentException("红果剧集标识无效");
        Video v=new Video();v.id="hongguo:"+id;v.hongguoSeriesId=id;v.source=SOURCE;v.title=wanted.title;v.poster=wanted.poster;v.description=wanted.description;v.year=wanted.year;v.region=wanted.region;
        v.category="短剧";v.credit="红果官方网页";v.page=SOURCE+"player/"+id;
        v.streams.add(new Video.Stream("","红果官方网页播放器",0));return v;
    }
    /** Official hot-list search is scoped to currently indexed recommendations. */
    public static List<Video> search(String query,int page) throws Exception {
        List<Video> result=new ArrayList<>();if(page>1)return result;
        RecommendationFeeds.Feed feed=RecommendationFeeds.ALL.stream().filter(f->f.kind.equals("hongguo")).findFirst().get();
        for(Video v:RecommendationFeeds.fetch(feed))if(query.isEmpty()||CatalogLogic.titleKey(v.title).contains(CatalogLogic.titleKey(query)))result.add(catalog(v));
        return result;
    }
}
