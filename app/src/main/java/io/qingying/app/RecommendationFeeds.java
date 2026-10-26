package io.qingying.app;

import org.json.JSONArray;
import org.json.JSONObject;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Public recommendation metadata only; official page URLs are never treated as playable streams. */
public final class RecommendationFeeds {
    public static final String UA="Mozilla/5.0 (Linux; Android 10) AppleWebKit/537.36 Chrome/130.0.0.0 Mobile Safari/537.36";
    public static final class Feed {
        public final String id,provider,group,kind,url,page,channel;
        Feed(String id,String provider,String group,String kind,String url,String page,String channel){this.id=id;this.provider=provider;this.group=group;this.kind=kind;this.url=url;this.page=page;this.channel=channel;}
    }
    private static Feed douban(String group){String page="https://m.douban.com/subject_collection/"+group;return new Feed(group,"豆瓣",group,"douban",page.replace("/subject_collection/","/rexxar/api/v2/subject_collection/")+"/items?start=0&count=30",page,"");}
    private static Feed iqiyi(String group,String channel){String page="https://www.iqiyi.com/ranks1/"+channel+"/0";return new Feed("iqiyi:"+group,"爱奇艺",group,"iqiyi",page,page,channel);}
    private static Feed tencent(String group,String channel){return new Feed("tencent:"+group,"腾讯视频",group,"tencent","https://pbaccess.video.qq.com/trpc.tencentvideohotlistdata.hotlistdata.DoSearchHotListHttp/getSearchHotListHttp?ChannelID="+channel+"&PageSize=30&PageNum=0&SceneID=2","https://v.qq.com/biu/ranks/",channel);}
    private static Feed bili(String channel){return new Feed("bilibili:"+channel,"哔哩哔哩",Recommendations.ANIME,"bilibili","https://api.bilibili.com/pgc/web/rank/list?day=3&season_type="+channel,"https://www.bilibili.com/",channel);}
    private static Feed mango(String group,String channel,String path){return new Feed("mango:"+group,"芒果TV",group,"mango","https://dc.bz.mgtv.com/dynamic/v1/channel/index/0/1.0.0/10/1000000/space/0/4/"+channel+"?osType=windows","https://www.mgtv.com/"+path+"/",channel);}
    public static final List<Feed> ALL=java.util.Collections.unmodifiableList(Arrays.asList(
        douban(Recommendations.TV),douban(Recommendations.MOVIE),douban(Recommendations.ANIME),
        new Feed("hongguo:"+Recommendations.SHORT,"红果短剧",Recommendations.SHORT,"hongguo","https://hongguoduanju.com/rank/hot-real-drama","https://hongguoduanju.com/rank/hot-real-drama","real"),
        tencent(Recommendations.TV,"2"),tencent(Recommendations.MOVIE,"1"),tencent(Recommendations.ANIME,"3"),
        iqiyi(Recommendations.TV,"2"),iqiyi(Recommendations.MOVIE,"1"),iqiyi(Recommendations.ANIME,"4"),iqiyi(Recommendations.SHORT,"35"),
        bili("4"),bili("1"),mango(Recommendations.TV,"4","tv"),mango(Recommendations.MOVIE,"9","movie"),mango(Recommendations.SHORT,"100970","short")));
    public static List<Feed> forGroup(String group){List<Feed> feeds=new ArrayList<>();for(Feed f:ALL)if(f.group.equals(group))feeds.add(f);return feeds;}
    public static List<String> providers(String group){List<String> names=new ArrayList<>();for(Feed f:forGroup(group))if(!names.contains(f.provider))names.add(f.provider);return names;}
    public static List<Video> fetch(Feed feed) throws Exception {
        Net.Response response=Net.get(feed.url,2*1024*1024,false,UA,feed.page);
        if(response.code!=200)throw new Exception("HTTP "+response.code);
        return parse(response.text(),feed);
    }
    public static List<Video> parse(String body,Feed feed) throws Exception {
        List<Video> result;
        switch(feed.kind){
            case "douban":result=Recommendations.parse(body,feed.group);break;
            case "iqiyi":result=parseIqiyi(body,feed);break;
            case "tencent":result=parseTencent(body,feed);break;
            case "bilibili":result=parseBili(body,feed);break;
            case "mango":result=parseMango(body,feed);break;
            case "hongguo":result=parseHongguo(body,feed);break;
            default:throw new Exception("不支持的推荐来源");
        }
        if(result.isEmpty())throw new Exception("暂无可用推荐");
        for(Video v:result){v.trendFeed=feed.id;v.trendProvider=feed.provider;if(feed.kind.equals("douban"))v.credit="豆瓣 · 榜单第 "+v.trendRank+" 名";}
        return result;
    }
    private static Video item(Feed feed,String key,String title,int rank){
        Video v=new Video();v.id="trend:"+FeedParser.id(feed.id,key);v.title=clean(title);v.source="trend";v.trendGroup=feed.group;v.category=Recommendations.label(feed.group);v.trendRank=rank;v.trendFeed=feed.id;v.trendProvider=feed.provider;return v;
    }
    private static List<Video> parseTencent(String body,Feed feed) throws Exception {
        JSONObject data=new JSONObject(body).getJSONObject("data");if(data.optInt("ErrCode",-1)!=0)throw new Exception("榜单暂不可用");
        JSONArray groups=data.getJSONArray("Results");List<Video> items=new ArrayList<>();
        for(int i=0;i<groups.length();i++){
            JSONObject group=groups.getJSONObject(i);if(!feed.channel.equals(group.optString("ChannelID")))continue;
            JSONArray list=group.getJSONObject("Result").getJSONArray("ListInfo");
            for(int n=0;n<list.length()&&items.size()<30;n++){
                JSONObject j=list.getJSONObject(n);if(j.optString("Query").isEmpty()||j.optString("ID").isEmpty())continue;
                Video v=item(feed,j.getString("ID"),j.getString("Query"),n+1);v.poster=https(j.optString("HzImgURL"));
                if(v.poster.startsWith("https://vcover-hz-pic.puui.qpic.cn/")&&v.poster.endsWith("/0"))v.poster=v.poster.substring(0,v.poster.length()-1)+"498";
                v.description=clean(j.optString("SecondTitle"));
                v.page="https://v.qq.com/x/cover/"+j.getString("ID")+".html";v.credit="腾讯视频 · 热搜第 "+v.trendRank+" 名";items.add(v);
            }
        }
        return items;
    }
    private static List<Video> parseBili(String body,Feed feed) throws Exception {
        JSONObject data=new JSONObject(body);if(data.optInt("code",-1)!=0)throw new Exception("榜单暂不可用");
        JSONArray list=data.getJSONObject("result").getJSONArray("list");List<Video> items=new ArrayList<>();
        for(int i=0;i<list.length()&&items.size()<30;i++){
            JSONObject j=list.getJSONObject(i);if(j.optString("title").isEmpty())continue;
            Video v=item(feed,j.optString("season_id"),j.getString("title"),j.optInt("rank",i+1));v.poster=https(j.optString("cover"));v.page=https(j.optString("url"));
            JSONObject ep=j.optJSONObject("new_ep");v.description=(ep==null?"":clean(ep.optString("index_show")))+(j.optString("rating").isEmpty()?"":" · 站内评分 "+j.optString("rating"));
            String type=feed.channel.equals("4")?"国创":"番剧";if(feed.channel.equals("4"))v.region="中国大陆";
            v.credit="哔哩哔哩 · "+type+"榜第 "+v.trendRank+" 名";items.add(v);
        }
        return items;
    }
    private static List<Video> parseIqiyi(String html,Feed feed){
        List<Video> items=new ArrayList<>();
        Matcher boxes=Pattern.compile("<a\\b[^>]*\\bclass=\"[^\"]*\\brvi__box\\b[^\"]*\"[^>]*>.*?</a>",Pattern.DOTALL).matcher(html);
        while(boxes.find()&&items.size()<30){
            String box=boxes.group(),title=classAttribute(box,"rvi__tit","title"),page=https(attribute(box.substring(0,box.indexOf('>')+1),"href"));
            if(title.isEmpty()||page.isEmpty())continue;
            Video v=item(feed,page,title,items.size()+1);v.page=page;v.poster=https(classAttribute(box,"i71-img","src"));
            v.description=classAttribute(box,"rvi__des","title");String info=classAttribute(box,"rvi__type","title");if(v.description.isEmpty())v.description=info;
            Matcher year=Pattern.compile("(?:^| / )((?:19|20)\\d{2})(?: / |$)").matcher(info);if(year.find())v.year=year.group(1);
            v.credit="爱奇艺 · 热播第 "+v.trendRank+" 名";items.add(v);
        }
        return items;
    }
    private static String classAttribute(String html,String classPrefix,String attribute){
        Matcher tag=Pattern.compile("<[^>]+\\bclass=\"[^\"]*\\b"+Pattern.quote(classPrefix)+"[^\"]*\"[^>]*>",Pattern.DOTALL).matcher(html);
        return tag.find()?attribute(tag.group(),attribute):"";
    }
    private static String attribute(String tag,String name){Matcher value=Pattern.compile("\\b"+Pattern.quote(name)+"=([\"'])(.*?)\\1",Pattern.DOTALL).matcher(tag);return value.find()?clean(value.group(2)):"";}
    private static List<Video> parseMango(String body,Feed feed) throws Exception {
        JSONObject data=new JSONObject(body);if(data.optInt("code")!=200)throw new Exception("频道暂不可用");
        JSONArray modules=data.getJSONArray("data");List<Video> items=new ArrayList<>();java.util.Set<String> seen=new java.util.HashSet<>();
        for(int i=0;i<modules.length();i++){
            JSONObject module=modules.getJSONObject(i);String title=module.optString("moduleTitle");
            // Use the channel's actual hot-playing modules; skip banners, adverts, trailers and upcoming titles.
            if(!title.contains("热播")&&!title.contains("热映"))continue;
            JSONArray blocks=module.optJSONArray("DSLList");if(blocks==null)continue;
            for(int b=0;b<blocks.length();b++){
                JSONObject block=blocks.getJSONObject(b).optJSONObject("data");if(block==null)continue;JSONArray list=block.optJSONArray("items");if(list==null)continue;
                for(int n=0;n<list.length()&&items.size()<30;n++){
                    JSONObject j=list.getJSONObject(n);String key=j.optString("jumpId"),page=https(j.optString("videoUrl"));
                    if(key.isEmpty()||page.isEmpty()||!page.startsWith("https://www.mgtv.com/b/")||!seen.add(key))continue;
                    String name=clean(j.optString("name")).replaceFirst("[\\p{So}\\p{Cs}\\uFE0F].*$","").replaceFirst("[·•](经典上新|会员抢先看|限时免费|热播).*$","");if(name.isEmpty())continue;
                    Video v=item(feed,key,name,items.size()+1);v.page=page;v.poster=https(j.optString("imageV"));if(v.poster.isEmpty())v.poster=https(j.optString("imageH"));
                    v.description=clean(j.optString("subName"));v.credit="芒果TV · "+title;items.add(v);
                }
            }
        }
        return items;
    }
    private static List<Video> parseHongguo(String html,Feed feed) throws Exception {
        JSONObject content=null;String route="rank_hot-real-drama/page";
        Matcher initial=Pattern.compile("_ROUTER_DATA\\s*=\\s*(?=\\{)").matcher(html);
        if(initial.find()){
            JSONObject data=(JSONObject)new org.json.JSONTokener(html.substring(initial.end())).nextValue();
            JSONObject loaders=data.optJSONObject("loaderData"),page=loaders==null?null:loaders.optJSONObject(route);
            if(page!=null)content=page.optJSONObject("content");
        }
        // Modern.js streams rank data as JSON arguments in a script attribute. Read data; never execute JS.
        Matcher scripts=Pattern.compile("<script\\b[^>]*>",Pattern.DOTALL).matcher(html);
        while(scripts.find()){
            String tag=scripts.group();if(!"modern-run-router-data-fn".equals(attribute(tag,"data-script-src")))continue;
            String encoded=attribute(tag,"data-fn-args");if(encoded.isEmpty())continue;JSONArray args=new JSONArray(encoded);
            if(route.equals(args.optString(0))&&"content".equals(args.optString(1)))content=args.optJSONObject(2);
        }
        if(content==null||!content.optBoolean("isSuccess"))throw new Exception("红果榜单暂不可用");
        JSONArray list=content.getJSONArray("rankList");List<Video> items=new ArrayList<>();
        for(int i=0;i<list.length()&&items.size()<30;i++){
            JSONObject j=list.getJSONObject(i);String id=j.optString("seriesId",j.optString("id")),title=clean(j.optString("title"));
            if(!id.matches("[0-9]+")||title.isEmpty())continue;
            Video v=item(feed,id,title,j.optInt("rank",i+1));v.hongguoSeriesId=id;v.poster=https(j.optString("cover"));v.page="https://hongguoduanju.com/detail?series_id="+id;
            v.description=clean(j.optString("description"));
            if(v.description.isEmpty()){JSONArray tags=j.optJSONArray("tags");if(tags!=null)for(int t=0;t<tags.length();t++){if(!v.description.isEmpty())v.description+=" · ";v.description+=clean(tags.optString(t));}}
            int count=j.optInt("episodeCount");if(count>0)v.description="全"+count+"集"+(v.description.isEmpty()?"":" · "+v.description);
            v.credit="红果短剧 · 真人热播第 "+v.trendRank+" 名";items.add(v);
        }
        return items;
    }
    public static String posterReferer(String url){
        String host;try{host=java.net.URI.create(url).getHost();}catch(Exception e){return "";}if(host==null)return "";
        String[][] origins={{"byteimg.com","https://hongguoduanju.com/"},{"doubanio.com","https://movie.douban.com/"},{"iqiyipic.com","https://www.iqiyi.com/"},{"qpic.cn","https://v.qq.com/"},{"gtimg.cn","https://v.qq.com/"},{"hdslb.com","https://www.bilibili.com/"},{"hitv.com","https://www.mgtv.com/"},{"mgtv.com","https://www.mgtv.com/"},{"hunantv.com","https://www.mgtv.com/"}};
        for(String[] origin:origins)if(host.equals(origin[0])||host.endsWith("."+origin[0]))return origin[1];return "";
    }
    static String https(String value){String url=clean(value);if(url.startsWith("//"))url="https:"+url;else if(url.startsWith("http://"))url="https://"+url.substring(7);return FeedParser.https(url);}
    static String clean(String text){
        if(text==null||text.equals("null"))return "";
        String value=text.replace("&quot;","\"").replace("&#39;","'").replace("&apos;","'").replace("&nbsp;"," ").replace("&lt;","<").replace("&gt;",">").replace("&amp;","&");
        Matcher entity=Pattern.compile("&#(x[0-9a-fA-F]+|[0-9]+);").matcher(value);StringBuffer out=new StringBuffer();
        while(entity.find()){String code=entity.group(1),replacement=entity.group();try{int point=Integer.parseInt(code.startsWith("x")?code.substring(1):code,code.startsWith("x")?16:10);if(Character.isValidCodePoint(point))replacement=new String(Character.toChars(point));}catch(Exception ignored){}entity.appendReplacement(out,Matcher.quoteReplacement(replacement));}
        entity.appendTail(out);return out.toString().replaceAll("\\s+"," ").trim();
    }
}
