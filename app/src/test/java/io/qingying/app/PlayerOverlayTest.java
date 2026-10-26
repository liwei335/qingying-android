package io.qingying.app;

import android.app.Application;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.content.res.Configuration;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.media3.ui.AspectRatioFrameLayout;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.LooperMode;
import org.robolectric.util.ReflectionHelpers;
import java.time.Duration;
import static org.junit.Assert.*;
import static org.robolectric.Shadows.shadowOf;

/** Exercises the real player view tree without fetching or decoding an external movie. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk=28,application=Application.class,qualifiers="mdpi")
@LooperMode(LooperMode.Mode.PAUSED)
@androidx.annotation.OptIn(markerClass=androidx.media3.common.util.UnstableApi.class)
public class PlayerOverlayTest {
    private PlayerActivity activity;private ViewGroup root;private HoldPlayerView video;
    @Before public void create(){
        activity=Robolectric.buildActivity(PlayerActivity.class).get();activity.setTheme(R.style.AppTheme);
        Video item=new Video();item.title="测试影片";item.streams.add(new Video.Stream("https://example.test/video.mp4","线路一",0));
        ReflectionHelpers.setField(activity,"video",item);ReflectionHelpers.setField(activity,"started",true);
        ReflectionHelpers.callInstanceMethod(activity,"build");
        root=(ViewGroup)((ViewGroup)activity.findViewById(android.R.id.content)).getChildAt(0);video=ReflectionHelpers.getField(activity,"playerView");layout(400,800);
    }
    private void layout(int width,int height){root.measure(View.MeasureSpec.makeMeasureSpec(width,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(height,View.MeasureSpec.EXACTLY));root.layout(0,0,width,height);}
    private View field(String name){return ReflectionHelpers.getField(activity,name);}
    private void tap(){for(int action:new int[]{MotionEvent.ACTION_DOWN,MotionEvent.ACTION_UP}){MotionEvent e=MotionEvent.obtain(SystemClock.uptimeMillis(),SystemClock.uptimeMillis(),action,root.getWidth()/2f,root.getHeight()/2f,0);root.dispatchTouchEvent(e);e.recycle();}}
    @Test public void overlayVisibilityNeverShrinksVideoInEitherOrientation(){
        assertEquals(400,video.getWidth());assertEquals(800,video.getHeight());assertEquals(AspectRatioFrameLayout.RESIZE_MODE_ZOOM,video.getResizeMode());
        tap();assertEquals(View.GONE,field("topBar").getVisibility());layout(400,800);assertEquals(800,video.getHeight());
        tap();assertEquals(View.VISIBLE,field("bottomBar").getVisibility());
        Configuration config=new Configuration(activity.getResources().getConfiguration());config.orientation=Configuration.ORIENTATION_LANDSCAPE;activity.getResources().updateConfiguration(config,activity.getResources().getDisplayMetrics());activity.onConfigurationChanged(config);layout(800,400);
        assertSame(video,ReflectionHelpers.getField(activity,"playerView"));assertEquals(800,video.getWidth());assertEquals(400,video.getHeight());assertEquals("竖屏",((TextView)field("orientationButton")).getText().toString());
        assertEquals(2,root.getChildCount());
    }
    @Test public void autoHideAndLockedTapOnlyRevealUnlockButtons(){
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(3600));assertEquals(View.GONE,field("bottomBar").getVisibility());
        tap();field("lockLeft").performClick();assertTrue(video.isLocked());assertEquals(View.GONE,field("topBar").getVisibility());assertEquals(View.GONE,field("bottomBar").getVisibility());
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(3600));assertEquals(View.GONE,field("lockRight").getVisibility());
        tap();assertEquals(View.VISIBLE,field("lockRight").getVisibility());assertEquals(View.GONE,field("bottomBar").getVisibility());
        field("lockRight").performClick();assertFalse(video.isLocked());assertEquals(View.VISIBLE,field("bottomBar").getVisibility());
    }
    @Test public void resizeChoicesApplyAndPersist(){
        int[] modes={AspectRatioFrameLayout.RESIZE_MODE_FIT,AspectRatioFrameLayout.RESIZE_MODE_FILL,AspectRatioFrameLayout.RESIZE_MODE_ZOOM};
        for(int i=0;i<modes.length;i++){
            field("resizeButton").performClick();android.app.AlertDialog dialog=org.robolectric.shadows.ShadowAlertDialog.getLatestAlertDialog();
            dialog.getListView().performItemClick(dialog.getListView().getChildAt(i),i,i);
            assertEquals(modes[i],video.getResizeMode());assertEquals(modes[i],QingYingApp.prefs(activity).getInt("playerResizeMode",-1));assertFalse(dialog.isShowing());
        }
    }
    @After public void destroy(){((Handler)ReflectionHelpers.getField(activity,"handler")).removeCallbacksAndMessages(null);video.stopHold();activity.finish();}
}
