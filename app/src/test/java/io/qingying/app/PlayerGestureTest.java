package io.qingying.app;

import android.app.Activity;
import android.app.Application;
import android.media.AudioManager;
import android.os.Looper;
import android.os.SystemClock;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import androidx.media3.common.C;
import androidx.media3.common.PlaybackParameters;
import androidx.media3.common.Player;
import androidx.media3.common.SimpleBasePlayer;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.LooperMode;
import java.time.Duration;
import java.util.Collections;
import static org.junit.Assert.*;
import static org.robolectric.Shadows.shadowOf;

@RunWith(RobolectricTestRunner.class)
@Config(sdk=28,application=Application.class)
@LooperMode(LooperMode.Mode.PAUSED)
@androidx.annotation.OptIn(markerClass=androidx.media3.common.util.UnstableApi.class)
public class PlayerGestureTest {
    private Activity activity;private HoldPlayerView view;private TestPlayer player;private AudioManager audio;
    private int taps;private String hint="";
    @Before public void create(){
        activity=Robolectric.buildActivity(Activity.class).setup().get();activity.setTheme(R.style.AppTheme);
        view=new HoldPlayerView(activity);view.setUseController(false);activity.setContentView(view);
        view.measure(View.MeasureSpec.makeMeasureSpec(1000,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(600,View.MeasureSpec.EXACTLY));view.layout(0,0,1000,600);
        player=new TestPlayer();view.setPlayer(player);
        view.setGestureListener(new HoldPlayerView.GestureListener(){public void tap(){taps++;}public void feedback(String text){hint=text;}});
        audio=(AudioManager)activity.getSystemService(Activity.AUDIO_SERVICE);audio.setStreamVolume(AudioManager.STREAM_MUSIC,3,0);
    }
    private void touch(int action,float x,float y){MotionEvent e=MotionEvent.obtain(SystemClock.uptimeMillis(),SystemClock.uptimeMillis(),action,x,y,0);view.dispatchTouchEvent(e);e.recycle();}
    @Test public void horizontalDragPreviewsThenSeeksOnceWithoutTapOrVolumeChange(){
        touch(MotionEvent.ACTION_DOWN,500,300);touch(MotionEvent.ACTION_MOVE,750,310);
        assertEquals(0,player.seeks);assertTrue(hint.contains("快进"));assertEquals(3,audio.getStreamVolume(AudioManager.STREAM_MUSIC));
        touch(MotionEvent.ACTION_UP,750,310);assertEquals(90000,player.getCurrentPosition());assertEquals(1,player.seeks);assertEquals(0,taps);assertEquals("",hint);
    }
    @Test public void seekClampsBothEndsAndCancelDoesNotCommit(){
        touch(MotionEvent.ACTION_DOWN,500,300);touch(MotionEvent.ACTION_MOVE,-1500,300);touch(MotionEvent.ACTION_UP,-1500,300);assertEquals(0,player.getCurrentPosition());
        touch(MotionEvent.ACTION_DOWN,500,300);touch(MotionEvent.ACTION_MOVE,4500,300);touch(MotionEvent.ACTION_CANCEL,4500,300);assertEquals(1,player.seeks);
        touch(MotionEvent.ACTION_DOWN,500,300);touch(MotionEvent.ACTION_MOVE,4500,300);touch(MotionEvent.ACTION_UP,4500,300);assertEquals(300000,player.getCurrentPosition());
    }
    @Test public void verticalDragAdjustsSystemMediaVolumeOnly(){
        touch(MotionEvent.ACTION_DOWN,500,300);touch(MotionEvent.ACTION_MOVE,510,0);assertTrue(audio.getStreamVolume(AudioManager.STREAM_MUSIC)>3);assertTrue(hint.startsWith("音量"));
        touch(MotionEvent.ACTION_MOVE,800,600);assertEquals(0,audio.getStreamVolume(AudioManager.STREAM_MUSIC));touch(MotionEvent.ACTION_UP,800,600);
        assertEquals(0,player.seeks);assertEquals(0,taps);
    }
    @Test public void lockedScreenOnlyAcceptsTapToRevealUnlock(){
        view.setLocked(true);touch(MotionEvent.ACTION_DOWN,500,300);shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(800));
        assertEquals(1.5f,player.getPlaybackParameters().speed,0);touch(MotionEvent.ACTION_MOVE,900,0);touch(MotionEvent.ACTION_UP,900,0);
        assertEquals(0,player.seeks);assertEquals(3,audio.getStreamVolume(AudioManager.STREAM_MUSIC));assertEquals(0,taps);
        touch(MotionEvent.ACTION_DOWN,500,300);touch(MotionEvent.ACTION_UP,500,300);assertEquals(1,taps);
    }
    @Test public void unknownDurationCannotSeekAndDoesNotBecomeTap(){
        player.media(false,C.TIME_UNSET);touch(MotionEvent.ACTION_DOWN,500,300);touch(MotionEvent.ACTION_MOVE,900,300);assertTrue(hint.contains("不支持"));touch(MotionEvent.ACTION_UP,900,300);assertEquals(0,player.seeks);assertEquals(0,taps);
    }
    @Test public void holdThenMoveAndFocusLossRestoreSpeedWithoutSeeking(){
        touch(MotionEvent.ACTION_DOWN,500,300);shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(ViewConfiguration.getLongPressTimeout()+20));assertEquals(2f,player.getPlaybackParameters().speed,0);
        touch(MotionEvent.ACTION_MOVE,900,300);touch(MotionEvent.ACTION_UP,900,300);assertEquals(1.5f,player.getPlaybackParameters().speed,0);assertEquals(0,player.seeks);assertEquals(0,taps);
        touch(MotionEvent.ACTION_DOWN,500,300);touch(MotionEvent.ACTION_MOVE,900,300);view.onWindowFocusChanged(false);touch(MotionEvent.ACTION_UP,900,300);assertEquals(0,player.seeks);assertEquals(0,taps);
    }
    @Test public void secondPointerCancelsSeek(){
        touch(MotionEvent.ACTION_DOWN,500,300);touch(MotionEvent.ACTION_MOVE,800,300);touch(MotionEvent.ACTION_POINTER_DOWN,800,300);touch(MotionEvent.ACTION_UP,800,300);assertEquals(0,player.seeks);assertEquals(0,taps);
    }
    @After public void destroy(){view.stopHold();view.setPlayer(null);activity.finish();}
    private static final class TestPlayer extends SimpleBasePlayer {
        State state;int seeks;
        TestPlayer(){super(Looper.getMainLooper());state=new State.Builder().setAvailableCommands(new Player.Commands.Builder().addAll(Player.COMMAND_GET_CURRENT_MEDIA_ITEM,Player.COMMAND_GET_TIMELINE,Player.COMMAND_SEEK_IN_CURRENT_MEDIA_ITEM,Player.COMMAND_SET_SPEED_AND_PITCH).build())
            .setPlaybackState(Player.STATE_READY).setPlayWhenReady(true,Player.PLAY_WHEN_READY_CHANGE_REASON_USER_REQUEST).setPlaybackParameters(new PlaybackParameters(1.5f)).setContentPositionMs(()->60000L)
            .setPlaylist(Collections.singletonList(new MediaItemData.Builder("test").setIsSeekable(true).setDurationUs(300000000).build())).build();}
        void media(boolean seekable,long duration){state=state.buildUpon().setPlaylist(Collections.singletonList(new MediaItemData.Builder("test").setIsSeekable(seekable).setDurationUs(duration).build())).build();invalidateState();}
        @Override protected State getState(){return state;}
        @Override protected ListenableFuture<?> handleSeek(int index,long position,int command){seeks++;state=state.buildUpon().setContentPositionMs(()->position).build();return Futures.immediateVoidFuture();}
        @Override protected ListenableFuture<?> handleSetPlaybackParameters(PlaybackParameters parameters){state=state.buildUpon().setPlaybackParameters(parameters).build();return Futures.immediateVoidFuture();}
    }
}
