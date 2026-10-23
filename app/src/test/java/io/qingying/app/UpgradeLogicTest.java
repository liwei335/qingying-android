package io.qingying.app;

import org.json.JSONObject;
import org.junit.Test;
import java.util.List;
import static org.junit.Assert.*;

public class UpgradeLogicTest {
    @Test public void holdRestoresOriginalSpeedExactlyOnce(){
        HoldSpeed hold=new HoldSpeed();assertEquals(2f,hold.begin(1.25f),0);assertEquals(2f,hold.begin(2f),0);
        assertEquals(1.25f,hold.end(2f),0);assertFalse(hold.active());assertEquals(.75f,hold.end(.75f),0);
    }
    @Test public void cookieNeverMatchesAnExternalRedirectOrLookalike(){
        assertTrue(QuarkProtocol.credentialHost("https://drive.quark.cn/1/clouddrive/file"));assertTrue(QuarkProtocol.credentialHost("https://pdds-cdn.uc.cn/video"));
        for(String url:new String[]{"https://quark.cn.evil.example/x","https://evilquark.cn/x","http://pan.quark.cn/x","https://pan.quark.cn:8443/x","https://user@pan.quark.cn/x","https://storage.example/x"})assertFalse(url,QuarkProtocol.credentialHost(url));
    }
    @Test public void cloudTokensNeverEnterPersistentVideoJson() throws Exception {
        Video v=new Video();v.cloudFileId="file-123";v.source="quark";v.lastStream="https://pan.quark.cn/video?secret=token";
        Video.Stream s=new Video.Stream(v.lastStream,"原画",2160);s.width=3840;v.streams.add(s);v.lastEpisode="original";v.position=1234;
        String stored=v.json().toString();assertFalse(stored.contains("secret"));assertFalse(stored.contains("token"));
        Video restored=Video.from(new JSONObject(stored));assertTrue(restored.is4k());assertEquals("",restored.streams.get(0).url);assertEquals("original",restored.lastEpisode);assertEquals(1234,restored.position);
    }
    @Test public void recommendationsKeepRealProviderOrderAndRemainSeparate() throws Exception {
        String body="{\"subject_collection_items\":[{\"id\":\"a\",\"title\":\"A\",\"year\":\"2025\",\"rating\":{\"value\":7.5}},{\"id\":\"b\",\"title\":\"B\",\"year\":\"2026\"}]}";
        List<Video> tv=Recommendations.parse(body,Recommendations.TV),anime=Recommendations.parse(body,Recommendations.ANIME);
        assertEquals("A",tv.get(0).title);assertEquals(1,tv.get(0).trendRank);assertEquals(2,tv.get(1).trendRank);
        assertTrue(tv.get(0).streams.isEmpty());assertFalse(tv.get(0).id.equals(anime.get(0).id));assertEquals("",tv.get(0).updated);
    }
    @Test public void resourceMatchingDoesNotMixDifferentSeasonsOrRemakes(){
        Video a=new Video(),b=new Video();a.title="剧集：第二季";b.title="剧集 第二季";assertTrue(CatalogLogic.sameTitle(a,b));
        b.title="剧集第三季";assertFalse(CatalogLogic.sameTitle(a,b));b.title=a.title;a.year="2020";b.year="2026";assertFalse(CatalogLogic.sameTitle(a,b));
        assertEquals(CatalogLogic.episodeKey("第01集"),CatalogLogic.episodeKey("第1集"));
    }
    @Test public void smallAndLargeWindowsGetUsableLayout(){
        assertEquals(1,CatalogLogic.columns(280));assertEquals(2,CatalogLogic.columns(700));assertEquals(4,CatalogLogic.columns(1600));
        assertTrue(CatalogLogic.playerHeight(1080,600,16f/9)<=342);assertTrue(CatalogLogic.playerHeight(700,1200,16f/9)<=394);
    }
    @Test public void quarkDirectoryAndTranscodeParsingRespectRealDimensions() throws Exception {
        List<QuarkProtocol.Entry> entries=QuarkProtocol.entries(new JSONObject("{\"data\":{\"list\":[{\"fid\":\"d\",\"file\":false,\"file_name\":\"电影\"},{\"fid\":\"v\",\"file\":true,\"category\":1,\"file_name\":\"film.mkv\"},{\"fid\":\"t\",\"file\":true,\"file_name\":\"notes.txt\"}]}}"));
        assertEquals(2,entries.size());assertTrue(entries.get(0).folder);assertTrue(entries.get(1).video);
        List<Video.Stream> streams=QuarkProtocol.transcodes(new JSONObject("{\"data\":{\"video_list\":[{\"resolution\":\"4k\",\"video_info\":{\"url\":\"https://video.quark.cn/x.m3u8\",\"width\":3840,\"height\":1636,\"format\":\"m3u8\"}},{\"resolution\":\"4k\",\"video_info\":{\"url\":\"\"}}]}}"));
        assertEquals(1,streams.size());assertEquals(1636,streams.get(0).height);assertEquals("application/x-mpegURL",streams.get(0).mimeType);
    }
    @Test public void movieQualityVariantsKeepSameTimeline() throws Exception {
        Video v=Providers.cms("{\"list\":[{\"vod_id\":1,\"vod_name\":\"电影\",\"type_name\":\"4K电影\",\"vod_play_url\":\"HD$https://example.test/hd.m3u8#4K$https://example.test/4k.m3u8\"}]}","source","name").get(0);
        assertEquals(v.streams.get(0).episode,v.streams.get(1).episode);assertEquals(1,CatalogLogic.resumeIndex(v));assertFalse(v.is4k());assertFalse(v.qualityHint.isEmpty());
    }
}
