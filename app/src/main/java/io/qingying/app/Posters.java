package io.qingying.app;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Handler;
import android.os.Looper;
import android.util.LruCache;
import android.widget.ImageView;
import java.lang.ref.WeakReference;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

final class Posters {
    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    private static final ThreadPoolExecutor POOL = new ThreadPoolExecutor(3,3,30,TimeUnit.SECONDS,
        new ArrayBlockingQueue<>(40), new ThreadPoolExecutor.DiscardOldestPolicy());
    private static final LruCache<String, Bitmap> CACHE = new LruCache<>(12 * 1024 * 1024) {
        @Override protected int sizeOf(String key, Bitmap value) { return value.getByteCount(); }
    };
    static void load(ImageView view, String url) {
        view.setTag(url); view.setImageResource(R.drawable.ic_launcher);
        if (url.isEmpty()) return;
        Bitmap cached = CACHE.get(url); if (cached != null) { view.setImageBitmap(cached); return; }
        WeakReference<ImageView> ref = new WeakReference<>(view);
        POOL.execute(() -> {
            try {
                Net.Response r = Net.get(url, 1024 * 1024, false); if (r.code != 200) return;
                BitmapFactory.Options o = new BitmapFactory.Options(); o.inJustDecodeBounds = true;
                BitmapFactory.decodeByteArray(r.body,0,r.body.length,o);
                o.inSampleSize = 1;
                while (o.outWidth / o.inSampleSize > 600 || o.outHeight / o.inSampleSize > 900) o.inSampleSize *= 2;
                o.inJustDecodeBounds = false;
                Bitmap bitmap = BitmapFactory.decodeByteArray(r.body,0,r.body.length,o);
                if (bitmap == null) return; CACHE.put(url,bitmap);
                MAIN.post(() -> { ImageView v = ref.get(); if (v != null && url.equals(v.getTag())) v.setImageBitmap(bitmap); });
            } catch (Exception ignored) { }
        });
    }
}
