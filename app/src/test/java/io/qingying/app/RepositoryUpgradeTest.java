package io.qingying.app;

import android.app.Application;
import android.content.ContentValues;
import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import java.util.Collections;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk=28,application=Application.class)
public class RepositoryUpgradeTest {
    @Test public void upgradeKeepsSourcesFavoritesAndPlaybackPosition() throws Exception {
        Context context=RuntimeEnvironment.getApplication();context.deleteDatabase("catalog.db");
        SQLiteDatabase db=context.openOrCreateDatabase("catalog.db",0,null);
        db.execSQL("CREATE TABLE videos(id TEXT PRIMARY KEY, source TEXT NOT NULL, body TEXT NOT NULL)");
        db.execSQL("CREATE TABLE sources(url TEXT PRIMARY KEY, name TEXT NOT NULL, kind TEXT NOT NULL, synced INTEGER DEFAULT 0, error TEXT DEFAULT '')");
        db.execSQL("INSERT INTO sources(url,name,kind) VALUES('https://example.test/api','我的片源','cms')");
        Video original=new Video();original.id="favorite";original.source="https://example.test/api";original.favorite=true;original.position=42000;original.lastEpisode="第3集";
        original.streams.add(new Video.Stream("https://example.test/3.mp4","第3集",1080));
        ContentValues values=new ContentValues();values.put("id",original.id);values.put("source",original.source);values.put("body",original.json().toString());db.insertOrThrow("videos",null,values);db.setVersion(1);db.close();
        Repository repo=new Repository(context);Video restored=repo.get(original.id);
        assertNotNull(restored);assertTrue(restored.favorite);assertEquals(42000,restored.position);assertEquals("第3集",restored.lastEpisode);
        assertTrue(repo.hasSource("https://example.test/api"));assertTrue(repo.sources().stream().anyMatch(s->s.url.contains("t=62")));assertEquals(2,repo.getReadableDatabase().getVersion());repo.close();context.deleteDatabase("catalog.db");
    }
    @Test public void failedRankRefreshKeepsOldDataAndOriginalTimestamp() throws Exception {
        Context context=RuntimeEnvironment.getApplication();context.deleteDatabase("catalog.db");Repository repo=new Repository(context);
        Video v=new Video();v.id="trend:test";v.title="旧榜单";v.trendGroup=Recommendations.TV;v.trendRank=1;
        repo.recommendations(Recommendations.TV,Collections.singletonList(v),"");long timestamp=repo.recommendationTime(Recommendations.TV);
        repo.recommendations(Recommendations.TV,Collections.emptyList(),"网络不可用");
        assertEquals("旧榜单",repo.recommendations(Recommendations.TV).get(0).title);assertEquals(timestamp,repo.recommendationTime(Recommendations.TV));assertEquals("网络不可用",repo.recommendationError(Recommendations.TV));
        repo.close();context.deleteDatabase("catalog.db");
    }
}
