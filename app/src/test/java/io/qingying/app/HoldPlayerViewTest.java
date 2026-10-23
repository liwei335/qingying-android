package io.qingying.app;

import android.app.Activity;
import android.app.Application;
import android.os.Looper;
import android.os.SystemClock;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import androidx.media3.exoplayer.ExoPlayer;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.LooperMode;
import java.time.Duration;
import static org.junit.Assert.*;
import static org.robolectric.Shadows.shadowOf;

@RunWith(RobolectricTestRunner.class)
@Config(sdk=28,application=Application.class)
@LooperMode(LooperMode.Mode.PAUSED)
@androidx.annotation.OptIn(markerClass=androidx.media3.common.util.UnstableApi.class)
public class HoldPlayerViewTest {
    private Activity activity;private HoldPlayerView view;private ExoPlayer player;
    @Before public void create(){
        activity=Robolectric.buildActivity(Activity.class).setup().get();activity.setTheme(R.style.AppTheme);
        view=new HoldPlayerView(activity);activity.setContentView(view);
        view.measure(View.MeasureSpec.makeMeasureSpec(1000,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(600,View.MeasureSpec.EXACTLY));view.layout(0,0,1000,600);
        player=new ExoPlayer.Builder(activity).build();player.setPlayWhenReady(true);player.setPlaybackSpeed(1.5f);view.setPlayer(player);view.hideController();
    }
    private void touch(int action){MotionEvent e=MotionEvent.obtain(SystemClock.uptimeMillis(),SystemClock.uptimeMillis(),action,500,150,0);view.dispatchTouchEvent(e);e.recycle();}
    private void hold(){touch(MotionEvent.ACTION_DOWN);shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(ViewConfiguration.getLongPressTimeout()+50));}
    @Test public void releaseRestoresSpeedAndDoesNotTurnIntoClick(){hold();assertEquals(2f,player.getPlaybackParameters().speed,0);touch(MotionEvent.ACTION_UP);assertEquals(1.5f,player.getPlaybackParameters().speed,0);assertFalse(view.isControllerFullyVisible());}
    @Test public void canceledGestureRestoresSpeed(){hold();assertEquals(2f,player.getPlaybackParameters().speed,0);touch(MotionEvent.ACTION_CANCEL);assertEquals(1.5f,player.getPlaybackParameters().speed,0);}
    @Test public void focusLossAndStopRestoreSpeed(){hold();view.onWindowFocusChanged(false);assertEquals(1.5f,player.getPlaybackParameters().speed,0);view.stopHold();assertEquals(1.5f,player.getPlaybackParameters().speed,0);}
    @Test public void shortTapDoesNotChangeSpeed(){touch(MotionEvent.ACTION_DOWN);touch(MotionEvent.ACTION_UP);shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(1000));assertEquals(1.5f,player.getPlaybackParameters().speed,0);}
    @After public void destroy(){if(view!=null){view.stopHold();view.setPlayer(null);}if(player!=null)player.release();if(activity!=null)activity.finish();}
}
