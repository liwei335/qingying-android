package io.qingying.app;

import android.app.Application;
import android.content.Context;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import java.util.Collections;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk=28,application=Application.class)
public class PlaybackSourceUpgradeTest {
    @Test public void upgradeAddsSourcesOnceAndPreservesHistoryWhileRemovingHtmlPlayers(){
        Context context=RuntimeEnvironment.getApplication();context.deleteDatabase("catalog.db");Repository repo=new Repository(context);
        String custom="https://custom.test/api.php/provide/vod/",mirror="https://360zyzz.com/api.php/provide/vod/";
        repo.addSource(custom,"自定义片源","cms");repo.removeSource(mirror);repo.addSource(mirror,"用户命名的360","cms");
        Video v=new Video();v.id="saved";v.source=custom;v.favorite=true;v.position=23000;v.lastEpisode="第1集";v.lastPlayed=100;
        v.streams.add(new Video.Stream("https://web.test/play/123","网页",0));Video.Stream media=new Video.Stream("https://cdn.test/1.m3u8","第1集",0);media.episode="第1集";v.streams.add(media);repo.merge(Collections.singletonList(v));
        repo.removeSource(Hongguo.SOURCE);repo.removeSource("https://hhzyapi.com/api.php/provide/vod/");repo.getWritableDatabase().setVersion(3);repo.close();
        repo=new Repository(context);Video saved=repo.get(v.id);assertEquals(4,repo.getReadableDatabase().getVersion());assertTrue(saved.favorite);assertEquals(23000,saved.position);assertEquals(100,saved.lastPlayed);assertEquals(1,saved.streams.size());assertTrue(saved.streams.get(0).url.endsWith("m3u8"));
        assertTrue(repo.hasSource(Hongguo.SOURCE));assertTrue(repo.hasSource("https://hhzyapi.com/api.php/provide/vod/"));assertEquals("用户命名的360",repo.sources().stream().filter(s->s.url.equals(mirror)).findFirst().get().name);
        repo.removeSource(Hongguo.SOURCE);repo.close();repo=new Repository(context);assertFalse(repo.hasSource(Hongguo.SOURCE));repo.close();context.deleteDatabase("catalog.db");
    }
    @Test public void metadataRefreshDoesNotEraseOfficialEpisodeDescriptorsAndResume(){
        Context context=RuntimeEnvironment.getApplication();context.deleteDatabase("catalog.db");Repository repo=new Repository(context);
        Video wanted=new Video();wanted.title="红果短剧";wanted.hongguoSeriesId="123";Video old=Hongguo.catalog(wanted);
        old.position=12000;old.lastEpisode="第2集";old.lastStream="https://hongguoduanju.com/player/123/102";old.favorite=true;old.lastPlayed=1000;
        repo.merge(Collections.singletonList(old));repo.merge(Collections.singletonList(Hongguo.catalog(wanted)));
        Video saved=repo.get(old.id);assertEquals(1,saved.streams.size());assertEquals("第2集",saved.lastEpisode);assertEquals(12000,saved.position);assertTrue(saved.favorite);assertEquals("https://hongguoduanju.com/player/123/102",Hongguo.playbackUrl(saved));
        repo.close();context.deleteDatabase("catalog.db");
    }
}
