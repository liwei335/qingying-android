package io.qingying.app;

import android.app.Application;
import android.content.ContentValues;
import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import org.json.JSONObject;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import java.util.Arrays;
import java.util.Collections;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk=28,application=Application.class)
public class HistoryRepositoryTest {
    private Context context;private Repository repo;
    @Before public void create(){context=RuntimeEnvironment.getApplication();context.deleteDatabase("catalog.db");repo=new Repository(context);}
    @After public void close(){repo.close();context.deleteDatabase("catalog.db");}
    private Video video(String id){Video v=new Video();v.id=id;v.title=id;v.source="builtin";v.streams.add(new Video.Stream("https://example.test/"+id+".mp4","第1集",1080));return v;}
    @Test public void searchHistoryDeduplicatesCapsAndPersists(){
        repo.rememberSearch("   ");assertTrue(repo.searchHistory().isEmpty());
        for(int i=0;i<35;i++)repo.rememberSearch("片名"+i);assertEquals(30,repo.searchHistory().size());assertFalse(repo.searchHistory().contains("片名0"));
        repo.rememberSearch("  片名10  ");assertEquals("片名10",repo.searchHistory().get(0));assertEquals(30,repo.searchHistory().size());
        repo.close();repo=new Repository(context);assertEquals("片名10",repo.searchHistory().get(0));repo.removeSearch("片名10");assertEquals(29,repo.searchHistory().size());repo.clearSearchHistory();assertTrue(repo.searchHistory().isEmpty());
    }
    @Test public void onlyActualPlaybackCreatesHistoryAndCompletedItemsRemain(){
        Video v=video("watched");repo.merge(Collections.singletonList(v));repo.progress(v.id,v.streams.get(0).url,"第1集",0,100000);assertTrue(repo.playbackHistory().isEmpty());
        repo.played(v.id);repo.progress(v.id,v.streams.get(0).url,"第1集",0,100000,true);assertEquals(1,repo.playbackHistory().size());Video stored=repo.playbackHistory().get(0);assertTrue(stored.completed);assertTrue(stored.lastPlayed>0);
        repo.merge(Collections.singletonList(video(v.id)));assertTrue(repo.get(v.id).completed);assertEquals(stored.lastPlayed,repo.get(v.id).lastPlayed);
    }
    @Test public void historyUsesWatchTimeAndClearKeepsFavoritesAndSources(){
        Video a=video("older"),b=video("newer");a.lastPlayed=100;b.lastPlayed=200;a.favorite=true;a.position=20000;b.position=30000;
        repo.merge(Arrays.asList(a,b));assertEquals("newer",repo.playbackHistory().get(0).id);repo.removePlaybackHistory(b.id);assertEquals(1,repo.playbackHistory().size());
        repo.clearPlaybackHistory();assertTrue(repo.playbackHistory().isEmpty());Video saved=repo.get(a.id);assertTrue(saved.favorite);assertEquals(0,saved.position);assertEquals(1,saved.streams.size());assertFalse(repo.sources().isEmpty());
    }
    @Test public void versionTwoUpgradePreservesLegacyHistoryWithoutInventedWatchDates() throws Exception {
        repo.close();context.deleteDatabase("catalog.db");SQLiteDatabase db=context.openOrCreateDatabase("catalog.db",0,null);
        db.execSQL("CREATE TABLE videos(id TEXT PRIMARY KEY,source TEXT NOT NULL,body TEXT NOT NULL)");db.execSQL("CREATE TABLE sources(url TEXT PRIMARY KEY,name TEXT NOT NULL,kind TEXT NOT NULL,synced INTEGER DEFAULT 0,error TEXT DEFAULT '')");
        Video v=video("old");v.position=45000;v.lastEpisode="第3集";v.favorite=true;JSONObject body=v.json();body.remove("lastPlayed");body.remove("legacyHistory");body.remove("completed");
        ContentValues values=new ContentValues();values.put("id",v.id);values.put("source",v.source);values.put("body",body.toString());db.insertOrThrow("videos",null,values);db.setVersion(2);db.close();
        repo=new Repository(context);assertEquals(4,repo.getReadableDatabase().getVersion());assertEquals(1,repo.playbackHistory().size());assertEquals(0,repo.playbackHistory().get(0).lastPlayed);assertEquals(45000,repo.get(v.id).position);assertTrue(repo.get(v.id).favorite);
        repo.rememberSearch("旧记录仍可搜索");assertEquals(1,repo.searchHistory().size());repo.merge(Collections.singletonList(video(v.id)));assertEquals(1,repo.playbackHistory().size());
        repo.removePlaybackHistory(v.id);assertTrue(repo.playbackHistory().isEmpty());
    }
    @Test public void failedPlatformRetainsItsOwnCacheWhileAnotherPlatformUpdates(){
        Video a=video("a"),b=video("b");a.trendRank=1;b.trendRank=1;a.trendGroup=Recommendations.TV;b.trendGroup=Recommendations.TV;
        repo.recommendations("iqiyi:tv_hot",Collections.singletonList(a),"");long old=repo.recommendationTime("iqiyi:tv_hot");
        repo.recommendations("iqiyi:tv_hot",Collections.emptyList(),"超时");repo.recommendations("tencent:tv_hot",Collections.singletonList(b),"");
        assertEquals(old,repo.recommendationTime("iqiyi:tv_hot"));assertEquals("a",repo.recommendations("iqiyi:tv_hot").get(0).id);assertEquals("b",repo.recommendations("tencent:tv_hot").get(0).id);assertEquals("超时",repo.recommendationError("iqiyi:tv_hot"));
    }
}
