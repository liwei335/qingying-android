package io.qingying.app;

import android.app.Application;
import android.content.Context;
import android.content.SharedPreferences;
import androidx.work.Constraints;
import androidx.work.ExistingPeriodicWorkPolicy;
import androidx.work.ExistingWorkPolicy;
import androidx.work.NetworkType;
import androidx.work.OneTimeWorkRequest;
import androidx.work.PeriodicWorkRequest;
import androidx.work.WorkManager;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public final class QingYingApp extends Application {
    public final ExecutorService io = Executors.newSingleThreadExecutor();
    public final ExecutorService net = Executors.newFixedThreadPool(2);
    public Repository repo;
    @Override public void onCreate() {
        super.onCreate(); repo = new Repository(this);
        io.execute(() -> repo.merge(Providers.demo()));
        schedule(this);
    }
    public static QingYingApp app(Context context) { return (QingYingApp) context.getApplicationContext(); }
    public static SharedPreferences prefs(Context c) { return c.getSharedPreferences("settings", MODE_PRIVATE); }
    public static void schedule(Context c) {
        WorkManager wm = WorkManager.getInstance(c);
        if (!prefs(c).getBoolean("daily", true)) { wm.cancelUniqueWork("daily-catalog"); return; }
        Constraints constraints = new Constraints.Builder().setRequiredNetworkType(
            prefs(c).getBoolean("wifi", true) ? NetworkType.UNMETERED : NetworkType.CONNECTED).setRequiresBatteryNotLow(true).build();
        PeriodicWorkRequest work = new PeriodicWorkRequest.Builder(SyncWorker.class, 24, TimeUnit.HOURS)
            .setInitialDelay(24, TimeUnit.HOURS).setConstraints(constraints).addTag("catalog").build();
        wm.enqueueUniquePeriodicWork("daily-catalog", ExistingPeriodicWorkPolicy.UPDATE, work);
    }
    public static void refresh(Context c) {
        OneTimeWorkRequest work = new OneTimeWorkRequest.Builder(SyncWorker.class)
            .setConstraints(new Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .addTag("catalog").build();
        WorkManager.getInstance(c).enqueueUniqueWork("manual-catalog", ExistingWorkPolicy.KEEP, work);
    }
}
