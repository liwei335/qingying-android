package io.qingying.app;

import org.junit.Test;
import java.nio.charset.StandardCharsets;
import java.util.List;
import static org.junit.Assert.*;

public class CatalogTest {
    @Test public void jsonIgnoresUnusableUrlsAndKeepsResolution() throws Exception {
        List<Video> v=FeedParser.parse("{\"videos\":[{\"title\":\"Test\",\"published\":\"2026-10-23\",\"streams\":[{\"url\":\"https://example.com/v.mp4\",\"height\":2160},{\"url\":\"javascript:alert(1)\"}]}]}","s","https://example.com/feed");
        assertEquals(1,v.size());assertEquals(1,v.get(0).streams.size());assertTrue(v.get(0).is4k());assertEquals("2026-10-23T00:00:00Z",v.get(0).published);
    }
    @Test public void m3uResolvesRelativeUrlsAndQuotedCommas() throws Exception {
        List<Video> v=FeedParser.parse("#EXTM3U\n#EXTINF:-1 tvg-logo=\"https://example.com/a,b.jpg\",测试节目\nv.mp4\n","s","https://example.com/path/list.m3u");
        assertEquals("测试节目",v.get(0).title);assertEquals("https://example.com/path/v.mp4",v.get(0).streams.get(0).url);assertFalse(v.get(0).is4k());
    }
    @Test public void hlsIsNotImportedAsSeparateSegments() throws Exception {
        List<Video> v=FeedParser.parse("#EXTM3U\n#EXT-X-TARGETDURATION:10\n#EXTINF:10,\n1.ts\n#EXTINF:10,\n2.ts", "s","https://example.com/movie.m3u8");
        assertEquals(1,v.size());assertEquals("https://example.com/movie.m3u8",v.get(0).streams.get(0).url);
    }
    @Test public void timestampsAndSchemesAreValidated(){
        assertEquals("",FeedParser.date("yesterday"));assertEquals("",FeedParser.https("http://example.com"));assertEquals("",FeedParser.https("https://user:pass@example.com"));
        assertEquals("2026-10-22T16:00:00Z",FeedParser.date("2026-10-23T00:00:00+08:00"));
    }
    @Test public void failuresDoNotEraseWorkingAlternative(){
        Video v=new Video();Video.Stream a=new Video.Stream("a","a",0),b=new Video.Stream("b","b",0);v.streams.add(a);v.streams.add(b);
        a.state="missing";assertFalse(v.missing());b.state="ok";assertTrue(v.reachable());assertFalse(v.missing());b.state="missing";assertTrue(v.missing());
    }
    @Test public void classifierRejectsHtmlAndDistinguishesTransientErrors(){
        assertEquals("temporary",Net.classify(response(200,"<!doctype html><html>blocked</html>","text/html")));
        assertEquals("temporary",Net.classify(response(403,"","")));assertEquals("temporary",Net.classify(response(503,"","")));
        assertEquals("missing",Net.classify(response(410,"","")));assertEquals("ok",Net.classify(response(200,"#EXTM3U\n#EXTINF:10,\n1.ts","text/plain")));
    }
    @Test public void cmsKeepsEpisodesSeparateAndDoesNotInvent4k() throws Exception {
        List<Video> list=Providers.cms("{\"list\":[{\"vod_id\":1,\"vod_name\":\"4K剧集\",\"vod_time\":\"2026-10-23 08:00:00\",\"vod_play_from\":\"a$$$b\",\"vod_play_url\":\"第1集$https://example.com/1.m3u8#第2集$https://example.com/2.m3u8$$$第1集$https://other.test/1.mp4\"}]}","source","name");
        assertEquals(3,list.get(0).streams.size());assertEquals("第2集",list.get(0).streams.get(1).episode);assertFalse(list.get(0).is4k());
        assertEquals("2026-10-23T00:00:00Z",list.get(0).updated);assertEquals("",list.get(0).published);
    }
    @Test public void storedVideoRetainsProgressAndStream() throws Exception {
        Video v=new Video();v.lastStream="https://example.com/2";v.position=12000;v.favorite=true;Video.Stream s=new Video.Stream(v.lastStream,"2",1080);s.episode="第2集";v.streams.add(s);
        Video copy=Video.from(v.json());assertEquals(v.lastStream,copy.lastStream);assertEquals(12000,copy.position);assertTrue(copy.favorite);assertEquals("第2集",copy.streams.get(0).episode);
    }
    @Test public void cinemaAspectRatioStillCountsAs4k(){
        Video v=new Video();Video.Stream s=new Video.Stream("https://example.com/film.mpd","4K",1636);s.width=3840;v.streams.add(s);assertTrue(v.is4k());
    }
    private Net.Response response(int status,String body,String type){return new Net.Response(status,body.getBytes(StandardCharsets.UTF_8),"https://example.com",type);}
}
