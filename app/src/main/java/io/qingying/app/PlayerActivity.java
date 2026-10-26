package io.qingying.app;

import android.app.AlertDialog;
import android.content.pm.ActivityInfo;
import android.content.Intent;
import android.content.res.Configuration;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import androidx.activity.ComponentActivity;
import androidx.activity.OnBackPressedCallback;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsControllerCompat;
import androidx.media3.common.AudioAttributes;
import androidx.media3.common.C;
import androidx.media3.common.Format;
import androidx.media3.common.MediaItem;
import androidx.media3.common.PlaybackException;
import androidx.media3.common.Player;
import androidx.media3.common.VideoSize;
import androidx.media3.datasource.DefaultHttpDataSource;
import androidx.media3.datasource.DataSource;
import androidx.media3.exoplayer.DefaultLoadControl;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory;
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector;
import androidx.media3.ui.PlayerView;
import androidx.media3.ui.AspectRatioFrameLayout;
import androidx.media3.ui.DefaultTimeBar;
import androidx.media3.ui.TimeBar;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

@androidx.annotation.OptIn(markerClass=androidx.media3.common.util.UnstableApi.class)
public final class PlayerActivity extends ComponentActivity {
    private QingYingApp app;
    private Video video;
    private ExoPlayer player;
    private DefaultTrackSelector selector;
    private HoldPlayerView playerView;
    private TextView status,quality,favorite,playButton,positionText,orientationButton,resizeButton,feedback;
    private LinearLayout topBar,bottomBar;
    private TextView lockLeft,lockRight;
    private DefaultTimeBar timeBar;
    private boolean locked,controlsVisible=true,scrubbing;
    private int unlockedOrientation=ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED;
    private final Runnable hideControls=()->setControlsVisible(false);
    private final Runnable progressTick=new Runnable(){public void run(){updateProgress();if(started&&controlsVisible&&!locked)handler.postDelayed(this,500);}};
    private int streamIndex;
    private long resumePosition;
    private boolean started,playWhenReady=true,recorded;
    private boolean cloudResolved,cloudResolving,cloudRefreshed;
    private volatile int resolveRequest;
    private int qualityMode,resizeMode=AspectRatioFrameLayout.RESIZE_MODE_ZOOM;
    private volatile int sourceRequest;
    private AlertDialog sourceLoading;
    private float normalSpeed=1f;
    private final Set<Integer> attempted=new HashSet<>();
    private final Handler handler=new Handler(Looper.getMainLooper());
    private final Runnable saver=new Runnable(){public void run(){save();handler.postDelayed(this,5000);}};

    @Override public void onCreate(Bundle state){
        super.onCreate(state);app=QingYingApp.app(this);
        resizeMode=QingYingApp.prefs(this).getInt("playerResizeMode",AspectRatioFrameLayout.RESIZE_MODE_ZOOM);
        WindowCompat.setDecorFitsSystemWindows(getWindow(),false);
        setVolumeControlStream(android.media.AudioManager.STREAM_MUSIC);
        if(android.os.Build.VERSION.SDK_INT>=28){WindowManager.LayoutParams attrs=getWindow().getAttributes();attrs.layoutInDisplayCutoutMode=WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES;getWindow().setAttributes(attrs);}
        getOnBackPressedDispatcher().addCallback(this,new OnBackPressedCallback(true){public void handleOnBackPressed(){
            if(locked){setControlsVisible(true);Toast.makeText(PlayerActivity.this,"请先点击侧边按钮解锁",Toast.LENGTH_SHORT).show();}else finish();
        }});
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        String id=state==null?getIntent().getStringExtra("id"):state.getString("id",getIntent().getStringExtra("id"));
        app.io.execute(()->{Video v=app.repo.get(id);runOnUiThread(()->{
            if(isDestroyed()||isFinishing())return;
            if(v==null||v.streams.isEmpty()){Toast.makeText(this,"资源不存在，请刷新片单",Toast.LENGTH_LONG).show();finish();return;}
            if(v.isHongguo()){startActivity(new Intent(this,HongguoActivity.class).putExtra("id",v.id));finish();return;}
            video=v;resumePosition=v.position;
            streamIndex=CatalogLogic.resumeIndex(v);
            boolean matched=false;
            for(int i=0;i<v.streams.size();i++)if(!v.lastStream.isEmpty()&&v.streams.get(i).url.equals(v.lastStream)){streamIndex=i;matched=true;break;}
            if(!matched&&!v.lastEpisode.isEmpty())for(int i=0;i<v.streams.size();i++)if(v.streams.get(i).episode.equals(v.lastEpisode)){streamIndex=i;matched=true;break;}
            if(!matched&&!v.lastEpisode.isEmpty()&&!v.isCloud()&&!v.isHongguo())resumePosition=0;
            if(state!=null){resumePosition=state.getLong("position",resumePosition);playWhenReady=state.getBoolean("play",true);normalSpeed=state.getFloat("speed",1f);qualityMode=state.getInt("qualityMode");resizeMode=state.getInt("resizeMode",resizeMode);v.lastEpisode=state.getString("episode",v.lastEpisode);streamIndex=CatalogLogic.resumeIndex(v);}
            if(state!=null){locked=state.getBoolean("locked");unlockedOrientation=state.getInt("unlockedOrientation",ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED);}
            build();if(locked)setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LOCKED);if(started)initialize();
        });});
    }
    private void build(){
        handler.removeCallbacks(hideControls);handler.removeCallbacks(progressTick);
        if(playerView!=null){playerView.stopHold();playerView.setPlayer(null);}
        FrameLayout root=new FrameLayout(this);root.setBackgroundColor(android.graphics.Color.BLACK);setContentView(root);
        playerView=new HoldPlayerView(this);playerView.setUseController(false);
        playerView.setShowBuffering(PlayerView.SHOW_BUFFERING_ALWAYS);playerView.setKeepContentOnPlayerReset(true);playerView.setResizeMode(resizeMode);
        root.addView(playerView,new FrameLayout.LayoutParams(-1,-1));
        FrameLayout overlay=new FrameLayout(this);root.addView(overlay,new FrameLayout.LayoutParams(-1,-1));
        // Insets protect controls only. Video always occupies the entire window.
        ViewCompat.setOnApplyWindowInsetsListener(overlay,(v,insets)->{
            Insets i=insets.getInsetsIgnoringVisibility(WindowInsetsCompat.Type.systemBars()|WindowInsetsCompat.Type.displayCutout());
            v.setPadding(i.left,i.top,i.right,i.bottom);return insets;
        });
        topBar=Ui.row(this);Ui.pad(topBar,12,8);topBar.setBackground(new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,new int[]{0xdd000000,0x22000000}));topBar.setClickable(true);
        topBar.addView(Ui.button(this,"‹ 返回",false,v->finish()));
        TextView title=Ui.title(this,video.title,16);title.setSingleLine();title.setEllipsize(android.text.TextUtils.TruncateAt.END);Ui.pad(title,12,0);topBar.addView(title,new LinearLayout.LayoutParams(0,-2,1));
        overlay.addView(topBar,new FrameLayout.LayoutParams(-1,-2,Gravity.TOP));
        bottomBar=Ui.col(this);Ui.pad(bottomBar,12,6);bottomBar.setClickable(true);
        bottomBar.setBackground(new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,new int[]{0x22000000,0xee000000}));
        LinearLayout info=Ui.row(this);status=Ui.text(this,"准备播放…",12,Ui.MUTED);status.setSingleLine();status.setEllipsize(android.text.TextUtils.TruncateAt.END);info.addView(status,new LinearLayout.LayoutParams(0,-2,1));
        quality=Ui.text(this,"自适应",12,Ui.GREEN);info.addView(quality);bottomBar.addView(info);
        LinearLayout timeline=Ui.row(this);
        playButton=Ui.button(this,"暂停",false,v->{if(player==null)return;if(player.getPlaybackState()==Player.STATE_ENDED){player.seekTo(0);player.play();}else if(player.getPlayWhenReady())player.pause();else player.play();updateProgress();scheduleHide();});
        timeline.addView(playButton);
        timeBar=new DefaultTimeBar(this);timeBar.setPlayedColor(Ui.GREEN);timeBar.setScrubberColor(Ui.GREEN);
        timeBar.setContentDescription("播放进度");timeline.addView(timeBar,new LinearLayout.LayoutParams(0,Ui.dp(this,48),1));
        positionText=Ui.text(this,"00:00 / 00:00",11,Ui.TEXT);timeline.addView(positionText);bottomBar.addView(timeline);
        timeBar.addListener(new TimeBar.OnScrubListener(){
            public void onScrubStart(TimeBar bar,long position){scrubbing=true;handler.removeCallbacks(hideControls);}
            public void onScrubMove(TimeBar bar,long position){positionText.setText(duration(position)+" / "+duration(player==null?0:Math.max(0,player.getDuration())));}
            public void onScrubStop(TimeBar bar,long position,boolean canceled){if(!canceled&&canSeek())player.seekTo(position);scrubbing=false;updateProgress();scheduleHide();}
        });
        LinearLayout actions=Ui.row(this);
        android.widget.HorizontalScrollView scroller=new android.widget.HorizontalScrollView(this);scroller.setHorizontalScrollBarEnabled(false);
        LinearLayout controls=Ui.row(this);
        String[] labels={video.isCloud()?"原画 / 画质":"选集 / 线路",video.isCloud()?"更新地址":"换片源","画质",resizeLabel(),"速度",video.favorite?"已收藏":"收藏"};
        for(int i=0;i<labels.length;i++){final int action=i;TextView button=Ui.button(this,labels[i],false,v->{
            scheduleHide();
            if(action==0)chooseStream();if(action==1){if(video.isCloud()){cloudRefreshed=false;refreshCloud();}else chooseResources();}
            if(action==2)chooseQuality();if(action==3)chooseResize();if(action==4)chooseSpeed();
            if(action==5){String id=video.id;app.io.execute(()->app.repo.favorite(id));video.favorite=!video.favorite;favorite.setText(video.favorite?"已收藏":"收藏");}
        });LinearLayout.LayoutParams params=Ui.margin(this,-2,48,0);params.rightMargin=Ui.dp(this,6);controls.addView(button,params);if(i==5)favorite=button;if(i==3)resizeButton=button;}
        scroller.addView(controls);actions.addView(scroller,new LinearLayout.LayoutParams(0,-2,1));
        orientationButton=Ui.button(this,"",false,v->{if(!locked){scheduleHide();setRequestedOrientation(isLandscape()?ActivityInfo.SCREEN_ORIENTATION_PORTRAIT:ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE);}});
        updateOrientationButton();actions.addView(orientationButton);bottomBar.addView(actions);
        overlay.addView(bottomBar,new FrameLayout.LayoutParams(-1,-2,Gravity.BOTTOM));
        lockLeft=lockButton();lockRight=lockButton();
        FrameLayout.LayoutParams left=new FrameLayout.LayoutParams(Ui.dp(this,60),Ui.dp(this,48),Gravity.CENTER_VERTICAL|Gravity.START);left.leftMargin=Ui.dp(this,12);overlay.addView(lockLeft,left);
        FrameLayout.LayoutParams right=new FrameLayout.LayoutParams(Ui.dp(this,60),Ui.dp(this,48),Gravity.CENTER_VERTICAL|Gravity.END);right.rightMargin=Ui.dp(this,12);overlay.addView(lockRight,right);
        feedback=Ui.text(this,"",16,Ui.TEXT);Ui.pad(feedback,20,14);feedback.setBackground(Ui.shape(this,0xdd101417,12));feedback.setVisibility(View.GONE);
        overlay.addView(feedback,new FrameLayout.LayoutParams(-2,-2,Gravity.CENTER));
        playerView.setHoldListener(active->showFeedback(active?"▶▶  临时 2 倍速 · 松手恢复":""));
        playerView.setGestureListener(new HoldPlayerView.GestureListener(){
            public void tap(){setControlsVisible(!controlsVisible);}
            public void feedback(String text){showFeedback(text);}
        });
        playerView.setLocked(locked);
        playerView.addOnLayoutChangeListener((v,l,t,r,b,ol,ot,or,ob)->{if(r-l!=or-ol||b-t!=ob-ot)applyQuality();});
        if(player!=null)playerView.setPlayer(player);
        updateLockButtons();setControlsVisible(true);updateInfo();immerse();ViewCompat.requestApplyInsets(overlay);
    }
    private boolean isLandscape(){return getResources().getConfiguration().orientation==Configuration.ORIENTATION_LANDSCAPE;}
    private void updateOrientationButton(){if(orientationButton!=null)orientationButton.setText(isLandscape()?"竖屏":"横屏");}
    private TextView lockButton(){TextView button=Ui.button(this,"锁定",false,v->toggleLock());Ui.pad(button,6,8);return button;}
    private void toggleLock(){
        locked=!locked;playerView.setLocked(locked);
        if(locked){unlockedOrientation=getRequestedOrientation();setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LOCKED);}
        else setRequestedOrientation(unlockedOrientation);
        updateLockButtons();setControlsVisible(true);
    }
    private void updateLockButtons(){lockLeft.setText(locked?"解锁":"锁定");lockRight.setText(locked?"解锁":"锁定");lockLeft.setContentDescription(locked?"解除屏幕锁定":"锁定屏幕触控");lockRight.setContentDescription(lockLeft.getContentDescription());}
    private void setControlsVisible(boolean visible){
        controlsVisible=visible;if(topBar==null)return;
        topBar.setVisibility(visible&&!locked?View.VISIBLE:View.GONE);bottomBar.setVisibility(visible&&!locked?View.VISIBLE:View.GONE);
        lockLeft.setVisibility(visible?View.VISIBLE:View.GONE);lockRight.setVisibility(visible?View.VISIBLE:View.GONE);
        handler.removeCallbacks(progressTick);if(visible&&!locked){updateProgress();if(started)handler.postDelayed(progressTick,500);}scheduleHide();
    }
    private void scheduleHide(){handler.removeCallbacks(hideControls);if(started&&controlsVisible&&!scrubbing)handler.postDelayed(hideControls,3500);}
    private void showFeedback(String text){if(feedback!=null){feedback.setText(text);feedback.setVisibility(text.isEmpty()?View.GONE:View.VISIBLE);}}
    private boolean canSeek(){return player!=null&&player.isCommandAvailable(Player.COMMAND_SEEK_IN_CURRENT_MEDIA_ITEM)&&player.isCurrentMediaItemSeekable()&&player.getDuration()>0;}
    private void updateProgress(){
        if(playButton==null)return;
        long duration=player==null?0:Math.max(0,player.getDuration()),position=player==null?resumePosition:Math.max(0,player.getCurrentPosition());
        playButton.setText(player!=null&&player.getPlaybackState()==Player.STATE_ENDED?"重播":player!=null&&player.getPlayWhenReady()?"暂停":"播放");
        playButton.setEnabled(player!=null);timeBar.setEnabled(canSeek());
        if(!scrubbing){timeBar.setDuration(duration);timeBar.setPosition(position);timeBar.setBufferedPosition(player==null?0:Math.max(0,player.getBufferedPosition()));positionText.setText(duration(position)+" / "+(duration>0?duration(duration):"--:--"));}
    }
    private String resizeLabel(){return resizeMode==AspectRatioFrameLayout.RESIZE_MODE_FIT?"自适应":resizeMode==AspectRatioFrameLayout.RESIZE_MODE_FILL?"拉伸":"缩放";}
    private void chooseResize(){
        int[] modes={AspectRatioFrameLayout.RESIZE_MODE_FIT,AspectRatioFrameLayout.RESIZE_MODE_FILL,AspectRatioFrameLayout.RESIZE_MODE_ZOOM};
        int selected=resizeMode==modes[0]?0:resizeMode==modes[1]?1:2;
        new AlertDialog.Builder(this).setTitle("画面比例").setSingleChoiceItems(new String[]{"自适应 · 完整画面，可能留黑边","拉伸 · 铺满屏幕，改变画面比例","缩放 · 等比铺满，裁切边缘"},selected,(d,n)->{
            resizeMode=modes[n];playerView.setResizeMode(resizeMode);resizeButton.setText(resizeLabel());QingYingApp.prefs(this).edit().putInt("playerResizeMode",resizeMode).apply();d.dismiss();
        }).setNegativeButton("关闭",null).show();
    }
    private void immerse(){WindowInsetsControllerCompat controller=WindowCompat.getInsetsController(getWindow(),getWindow().getDecorView());controller.setSystemBarsBehavior(WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);controller.hide(WindowInsetsCompat.Type.systemBars());}
    @Override public void onWindowFocusChanged(boolean focused){super.onWindowFocusChanged(focused);if(focused){immerse();scheduleHide();}else{handler.removeCallbacks(hideControls);if(playerView!=null)playerView.stopHold();}}
    @Override public boolean dispatchTouchEvent(MotionEvent event){
        if(event.getActionMasked()==MotionEvent.ACTION_DOWN)handler.removeCallbacks(hideControls);
        boolean handled=super.dispatchTouchEvent(event);
        if(event.getActionMasked()==MotionEvent.ACTION_UP||event.getActionMasked()==MotionEvent.ACTION_CANCEL)scheduleHide();
        return handled;
    }
    private void initialize(){
        if(!started||player!=null||video==null)return;
        if(video.isCloud()&&!cloudResolved){resolveCloud();return;}
        selector=new DefaultTrackSelector(this);
        // Allow 4K when supported, with bandwidth adaptation instead of forcing the largest rendition.
        selector.setParameters(selector.buildUponParameters().setMaxVideoSize(Integer.MAX_VALUE,Integer.MAX_VALUE));
        DefaultHttpDataSource.Factory http=new DefaultHttpDataSource.Factory().setUserAgent(RecommendationFeeds.UA)
            .setConnectTimeoutMs(10000).setReadTimeoutMs(15000).setAllowCrossProtocolRedirects(false);
        DataSource.Factory data=video.isCloud()?CloudHttpDataSource::new:http;
        player=new ExoPlayer.Builder(this).setTrackSelector(selector)
            .setMediaSourceFactory(new DefaultMediaSourceFactory(data))
            .setLoadControl(new DefaultLoadControl.Builder().setBufferDurationsMs(15000,50000,1000,2000)
                .setTargetBufferBytes(32*1024*1024).setPrioritizeTimeOverSizeThresholds(false).build()).build();
        player.setAudioAttributes(new AudioAttributes.Builder().setUsage(C.USAGE_MEDIA).setContentType(C.AUDIO_CONTENT_TYPE_MOVIE).build(),true);
        player.setHandleAudioBecomingNoisy(true);playerView.setPlayer(player);
        player.setPlaybackSpeed(normalSpeed);applyQuality();
        final ExoPlayer boundPlayer=player;
        player.addListener(new Player.Listener(){
            @Override public void onEvents(Player p,Player.Events events){if(player==boundPlayer)updateProgress();}
            @Override public void onPlaybackStateChanged(int state){
                if(player!=boundPlayer)return;
                if(state==Player.STATE_BUFFERING)status.setText("正在缓冲…");
                else if(state==Player.STATE_READY)updateInfo();
                else if(state==Player.STATE_ENDED){resumePosition=0;String id=video.id,url=video.streams.get(streamIndex).url,episode=video.streams.get(streamIndex).episode;long length=video.duration;app.io.execute(()->app.repo.progress(id,url,episode,0,length,true));status.setText("本集播放完毕");}
            }
            @Override public void onVideoSizeChanged(VideoSize size){if(player!=boundPlayer)return;quality.setText(size.height>0?size.width+" × "+size.height:"自适应");
                if(size.width>0&&size.height>0){Video.Stream stream=video.streams.get(streamIndex);if(size.width>stream.width||size.height>stream.height){stream.width=Math.max(stream.width,size.width);stream.height=Math.max(stream.height,size.height);String id=video.id;app.io.execute(()->app.repo.checked(id,stream));}}}
            @Override public void onIsPlayingChanged(boolean playing){
                if(player!=boundPlayer)return;
                if(playing&&!recorded){recorded=true;Video.Stream s=video.streams.get(streamIndex);s.state="ok";s.checked=System.currentTimeMillis();
                    Format format=player.getVideoFormat();if(format!=null&&format.height>0){s.height=Math.max(s.height,format.height);s.width=Math.max(s.width,format.width);}String id=video.id;app.io.execute(()->{app.repo.checked(id,s);app.repo.played(id);});}
            }
            @Override public void onPlayerError(PlaybackException error){
                if(player!=boundPlayer)return;
                playerView.stopHold();long position=player.getCurrentPosition()>0?player.getCurrentPosition():resumePosition;String episode=video.streams.get(streamIndex).episode;
                if(video.isCloud()&&!cloudRefreshed){cloudRefreshed=true;resumePosition=position;refreshCloud();return;}
                for(int i=0;i<video.streams.size();i++)if(!attempted.contains(i)&&(video.isCloud()||CatalogLogic.episodeKey(video.streams.get(i).episode).equals(CatalogLogic.episodeKey(episode)))&&!video.streams.get(i).state.equals("missing")){
                    Toast.makeText(PlayerActivity.this,"当前线路失败，尝试备用线路",Toast.LENGTH_SHORT).show();open(i,position);return;}
                status.setText("播放失败 · 可重试或切换线路");
                if(!started||isFinishing())return;
                new AlertDialog.Builder(PlayerActivity.this).setTitle("暂时无法播放")
                    .setMessage("网络、片源或解码暂不可用（"+error.getErrorCodeName()+"）。可以重试、换线路，或降低画质。")
                    .setPositiveButton("重试",(d,w)->{attempted.clear();open(streamIndex,position);})
                    .setNeutralButton("选集 / 线路",(d,w)->chooseStream()).setNegativeButton("关闭",null).show();
            }
        });
        open(streamIndex,resumePosition);handler.removeCallbacks(saver);handler.postDelayed(saver,5000);
    }
    private void open(int index,long position){
        if(player==null)return;playerView.stopHold();streamIndex=index;resumePosition=position;recorded=false;attempted.add(index);
        Video.Stream stream=video.streams.get(index);
        MediaItem.Builder item=new MediaItem.Builder().setUri(stream.url);if(!stream.mimeType.isEmpty())item.setMimeType(stream.mimeType);
        player.setMediaItem(item.build(),position);player.prepare();player.setPlayWhenReady(playWhenReady);updateInfo();
    }
    private void updateInfo(){if(video==null||status==null)return;status.setText(video.isCloud()&&!cloudResolved?"正在获取夸克播放地址…":video.streams.get(streamIndex).label);
        if(player!=null){VideoSize s=player.getVideoSize();quality.setText(s.height>0?s.width+" × "+s.height:"自适应");}}
    private void chooseStream(){if(video==null||player==null)return;
        String[] entries=new String[video.streams.size()];for(int i=0;i<entries.length;i++){Video.Stream s=video.streams.get(i);entries[i]=s.label+(s.height>=2160||s.width>=3840?" · 4K":"")+(s.state.equals("missing")?" · 已失效":"");}
        new AlertDialog.Builder(this).setTitle("选集与播放线路").setSingleChoiceItems(entries,streamIndex,(d,which)->{
            if(which!=streamIndex){Video.Stream old=video.streams.get(streamIndex),next=video.streams.get(which);long pos=video.isCloud()||CatalogLogic.episodeKey(old.episode).equals(CatalogLogic.episodeKey(next.episode))?player.getCurrentPosition():0;
                save();attempted.clear();playWhenReady=true;open(which,pos);}d.dismiss();}).setNegativeButton("取消",null).show();
    }
    private void chooseQuality(){if(player==null)return;
        new AlertDialog.Builder(this).setTitle("自适应画质上限").setItems(new String[]{"自动 · 适应屏幕、网络与设备","最高 1080p · 优先流畅","最高 4K · 优先清晰"},(d,index)->{
            qualityMode=index;applyQuality();
            Toast.makeText(this,"对多码率视频生效；单画质视频可在“选集 / 线路”中切换",Toast.LENGTH_LONG).show();
        }).show();
    }
    private void chooseSpeed(){if(player==null)return;String[] labels={"0.75×","1.0×","1.25×","1.5×","2.0×"};float[] speeds={.75f,1f,1.25f,1.5f,2f};
        new AlertDialog.Builder(this).setTitle("播放速度").setItems(labels,(d,index)->{playerView.stopHold();normalSpeed=speeds[index];player.setPlaybackSpeed(normalSpeed);}).show();}
    private void save(){
        if(player==null||video==null)return;
        long position=player.getPlaybackState()==Player.STATE_ENDED?0:Math.max(0,player.getCurrentPosition());
        if(player.getPlayerError()!=null&&position==0)position=resumePosition;
        long duration=Math.max(0,player.getDuration());resumePosition=position;video.duration=duration;
        String id=video.id,url=video.streams.get(streamIndex).url,episode=video.streams.get(streamIndex).episode;video.lastEpisode=episode;video.lastStream=video.isCloud()||video.isHongguo()?"":url;long savedPosition=position;boolean completed=player.getPlaybackState()==Player.STATE_ENDED,watching=player.isPlaying();app.io.execute(()->{app.repo.progress(id,url,episode,savedPosition,duration,completed);if(watching)app.repo.played(id);});
    }
    @Override protected void onStart(){super.onStart();started=true;initialize();if(playerView!=null)setControlsVisible(true);}
    @Override protected void onStop(){started=false;handler.removeCallbacks(hideControls);handler.removeCallbacks(progressTick);scrubbing=false;resolveRequest++;sourceRequest++;if(sourceLoading!=null){sourceLoading.dismiss();sourceLoading=null;}cloudResolving=false;releasePlayer();if(video!=null&&video.isCloud())cloudResolved=false;super.onStop();}
    @Override protected void onSaveInstanceState(Bundle out){if(playerView!=null)playerView.stopHold();super.onSaveInstanceState(out);if(video!=null){out.putString("id",video.id);out.putString("episode",video.streams.get(streamIndex).episode);}out.putLong("position",player==null?resumePosition:player.getCurrentPosition());out.putBoolean("play",player==null?playWhenReady:player.getPlayWhenReady());out.putFloat("speed",normalSpeed);out.putInt("qualityMode",qualityMode);out.putInt("resizeMode",resizeMode);out.putBoolean("locked",locked);out.putInt("unlockedOrientation",unlockedOrientation);}
    @Override public void onConfigurationChanged(Configuration config){super.onConfigurationChanged(config);if(playerView!=null){playerView.stopHold();updateOrientationButton();applyQuality();immerse();}}
    static String duration(long ms){long s=ms/1000;return String.format(Locale.ROOT,"%02d:%02d",s/60,s%60);}

    private void applyQuality(){
        if(selector==null||playerView==null)return;int w=Math.max(1,playerView.getWidth()),h=Math.max(1,playerView.getHeight());
        DefaultTrackSelector.Parameters.Builder b=selector.buildUponParameters().setForceHighestSupportedBitrate(qualityMode==2)
            .setMaxVideoSize(qualityMode==1?1920:qualityMode==2?4096:Integer.MAX_VALUE,qualityMode==1?1080:qualityMode==2?2160:Integer.MAX_VALUE);
        if(qualityMode==0&&w>1&&h>1)b.setViewportSize(w,h,true);else b.setViewportSize(Integer.MAX_VALUE,Integer.MAX_VALUE,true);selector.setParameters(b);
    }
    private void releasePlayer(){handler.removeCallbacks(saver);if(playerView!=null)playerView.stopHold();if(player!=null){save();playWhenReady=player.getPlayWhenReady();playerView.setPlayer(null);player.release();player=null;}selector=null;}
    private void refreshCloud(){if(video==null||!video.isCloud())return;releasePlayer();cloudResolved=false;resolveCloud();}
    private void resolveCloud(){
        if(cloudResolving||!started)return;cloudResolving=true;QuarkSession.load();status.setText("正在获取夸克原画与可用画质…");
        int token=++resolveRequest;String id=video.id,fid=video.cloudFileId;
        app.net.execute(()->{try{java.util.List<Video.Stream> streams=QuarkApi.resolve(fid);runOnUiThread(()->{
            if(isDestroyed()||token!=resolveRequest||!video.id.equals(id))return;cloudResolving=false;cloudResolved=true;video.streams.clear();video.streams.addAll(streams);
            streamIndex=CatalogLogic.resumeIndex(video);attempted.clear();initialize();
        });}catch(Exception e){runOnUiThread(()->{
            if(isDestroyed()||token!=resolveRequest)return;cloudResolving=false;status.setText("夸克暂未返回可播放地址");if(!started)return;
            new AlertDialog.Builder(this).setTitle("夸克网盘").setMessage(e instanceof QuarkApi.LoginRequired?e.getMessage():"当前文件、账号权益或网络未返回可用地址。可在夸克官方页面检查，或稍后重试。")
                .setPositiveButton("打开网盘",(d,w)->startActivity(new Intent(this,QuarkActivity.class))).setNeutralButton("重试",(d,w)->resolveCloud()).setNegativeButton("返回",(d,w)->finish()).show();
        });}});
    }
    private void chooseResources(){
        if(video==null||video.isCloud())return;Video wanted=video;int token=++sourceRequest;
        AlertDialog loading=new AlertDialog.Builder(this).setTitle("查找同名资源").setMessage("正在搜索其他片源…").setNegativeButton("取消",(d,w)->sourceRequest++).create();loading.setOnCancelListener(d->sourceRequest++);loading.show();
        if(sourceLoading!=null)sourceLoading.dismiss();sourceLoading=loading;
        app.net.execute(()->{
            java.util.List<Video> result=new java.util.ArrayList<>();for(Video item:app.repo.videos())if(!item.isCloud()&&CatalogLogic.sameTitle(wanted,item))result.add(item);
            for(Repository.Source source:app.repo.sources()){if(token!=sourceRequest)break;if(!source.kind.equals("cms")&&!source.kind.equals("hongguo"))continue;
                try{java.util.List<Video> items=Providers.fetch(source,wanted.title,1);app.repo.merge(items);for(Video item:items)if(CatalogLogic.sameTitle(wanted,item)){result.removeIf(old->old.id.equals(item.id));result.add(item);}}catch(Exception ignored){}}
            runOnUiThread(()->{if(isDestroyed())return;loading.dismiss();if(sourceLoading==loading)sourceLoading=null;if(token!=sourceRequest||!started)return;if(result.isEmpty()){Toast.makeText(this,"暂未找到同名资源",Toast.LENGTH_LONG).show();return;}
                String[] labels=new String[result.size()];for(int i=0;i<labels.length;i++){Video v=result.get(i);labels[i]=v.credit+" · "+(v.is4k()?"已识别 4K":v.qualityHint.isEmpty()?"画质待识别":v.qualityHint);}
                new AlertDialog.Builder(this).setTitle("选择播放资源").setItems(labels,(d,index)->switchResource(result.get(index))).setNegativeButton("取消",null).show();
            });
        });
    }
    private void switchResource(Video target){
        if(target.id.equals(video.id))return;long position=player==null?resumePosition:player.getCurrentPosition();String episode=video.streams.get(streamIndex).episode;
        if(target.isHongguo()){save();startActivity(new Intent(this,HongguoActivity.class).putExtra("id",target.id));finish();return;}
        releasePlayer();video=target;streamIndex=0;boolean matched=false;
        for(int i=0;i<target.streams.size();i++)if(CatalogLogic.episodeKey(target.streams.get(i).episode).equals(CatalogLogic.episodeKey(episode))){streamIndex=i;matched=true;break;}
        resumePosition=matched?position:0;attempted.clear();playWhenReady=true;getIntent().putExtra("id",video.id);build();initialize();
        if(!matched&&target.streams.size()>1){Toast.makeText(this,"分集标记不同，请选择对应集数",Toast.LENGTH_LONG).show();chooseStream();}
    }
}
