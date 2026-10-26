package io.qingying.app;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import org.json.JSONObject;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public final class Repository extends SQLiteOpenHelper {
    public Repository(Context context) { super(context, "catalog.db", null, 4); }
    @Override public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE videos(id TEXT PRIMARY KEY, source TEXT NOT NULL, body TEXT NOT NULL)");
        db.execSQL("CREATE TABLE sources(url TEXT PRIMARY KEY, name TEXT NOT NULL, kind TEXT NOT NULL, synced INTEGER DEFAULT 0, error TEXT DEFAULT '')");
        ContentValues v = new ContentValues();
        v.put("url", "https://bfzyapi.com/api.php/provide/vod/"); v.put("name", "暴风 · 公开采集接口"); v.put("kind", "cms"); db.insert("sources", null, v);
        v.put("url", "https://cj.ffzyapi.com/api.php/provide/vod/"); v.put("name", "非凡 · 公开采集接口"); db.insert("sources", null, v);
        createRecommendations(db);
        createHistory(db);
        add4kSource(db);
        addPlaybackSources(db);
    }
    private void createRecommendations(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE IF NOT EXISTS recommendations(id TEXT PRIMARY KEY, group_name TEXT NOT NULL, rank INTEGER NOT NULL, body TEXT NOT NULL)");
        db.execSQL("CREATE TABLE IF NOT EXISTS recommendation_state(group_name TEXT PRIMARY KEY, fetched INTEGER DEFAULT 0, error TEXT DEFAULT '')");
    }
    private void createHistory(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE IF NOT EXISTS search_history(query TEXT PRIMARY KEY, searched INTEGER NOT NULL)");
    }
    private void add4kSource(SQLiteDatabase db) {
        ContentValues v=new ContentValues();v.put("url","https://caiji.kuaichezy.org/api.php/provide/vod/?t=62");
        v.put("name","快车 · 4K 专区");v.put("kind","cms");db.insertWithOnConflict("sources",null,v,SQLiteDatabase.CONFLICT_IGNORE);
    }
    private void addPlaybackSources(SQLiteDatabase db){
        String[][] sources={{Hongguo.SOURCE,"红果 · 官方网页播放","hongguo"},{"https://360zyzz.com/api.php/provide/vod/","360 · 公开采集接口","cms"},{"https://hhzyapi.com/api.php/provide/vod/","豪华 · 公开采集接口","cms"}};
        for(String[] source:sources){ContentValues values=new ContentValues();values.put("url",source[0]);values.put("name",source[1]);values.put("kind",source[2]);db.insertWithOnConflict("sources",null,values,SQLiteDatabase.CONFLICT_IGNORE);}
    }
    @Override public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        if(oldVersion<2) { createRecommendations(db); add4kSource(db); }
        if(oldVersion<3)createHistory(db);
        if(oldVersion<4){addPlaybackSources(db);removeCachedWebPlayers(db);}
    }
    private void removeCachedWebPlayers(SQLiteDatabase db){
        try(Cursor rows=db.rawQuery("SELECT videos.id,videos.body FROM videos JOIN sources ON videos.source=sources.url WHERE sources.kind='cms'",null)){
            while(rows.moveToNext())try{Video video=Video.from(new JSONObject(rows.getString(1)));int before=video.streams.size();video.streams.removeIf(stream->!Providers.directMedia(stream.url));
                if(before!=video.streams.size()){ContentValues values=new ContentValues();values.put("body",video.json().toString());db.update("videos",values,"id=?",new String[]{rows.getString(0)});}
            }catch(Exception ignored){}
        }
    }
    public synchronized Video get(String id) {
        try (Cursor c = getReadableDatabase().query("videos", null, "id=?", new String[]{id}, null, null, null)) {
            if (c.moveToFirst()) return Video.from(new JSONObject(c.getString(c.getColumnIndexOrThrow("body"))));
        } catch (Exception ignored) { }
        return null;
    }
    public synchronized List<Video> videos() {
        List<Video> all = new ArrayList<>();
        try (Cursor c = getReadableDatabase().query("videos", null, null, null, null, null, null)) {
            while (c.moveToNext()) {
                try { all.add(Video.from(new JSONObject(c.getString(c.getColumnIndexOrThrow("body"))))); }
                catch (Exception ignored) { }
            }
        }
        all.sort(Comparator.comparing((Video v) -> v.missing())
            .thenComparing((Video v) -> v.updated.isEmpty() ? v.published : v.updated, Comparator.reverseOrder())
            .thenComparing(Comparator.comparingLong((Video v) -> v.discovered).reversed()));
        return all;
    }
    private void save(Video video) {
        try {
            ContentValues v = new ContentValues(); v.put("id", video.id); v.put("source", video.source); v.put("body", video.json().toString());
            getWritableDatabase().insertWithOnConflict("videos", null, v, SQLiteDatabase.CONFLICT_REPLACE);
        } catch (Exception e) { throw new IllegalStateException(e); }
    }
    public synchronized void merge(List<Video> videos) {
        SQLiteDatabase db = getWritableDatabase(); db.beginTransaction();
        try {
            for (Video fresh : videos) {
                if (!fresh.source.equals("builtin") && !fresh.isCloud() && !hasSource(fresh.source)) continue;
                Video old = get(fresh.id);
                fresh.discovered = old == null ? System.currentTimeMillis() : old.discovered;
                if (old != null) {
                    fresh.favorite = old.favorite; fresh.position = old.position; fresh.duration = old.duration; fresh.lastStream = old.lastStream; fresh.lastEpisode = old.lastEpisode; fresh.lastPlayed = old.lastPlayed; fresh.completed = old.completed; fresh.legacyHistory = old.legacyHistory;
                    if(fresh.isHongguo()&&old.isHongguo()&&fresh.streams.size()==1&&fresh.streams.get(0).episode.isEmpty()){
                        fresh.streams.clear();fresh.streams.addAll(old.streams);
                    }
                    for (Video.Stream s : fresh.streams) for (Video.Stream prev : old.streams) if (s.url.equals(prev.url)) {
                        s.state = prev.state; s.checked = prev.checked; s.height = Math.max(s.height, prev.height); s.width = Math.max(s.width, prev.width); break;
                    }
                }
                save(fresh);
            }
            List<Video> all = videos();
            for (int i = 500; i < all.size(); i++) if (!all.get(i).favorite && all.get(i).position == 0 && all.get(i).lastPlayed == 0 && !all.get(i).legacyHistory)
                db.delete("videos", "id=?", new String[]{all.get(i).id});
            db.setTransactionSuccessful();
        } finally { db.endTransaction(); }
    }
    public synchronized void checked(String id, Video.Stream checked) {
        Video video = get(id); if (video == null) return;
        if(video.isCloud()||video.isHongguo()) {
            for(Video.Stream s:video.streams)if(video.isCloud()||s.episode.equals(checked.episode)){s.width=Math.max(s.width,checked.width);s.height=Math.max(s.height,checked.height);s.state=checked.state;s.checked=checked.checked;}
            save(video);return;
        }
        for (Video.Stream s : video.streams) if (s.url.equals(checked.url)) {
            s.state = checked.state; s.checked = checked.checked; s.height = Math.max(s.height, checked.height); s.width = Math.max(s.width, checked.width);
        }
        save(video);
    }
    public synchronized void progress(String id, String stream, String episode, long position, long duration) {
        progress(id,stream,episode,position,duration,false);
    }
    public synchronized void progress(String id,String stream,String episode,long position,long duration,boolean completed) {
        Video v=get(id);if(v==null)return;
        v.position=Math.max(0,position);v.duration=Math.max(0,duration);v.lastStream=v.isCloud()?"":v.isHongguo()?Hongguo.savedPlayerUrl(stream,v.hongguoSeriesId):stream;v.lastEpisode=episode;v.completed=completed;save(v);
    }
    public synchronized void played(String id) {Video v=get(id);if(v!=null){v.lastPlayed=System.currentTimeMillis();save(v);}}
    public synchronized List<Video> playbackHistory() {
        List<Video> items=videos();items.removeIf(v->v.lastPlayed==0&&!v.legacyHistory);
        // Old versions saved progress without a watch timestamp. Keep those entries, without inventing a date.
        items.sort(Comparator.comparingLong((Video v)->v.lastPlayed).reversed());return items;
    }
    public synchronized void removePlaybackHistory(String id) {
        Video v=get(id);if(v==null)return;v.lastPlayed=0;v.position=0;v.duration=0;v.lastStream="";v.lastEpisode="";v.completed=false;v.legacyHistory=false;save(v);
    }
    public synchronized void clearPlaybackHistory() {
        SQLiteDatabase db=getWritableDatabase();db.beginTransaction();
        try{for(Video v:playbackHistory())removePlaybackHistory(v.id);db.setTransactionSuccessful();}finally{db.endTransaction();}
    }
    public synchronized void rememberSearch(String query) {
        String term=query.trim().replaceAll("\\s+"," ");if(term.isEmpty())return;if(term.length()>100)term=term.substring(0,100);
        SQLiteDatabase db=getWritableDatabase();ContentValues values=new ContentValues();values.put("query",term);values.put("searched",System.currentTimeMillis());
        db.insertWithOnConflict("search_history",null,values,SQLiteDatabase.CONFLICT_REPLACE);
        db.execSQL("DELETE FROM search_history WHERE query NOT IN (SELECT query FROM search_history ORDER BY searched DESC, rowid DESC LIMIT 30)");
    }
    public synchronized List<String> searchHistory() {
        List<String> items=new ArrayList<>();try(Cursor c=getReadableDatabase().rawQuery("SELECT query FROM search_history ORDER BY searched DESC, rowid DESC",null)){while(c.moveToNext())items.add(c.getString(0));}return items;
    }
    public synchronized void removeSearch(String query){getWritableDatabase().delete("search_history","query=?",new String[]{query});}
    public synchronized void clearSearchHistory(){getWritableDatabase().delete("search_history",null,null);}
    public synchronized void favorite(String id) { Video v = get(id); if (v != null) { v.favorite = !v.favorite; save(v); } }
    public synchronized List<Source> sources() {
        List<Source> list = new ArrayList<>();
        try (Cursor c = getReadableDatabase().rawQuery("SELECT url,name,kind,synced,error FROM sources ORDER BY rowid", null)) {
            while (c.moveToNext()) list.add(new Source(c.getString(0), c.getString(1), c.getString(2), c.getLong(3), c.getString(4)));
        }
        return list;
    }
    public synchronized boolean hasSource(String url) { for (Source s : sources()) if (s.url.equals(url)) return true; return false; }
    public synchronized void addSource(String url, String name, String kind) {
        ContentValues v = new ContentValues(); v.put("url", url); v.put("name", name); v.put("kind", kind);
        getWritableDatabase().insertWithOnConflict("sources", null, v, SQLiteDatabase.CONFLICT_IGNORE);
    }
    public synchronized void sourceResult(String url, String error) {
        ContentValues v = new ContentValues(); v.put("error", error); if (error.isEmpty()) v.put("synced", System.currentTimeMillis());
        getWritableDatabase().update("sources", v, "url=?", new String[]{url});
    }
    public synchronized void removeSource(String url) {
        getWritableDatabase().delete("sources", "url=?", new String[]{url});
        getWritableDatabase().delete("videos", "source=?", new String[]{url});
    }
    public synchronized void recommendations(String group,List<Video> items,String error) {
        SQLiteDatabase db=getWritableDatabase();db.beginTransaction();
        try {
            if(error.isEmpty()&&!items.isEmpty()) {
                db.delete("recommendations","group_name=?",new String[]{group});
                for(Video item:items){ContentValues v=new ContentValues();v.put("id",item.id);v.put("group_name",group);v.put("rank",item.trendRank);v.put("body",item.json().toString());db.insertOrThrow("recommendations",null,v);}
                ContentValues state=new ContentValues();state.put("group_name",group);state.put("fetched",System.currentTimeMillis());state.put("error","");
                db.insertWithOnConflict("recommendation_state",null,state,SQLiteDatabase.CONFLICT_REPLACE);
            }else {
                db.execSQL("INSERT OR IGNORE INTO recommendation_state(group_name) VALUES(?)",new Object[]{group});
                ContentValues state=new ContentValues();state.put("error",error);db.update("recommendation_state",state,"group_name=?",new String[]{group});
            }
            db.setTransactionSuccessful();
        } catch(Exception e){throw new IllegalStateException(e);}finally{db.endTransaction();}
    }
    public synchronized List<Video> recommendations(String group) {
        List<Video> list=new ArrayList<>();
        try(Cursor c=getReadableDatabase().rawQuery("SELECT body FROM recommendations WHERE group_name=? ORDER BY rank",new String[]{group})) {
            while(c.moveToNext())try{list.add(Video.from(new JSONObject(c.getString(0))));}catch(Exception ignored){}
        }
        return list;
    }
    public synchronized long recommendationTime(String group) {
        try(Cursor c=getReadableDatabase().rawQuery("SELECT fetched FROM recommendation_state WHERE group_name=?",new String[]{group})){return c.moveToFirst()?c.getLong(0):0;}
    }
    public synchronized String recommendationError(String group) {
        try(Cursor c=getReadableDatabase().rawQuery("SELECT error FROM recommendation_state WHERE group_name=?",new String[]{group})){return c.moveToFirst()?c.getString(0):"";}
    }
    public static final class Source {
        public final String url, name, kind, error;
        public final long synced;
        Source(String url, String name, String kind, long synced, String error) {
            this.url=url; this.name=name; this.kind=kind; this.synced=synced; this.error=error;
        }
    }
}
