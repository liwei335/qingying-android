package io.qingying.app;

import android.content.Context;
import androidx.annotation.NonNull;
import androidx.work.Data;
import androidx.work.Worker;
import androidx.work.WorkerParameters;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;

public final class SyncWorker extends Worker {
    private static final ReentrantLock LOCK = new ReentrantLock();
    public SyncWorker(@NonNull Context c, @NonNull WorkerParameters p) { super(c, p); }
    private void progress(String text) { setProgressAsync(new Data.Builder().putString("message", text).build()); }
    @NonNull @Override public Result doWork() {
        if (!LOCK.tryLock()) return Result.retry();
        ExecutorService pool = Executors.newFixedThreadPool(4);
        try {
            Repository repo = QingYingApp.app(getApplicationContext()).repo;
            List<Repository.Source> sources = repo.sources();
            int success = 0;
            long deadline = System.currentTimeMillis() + 180_000;
            for(String group:Recommendations.GROUPS) {
                if(isStopped())return Result.retry();progress("更新"+Recommendations.label(group));
                try{repo.recommendations(group,Recommendations.fetch(group),"");}
                catch(Exception e){repo.recommendations(group,java.util.Collections.emptyList(),"暂时无法更新，保留上次榜单");}
            }
            for (Repository.Source source : sources) {
                if (isStopped() || System.currentTimeMillis() > deadline) break;
                progress("更新片源：" + source.name);
                try {
                    List<Video> videos = Providers.fetch(source, "", 1);
                    if (videos.isEmpty()) throw new Exception("没有可直接播放的条目");
                    repo.merge(videos); repo.sourceResult(source.url, ""); success++;
                } catch (Exception e) { repo.sourceResult(source.url, e.getMessage() == null ? "连接失败" : e.getMessage()); }
            }
            if (isStopped()) return Result.retry();
            progress("检查播放地址与视频片段…");
            List<Callable<Void>> checks = new ArrayList<>();
            // At most 120 checks per run; oldest checked items rotate through subsequent runs.
            List<Video> videos = repo.videos();
            videos.sort(java.util.Comparator.comparingLong(Video::checked));
            for (Video v : videos) {
                if(v.isCloud())continue;
                int n = 0;
                for (Video.Stream s : v.streams) {
                    if (checks.size() >= 120 || n++ >= 2) break;
                    checks.add(() -> {
                        if (!isStopped() && System.currentTimeMillis() < deadline && !Thread.currentThread().isInterrupted()) {
                            s.state = Net.probe(s); s.checked = System.currentTimeMillis(); repo.checked(v.id, s);
                        }
                        return null;
                    });
                }
            }
            pool.invokeAll(checks, Math.max(1, deadline - System.currentTimeMillis()), TimeUnit.MILLISECONDS);
            if (isStopped()) return Result.retry();
            QingYingApp.prefs(getApplicationContext()).edit().putLong("checkedAt", System.currentTimeMillis())
                .putString("syncResult", "已更新 " + success + "/" + sources.size() + " 个片源；检查结果见影片卡片").apply();
            return success == 0 && !sources.isEmpty() ? Result.retry() : Result.success();
        } catch (Exception e) { return Result.retry(); }
        finally { pool.shutdownNow(); LOCK.unlock(); }
    }
}
