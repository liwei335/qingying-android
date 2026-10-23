package io.qingying.app;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewConfiguration;
import androidx.media3.common.Player;
import androidx.media3.ui.PlayerView;
import androidx.media3.ui.TimeBar;

@androidx.annotation.OptIn(markerClass=androidx.media3.common.util.UnstableApi.class)
public final class HoldPlayerView extends PlayerView {
    public interface Listener { void changed(boolean active); }
    private final Handler handler=new Handler(Looper.getMainLooper());
    private final HoldSpeed speed=new HoldSpeed();
    private Listener listener;
    private float downX,downY;
    private boolean pending,consume;
    private final int slop;
    private final Runnable activate=()->{
        Player p=getPlayer();if(!pending||p==null||!p.getPlayWhenReady())return;
        pending=false;consume=true;p.setPlaybackSpeed(speed.begin(p.getPlaybackParameters().speed));
        MotionEvent cancel=MotionEvent.obtain(android.os.SystemClock.uptimeMillis(),android.os.SystemClock.uptimeMillis(),MotionEvent.ACTION_CANCEL,downX,downY,0);
        super.dispatchTouchEvent(cancel);cancel.recycle();getParent().requestDisallowInterceptTouchEvent(true);
        if(listener!=null)listener.changed(true);
    };
    public HoldPlayerView(Context c){super(c);slop=ViewConfiguration.get(c).getScaledTouchSlop();}
    public void setHoldListener(Listener listener){this.listener=listener;}
    public void stopHold(){
        pending=false;handler.removeCallbacks(activate);Player p=getPlayer();
        if(p!=null)p.setPlaybackSpeed(speed.end(p.getPlaybackParameters().speed));else speed.end(1f);
        if(listener!=null)listener.changed(false);
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
    @Override public boolean dispatchTouchEvent(MotionEvent e){
        int action=e.getActionMasked();
        if(action==MotionEvent.ACTION_DOWN){stopHold();consume=false;downX=e.getX();downY=e.getY();
            if(!controlHit(this,e.getRawX(),e.getRawY())){pending=true;handler.postDelayed(activate,ViewConfiguration.getLongPressTimeout());}}
        else if(action==MotionEvent.ACTION_MOVE&&(Math.abs(e.getX()-downX)>slop||Math.abs(e.getY()-downY)>slop))stopHold();
        else if(action==MotionEvent.ACTION_POINTER_DOWN||action==MotionEvent.ACTION_POINTER_UP)stopHold();
        if(action==MotionEvent.ACTION_UP||action==MotionEvent.ACTION_CANCEL){boolean used=consume;stopHold();consume=false;if(used){getParent().requestDisallowInterceptTouchEvent(false);return true;}}
        if(consume)return true;return super.dispatchTouchEvent(e);
    }
    @Override protected void onDetachedFromWindow(){stopHold();super.onDetachedFromWindow();}
    @Override public void onWindowFocusChanged(boolean focus){if(!focus)stopHold();super.onWindowFocusChanged(focus);}
}
