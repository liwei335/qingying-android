package io.qingying.app;

import org.junit.Test;
import java.util.Arrays;
import java.util.List;
import static org.junit.Assert.*;

public class RecommendationFeedsTest {
    private RecommendationFeeds.Feed feed(String id){return RecommendationFeeds.ALL.stream().filter(f->f.id.equals(id)).findFirst().get();}
    @Test public void tencentKeepsCorrectChannelArtworkAndSearchRank() throws Exception {
        String body="{\"data\":{\"ErrCode\":0,\"Results\":[{\"ChannelID\":\"1\",\"Result\":{\"ListInfo\":[{\"ID\":\"movie\",\"Query\":\"电影\"}]}},{\"ChannelID\":\"2\",\"Result\":{\"ListInfo\":[{\"ID\":\"tv\",\"Query\":\"热播剧\",\"SecondTitle\":\"悬案背后的真相\",\"HzImgURL\":\"https://vcover-hz-pic.puui.qpic.cn/vcover_hz_pic/0/tv/0\"}]}}]}}";
        List<Video> list=RecommendationFeeds.parse(body,feed("tencent:tv_hot"));assertEquals(1,list.size());Video v=list.get(0);
        assertEquals("热播剧",v.title);assertEquals("腾讯视频",v.trendProvider);assertTrue(v.credit.contains("热搜第 1"));assertTrue(v.poster.endsWith("/tv/498"));assertEquals("悬案背后的真相",v.description);assertTrue(v.streams.isEmpty());assertEquals(0,v.trendScore,0);
    }
    @Test public void iqiyiShortDramaUsesActualCardAndDecodesEntities() throws Exception {
        String html="<a href=\"//www.iqiyi.com/v_abc.html\" class=\"rvi__box rvi__box3\"><img class=\"i71-img\" src=\"//pic1.iqiyipic.com/a.jpg\"><div title=\"短剧&amp;第二季\" class=\"rvi__tit1\">1短剧</div><div title=\"2026 / 真人剧 / 甜宠\" class=\"rvi__type1\"></div><p title=\"她说：&quot;你好&quot;。&#10;再次相遇\" class=\"rvi__des2\"></p></a>";
        Video v=RecommendationFeeds.parse(html,feed("iqiyi:short_hot")).get(0);assertEquals("短剧&第二季",v.title);assertEquals("2026",v.year);assertEquals(Recommendations.SHORT,v.trendGroup);assertEquals("她说：\"你好\"。 再次相遇",v.description);assertTrue(v.poster.startsWith("https://"));
    }
    @Test public void biliDomesticRankDoesNotMasqueradeAsDoubanScore() throws Exception {
        String body="{\"code\":0,\"result\":{\"list\":[{\"season_id\":55,\"title\":\"国产动画\",\"cover\":\"http://i0.hdslb.com/a.jpg\",\"rank\":2,\"rating\":\"9.8分\",\"new_ep\":{\"index_show\":\"更新至第8话\"}}]}}";
        Video v=RecommendationFeeds.parse(body,feed("bilibili:4")).get(0);assertTrue(Recommendations.domestic(v));assertTrue(v.description.contains("第8话"));assertTrue(v.description.contains("站内评分 9.8"));assertEquals(0,v.trendScore,0);assertTrue(v.credit.contains("国创榜第 2"));
    }
    @Test public void mangoOnlyIncludesHotPlayingModulesAndCleansPromotionalSuffix() throws Exception {
        String body="{\"code\":200,\"data\":[{\"moduleTitle\":\"即将上线\",\"DSLList\":[{\"data\":{\"items\":[{\"jumpId\":\"1\",\"name\":\"预告\",\"videoUrl\":\"https://www.mgtv.com/b/1/\"}]}}]},{\"moduleTitle\":\"正在热播\",\"DSLList\":[{\"data\":{\"items\":[{\"jumpId\":\"2\",\"name\":\"功夫女足⚽️欢乐\",\"videoUrl\":\"https://www.mgtv.com/b/2/\",\"imageV\":\"https://img.hitv.com/a.jpg\",\"subName\":\"绿茵场上的新挑战\"},{\"jumpId\":\"2\",\"name\":\"重复\",\"videoUrl\":\"https://www.mgtv.com/b/2/\"},{\"jumpId\":\"3\",\"name\":\"广告\",\"videoUrl\":\"https://example.test/ad\"}]}}]}]}";
        List<Video> list=RecommendationFeeds.parse(body,feed("mango:short_hot"));assertEquals(1,list.size());assertEquals("功夫女足",list.get(0).title);assertEquals("芒果TV · 正在热播",list.get(0).credit);assertEquals(Recommendations.SHORT,list.get(0).trendGroup);
    }
    @Test public void unavailableFeedsFailRatherThanManufacturingARecommendation() throws Exception {
        for(String id:new String[]{"hongguo:short_hot","iqiyi:short_hot","tencent:tv_hot","mango:tv_hot","bilibili:4"})try{RecommendationFeeds.parse("{}",feed(id));fail(id);}catch(Exception expected){}
        assertEquals(Arrays.asList("红果短剧","爱奇艺","芒果TV"),RecommendationFeeds.providers(Recommendations.SHORT));
    }
    @Test public void combiningDeduplicatesEnrichesAndPreservesRemakesAndSourceObjects(){
        Video a=item("国产剧","","腾讯视频",1),b=item("国产剧","2026","豆瓣",3),old=item("国产剧","2020","爱奇艺",1),other=item("另一部","2026","豆瓣",4);
        b.region="中国大陆";b.description="完整剧情简介";b.trendScore=8.2;
        List<Video> combined=Recommendations.combine(Arrays.asList(a,b,other,old),"");assertEquals(3,combined.size());
        Video first=combined.get(0);assertEquals("2026",first.year);assertEquals("完整剧情简介",first.description);assertTrue(first.credit.contains("腾讯视频"));assertTrue(first.credit.contains("豆瓣"));assertEquals("",a.year);assertEquals("",a.description);
        assertEquals(2,Recommendations.combine(Arrays.asList(a,b,other,old),"豆瓣").size());assertTrue(combined.stream().anyMatch(v->v.year.equals("2020")));
    }
    @Test public void hongguoReadsStreamedRankMetadataWithoutTurningWebPagesIntoStreams() throws Exception {
        org.json.JSONObject item=new org.json.JSONObject().put("seriesId","1234567890123456789").put("rank",2).put("title","短剧第二季")
            .put("cover","https://p6-novel.byteimg.com/cover.image").put("description","她说：\"再见\"，而后重逢。").put("episodeCount",86).put("scoreText","评分9.1");
        org.json.JSONArray args=new org.json.JSONArray().put("rank_hot-real-drama/page").put("content").put(new org.json.JSONObject().put("isSuccess",true).put("rankList",new org.json.JSONArray().put(item)));
        String encoded=args.toString().replace("&","&amp;").replace("\"","&quot;").replace("'","&#39;");
        String html="<script data-script-src=\"modern-run-router-data-fn\" data-fn-args='"+encoded+"'>runRouterDataFn();</script>";
        Video v=RecommendationFeeds.parse(html,feed("hongguo:short_hot")).get(0);
        assertEquals("红果短剧",v.trendProvider);assertEquals("短剧第二季",v.title);assertTrue(v.description.startsWith("全86集 · 她说：\"再见\""));assertTrue(v.credit.contains("第 2 名"));assertTrue(v.streams.isEmpty());assertEquals(0,v.trendScore,0);assertEquals(Recommendations.SHORT,v.trendGroup);
    }
    @Test public void hongguoAlsoSupportsInitialPageDataAndKeepsTopicFallback() throws Exception {
        String html="<script>_ROUTER_DATA = {\"loaderData\":{\"rank_hot-real-drama/page\":{\"content\":{\"isSuccess\":true,\"rankList\":[{\"seriesId\":\"123\",\"title\":\"家中故事\",\"tags\":[\"家庭\",\"喜剧\"],\"episodeCount\":30}]}}}};notExecuted();</script>";
        Video v=RecommendationFeeds.parse(html,feed("hongguo:short_hot")).get(0);assertEquals("全30集 · 家庭 · 喜剧",v.description);assertTrue(v.page.startsWith("https://hongguoduanju.com/detail?"));
    }
    @Test public void artworkOriginsOnlyMatchActualPlatformDomains(){
        assertEquals("https://www.bilibili.com/",RecommendationFeeds.posterReferer("https://i0.hdslb.com/a.jpg"));assertEquals("https://www.iqiyi.com/",RecommendationFeeds.posterReferer("https://pic1.iqiyipic.com/a.jpg"));
        assertEquals("",RecommendationFeeds.posterReferer("https://hdslb.com.evil.test/a.jpg"));assertEquals("",RecommendationFeeds.posterReferer("invalid"));
    }
    private Video item(String title,String year,String provider,int rank){Video v=new Video();v.id=provider+year+rank;v.title=title;v.year=year;v.trendProvider=provider;v.trendFeed=provider;v.trendGroup=Recommendations.TV;v.trendRank=rank;v.credit=provider+" · 第 "+rank+" 名";return v;}
}
