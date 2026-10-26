package io.qingying.app;

import org.junit.Test;
import static org.junit.Assert.*;

public class HongguoPlaybackTest {
    @Test public void oldRecommendationsProduceOfficialPlayerEntriesInsteadOfPreviewMp4(){
        Video old=new Video();old.title="旧推荐";old.page="https://hongguoduanju.com/detail?series_id=123";old.trendGroup=Recommendations.SHORT;
        Video nativeVideo=Hongguo.catalog(old);assertEquals("hongguo:123",nativeVideo.id);assertTrue(nativeVideo.isHongguo());assertFalse(nativeVideo.isRecommendation());assertEquals("",nativeVideo.streams.get(0).url);
        assertEquals("https://hongguoduanju.com/player/123",Hongguo.playbackUrl(nativeVideo));
        old.page="https://hongguoduanju.com.evil.test/detail?series_id=123";assertEquals("",Hongguo.seriesId(old));
    }
    @Test public void resumeOnlyUsesTheSameSeriesOfficialPageAndStripsQuery(){
        Video v=new Video();v.hongguoSeriesId="123";
        v.lastStream="https://hongguoduanju.com/player/123/102?tracking=1";assertEquals("https://hongguoduanju.com/player/123/102",Hongguo.playbackUrl(v));
        for(String url:new String[]{"https://hongguoduanju.com/player/456/102","https://evil.test/player/123/102","http://hongguoduanju.com/player/123/102","https://user@hongguoduanju.com/player/123/102","https://hongguoduanju.com/video/public/101/360p.mp4"}){v.lastStream=url;assertEquals("https://hongguoduanju.com/player/123",Hongguo.playbackUrl(v));}
    }
    @Test public void officialOriginsAndSeoPreviewsAreDistinguished(){
        assertTrue(Hongguo.officialUrl("https://hongguoduanju.com/player/123"));assertTrue(Hongguo.officialUrl("https://www.hongguoduanju.com/player/123"));assertFalse(Hongguo.officialUrl("https://hongguoduanju.com.evil.test/player/123"));
        assertTrue(Hongguo.preview("https://hongguoduanju.com/video/public/101/360p.mp4"));assertFalse(Hongguo.preview("blob:https://hongguoduanju.com/abc"));
    }
    @Test public void savedOfficialHistoryPreservesWebEpisodeUrlNotTemporaryMedia() throws Exception {
        Video template=new Video();template.hongguoSeriesId="123";Video v=Hongguo.catalog(template);v.lastStream="https://hongguoduanju.com/player/123/102";v.lastEpisode="第2集";v.position=12000;
        Video saved=Video.from(v.json());assertEquals(v.lastStream,saved.lastStream);assertEquals(v.position,saved.position);
        v.lastStream="https://hongguoduanju.com/video/public/101/360p.mp4?token=test";assertEquals("",Video.from(v.json()).lastStream);
    }
    @Test public void officialIdentitySurvivesCrossPlatformDeduplication(){
        Video a=new Video(),b=new Video();a.id="a";b.id="b";a.title=b.title="同名短剧";a.trendFeed="iqiyi";b.trendFeed="hongguo";a.credit="爱奇艺";b.credit="红果";b.page="https://hongguoduanju.com/detail?series_id=123";
        Video merged=Recommendations.combine(java.util.Arrays.asList(a,b),"").get(0);assertEquals("123",merged.hongguoSeriesId);
    }
    @Test public void cmsDiscardsWebPlayerWrappersAndKeepsMediaWithSignedQueries() throws Exception {
        String body="{\"list\":[{\"vod_id\":1,\"vod_name\":\"短剧\",\"vod_play_from\":\"hhyun$$$hhm3u8\",\"vod_play_url\":\"第1集$https://web.test/play/123?url=video.m3u8$$$第1集$https://cdn.test/video.m3u8?token=abc\"}]}";
        Video v=Providers.cms(body,"source","provider").get(0);assertEquals(1,v.streams.size());assertTrue(v.streams.get(0).url.startsWith("https://cdn.test/"));
    }
}
