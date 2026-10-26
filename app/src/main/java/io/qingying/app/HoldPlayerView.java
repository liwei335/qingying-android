package io.qingying.app;

import android.content.Context;
import android.media.AudioManager;
import android.os.Handler;
import android.os.Looper;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewConfiguration;
import androidx.media3.common.Player;
import androidx.media3.ui.PlayerView;
import androidx.media3.ui.TimeBar;

/** One gesture owns a touch sequence: tap, hold, seek or volume. Controls keep their own touches. */
@androidx.annotation.OptIn(markerClass=androidx.media3.common.util.UnstableApi.class)
public final class HoldPlayerView extends PlayerView {
    public interface Listener { void changed(boolean active); }
    public interface GestureListener {
        void tap();
        void feedback(String text);
    }
    private static final int NONE=0, SEEK=1, VOLUME=2, CANCELED=3;
    private final Handler handler=new Handler(Looper.getMainLooper());
    private final HoldSpeed speed=new HoldSpeed();
    private final AudioManager audio;
    private Listener listener;
    private GestureListener gestures;
    private float downX,downY;
    private boolean pending,consume,tracking,locked;
    private int mode,startVolume;
    private long startPosition,seekPosition;
    private final int slop;
    private final Runnable activate=()->{
        Player p=getPlayer();if(!pending||locked||p==null||!p.getPlayWhenReady())return;
        pending=false;consume=true;p.setPlaybackSpeed(speed.begin(p.getPlaybackParameters().speed));
        cancelChild();disallowIntercept(true);
        if(listener!=null)listener.changed(true);
    };
    public HoldPlayerView(Context c){
        super(c);slop=ViewConfiguration.get(c).getScaledTouchSlop();
        audio=(AudioManager)c.getApplicationContext().getSystemService(Context.AUDIO_SERVICE);
    }
    public void setHoldListener(Listener listener){this.listener=listener;}
    public void setGestureListener(GestureListener listener){gestures=listener;}
    public void setLocked(boolean value){stopHold();locked=value;}
    public boolean isLocked(){return locked;}
    private void endSpeed(){
        pending=false;handler.removeCallbacks(activate);Player p=getPlayer();
        if(p!=null&&speed.active())p.setPlaybackSpeed(speed.end(p.getPlaybackParameters().speed));else speed.end(1f);
        if(listener!=null)listener.changed(false);
    }
    /** Cancel any in-flight gesture, without committing a seek or generating a tap. */
    public void stopHold(){
        endSpeed();tracking=false;consume=false;mode=NONE;disallowIntercept(false);
        if(gestures!=null)gestures.feedback("");
    }
    private void disallowIntercept(boolean value){if(getParent()!=null)getParent().requestDisallowInterceptTouchEvent(value);}
    private void cancelChild(){
        MotionEvent cancel=MotionEvent.obtain(android.os.SystemClock.uptimeMillis(),android.os.SystemClock.uptimeMillis(),MotionEvent.ACTION_CANCEL,downX,downY,0);
        super.dispatchTouchEvent(cancel);cancel.recycle();
    }
    private boolean controlHit(View v,float rawX,float rawY){
        if(v.getVisibility()!=VISIBLE)return false;
        if(v!=this&&(v instanceof TimeBar||(!(v instanceof ViewGroup)&&v.isClickable()))) {
            int[] at=new int[2];v.getLocationOnScreen(at);
            if(rawX>=at[0]&&rawX<at[0]+v.getWidth()&&rawY>=at[1]&&rawY<at[1]+v.getHeight())return true;
        }
        if(v instanceof ViewGroup){ViewGroup group=(ViewGroup)v;for(int i=0;i<group.getChildCount();i++)if(controlHit(group.getChildAt(i),rawX,rawY))return true;}
        return false;
    }
    private boolean seekable(Player p){return p!=null&&p.isCommandAvailable(Player.COMMAND_SEEK_IN_CURRENT_MEDIA_ITEM)&&p.isCurrentMediaItemSeekable()&&p.getDuration()>0;}
    private void drag(float dx,float dy){
        Player p=getPlayer();
        if(mode==SEEK){
            if(!seekable(p)){feedback("当前视频暂不支持调整进度");return;}
            // A full-width drag covers at most two minutes, allowing fine adjustment on long films.
            long delta=Math.round(dx/Math.max(1,getWidth())*Math.min(p.getDuration(),120000L));
            seekPosition=Math.max(0,Math.min(p.getDuration(),startPosition+delta));
            feedback((delta>=0?"快进  ":"快退  ")+PlayerActivity.duration(seekPosition)+" / "+PlayerActivity.duration(p.getDuration()));
        }else if(mode==VOLUME&&audio!=null){
            if(audio.isVolumeFixed()){feedback("当前设备音量固定");return;}
            int max=audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC);
            int next=Math.max(0,Math.min(max,startVolume+Math.round(-dy/Math.max(1,getHeight())*max)));
            try{audio.setStreamVolume(AudioManager.STREAM_MUSIC,next,0);
                feedback("音量  "+Math.round(audio.getStreamVolume(AudioManager.STREAM_MUSIC)*100f/Math.max(1,max))+"%");
            }catch(SecurityException ignored){feedback("请使用系统音量键调整");}
        }
    }
    private void feedback(String text){if(gestures!=null)gestures.feedback(text);}
    @Override public boolean performClick(){
        super.performClick();if(gestures!=null)gestures.tap();return true;
    }
    @Override public boolean dispatchTouchEvent(MotionEvent e){
        int action=e.getActionMasked();
        if(action==MotionEvent.ACTION_DOWN){
            stopHold();downX=e.getX();downY=e.getY();
            tracking=locked||!controlHit(this,e.getRawX(),e.getRawY());
            if(!tracking)return super.dispatchTouchEvent(e);
            Player p=getPlayer();startPosition=p==null?0:p.getCurrentPosition();seekPosition=startPosition;
            startVolume=audio==null?0:audio.getStreamVolume(AudioManager.STREAM_MUSIC);
            if(!locked){pending=true;handler.postDelayed(activate,ViewConfiguration.getLongPressTimeout());}
            disallowIntercept(true);
            if(gestures==null&&!locked)super.dispatchTouchEvent(e);
            return true;
        }
        if(!tracking)return super.dispatchTouchEvent(e);
        if(action==MotionEvent.ACTION_POINTER_DOWN||action==MotionEvent.ACTION_POINTER_UP){
            endSpeed();mode=CANCELED;consume=true;cancelChild();feedback("");return true;
        }
        if(action==MotionEvent.ACTION_MOVE){
            float dx=e.getX()-downX,dy=e.getY()-downY;
            if(mode==NONE&&(Math.abs(dx)>slop||Math.abs(dy)>slop)){
                boolean held=consume;endSpeed();consume=true;cancelChild();
                mode=locked||held?CANCELED:Math.abs(dx)>=Math.abs(dy)?SEEK:VOLUME;
            }
            if(!locked)drag(dx,dy);
            return true;
        }
        if(action==MotionEvent.ACTION_UP||action==MotionEvent.ACTION_CANCEL){
            boolean tap=action==MotionEvent.ACTION_UP&&!consume&&mode==NONE;
            Player p=getPlayer();
            if(action==MotionEvent.ACTION_UP&&!locked&&mode==SEEK&&seekable(p))p.seekTo(seekPosition);
            stopHold();
            if(tap){if(gestures!=null)performClick();else if(!locked)super.dispatchTouchEvent(e);}
            else cancelChild();
            return true;
        }
        return true;
    }
    @Override protected void onDetachedFromWindow(){stopHold();super.onDetachedFromWindow();}
    @Override public void onWindowFocusChanged(boolean focus){if(!focus)stopHold();super.onWindowFocusChanged(focus);}
}
