package io.qingying.app;

import android.app.Application;
import android.view.View;
import android.webkit.WebChromeClient;
import android.webkit.WebView;
import android.widget.FrameLayout;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.util.ReflectionHelpers;
import static org.junit.Assert.*;
import static org.robolectric.Shadows.shadowOf;

@RunWith(RobolectricTestRunner.class)
@Config(sdk=28,application=Application.class)
public class HongguoActivityTest {
    @Test public void officialWebViewLoadsSavedEpisodeAndSupportsFullscreenWithoutNativeBridge(){
        HongguoActivity activity=Robolectric.buildActivity(HongguoActivity.class).get();activity.setTheme(R.style.AppTheme);
        Video wanted=new Video();wanted.hongguoSeriesId="123";Video video=Hongguo.catalog(wanted);video.lastStream="https://hongguoduanju.com/player/123/102";
        ReflectionHelpers.setField(activity,"video",video);ReflectionHelpers.callInstanceMethod(activity,"build");
        WebView web=ReflectionHelpers.getField(activity,"web");assertEquals(video.lastStream,shadowOf(web).getLastLoadedUrl());assertTrue(web.getSettings().getJavaScriptEnabled());assertTrue(web.getSettings().getDomStorageEnabled());assertFalse(web.getSettings().getAllowFileAccess());assertFalse(web.getSettings().getAllowContentAccess());
        FrameLayout host=ReflectionHelpers.getField(activity,"host");View root=ReflectionHelpers.getField(activity,"root");WebChromeClient chrome=web.getWebChromeClient();int[] hidden={0};
        chrome.onShowCustomView(new View(activity),()->hidden[0]++);assertEquals(View.GONE,root.getVisibility());assertEquals(2,host.getChildCount());
        chrome.onHideCustomView();assertEquals(View.VISIBLE,root.getVisibility());assertEquals(1,host.getChildCount());assertEquals(1,hidden[0]);web.destroy();activity.finish();
    }
}
