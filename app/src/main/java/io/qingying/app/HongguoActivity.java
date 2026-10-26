package io.qingying.app;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.view.WindowManager;
import android.webkit.CookieManager;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import androidx.activity.ComponentActivity;
import androidx.activity.OnBackPressedCallback;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsControllerCompat;
import org.json.JSONObject;
import org.json.JSONTokener;

/** Runs the official player and its ordinary access flow; does not substitute SEO previews for episodes. */
public final class HongguoActivity extends ComponentActivity {
    private QingYingApp app;
    private Video video;
    private FrameLayout host;
    private LinearLayout root;
    private WebView web;
    private TextView status;
    private View fullScreen;
    private WebChromeClient.CustomViewCallback fullScreenCallback;
    private boolean started,restored;
    private int navigation;
    private final Handler handler=new Handler(Looper.getMainLooper());
    private final Runnable ticker=new Runnable(){public void run(){captureProgress();if(started)handler.postDelayed(this,3000);}};
    @Override public void onCreate(Bundle state){
        super.onCreate(state);app=QingYingApp.app(this);getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        String id=getIntent().getStringExtra("id");
        app.io.execute(()->{Video loaded=app.repo.get(id);runOnUiThread(()->{
            if(isDestroyed()||isFinishing())return;if(loaded==null||!loaded.isHongguo()){finish();return;}video=loaded;build();
        });});
        getOnBackPressedDispatcher().addCallback(this,new OnBackPressedCallback(true){public void handleOnBackPressed(){if(fullScreen!=null)closeFullScreen();else if(web!=null&&web.canGoBack())web.goBack();else finish();}});
    }
    @android.annotation.SuppressLint("SetJavaScriptEnabled")
    private void build(){
        host=new FrameLayout(this);host.setBackgroundColor(Ui.BG);root=Ui.col(this);host.addView(root,new FrameLayout.LayoutParams(-1,-1));setContentView(host);
        ViewCompat.setOnApplyWindowInsetsListener(root,(v,insets)->{Insets i=insets.getInsets(WindowInsetsCompat.Type.systemBars()|WindowInsetsCompat.Type.displayCutout());v.setPadding(i.left,i.top,i.right,i.bottom);return insets;});
        LinearLayout top=Ui.row(this);top.addView(Ui.button(this,"‹ 返回",false,v->finish()));
        TextView title=Ui.title(this,"红果官方网页",16);Ui.pad(title,10,0);top.addView(title,new LinearLayout.LayoutParams(0,-2,1));
        top.addView(Ui.button(this,"浏览器",false,v->openBrowser()));root.addView(top);
        status=Ui.text(this,"正在打开官方播放器…",12,Ui.MUTED);Ui.pad(status,14,8);root.addView(status);
        web=new WebView(this);WebSettings settings=web.getSettings();settings.setJavaScriptEnabled(true);settings.setDomStorageEnabled(true);settings.setAllowFileAccess(false);settings.setAllowContentAccess(false);
        settings.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);settings.setSafeBrowsingEnabled(true);settings.setUseWideViewPort(true);settings.setLoadWithOverviewMode(true);settings.setMediaPlaybackRequiresUserGesture(true);
        CookieManager.getInstance().setAcceptCookie(true);CookieManager.getInstance().setAcceptThirdPartyCookies(web,false);
        web.setWebViewClient(new WebViewClient(){
            @Override public void onPageStarted(WebView view,String url,android.graphics.Bitmap icon){navigation++;restored=false;status.setText("红果官方播放器 · 可在网页内选集与全屏播放");}
            @Override public boolean shouldOverrideUrlLoading(WebView view,WebResourceRequest request){
                String url=request.getUrl().toString();if(Hongguo.officialUrl(url))return false;
                if(request.hasGesture()&&"https".equalsIgnoreCase(request.getUrl().getScheme()))try{startActivity(new Intent(Intent.ACTION_VIEW,request.getUrl()));}catch(Exception ignored){}
                return true;
            }
            @Override public void onReceivedError(WebView view,WebResourceRequest request,WebResourceError error){if(request.isForMainFrame())status.setText("网页暂时无法加载，可重试或使用系统浏览器");}
        });
        web.setWebChromeClient(new WebChromeClient(){
            @Override public void onShowCustomView(View view,CustomViewCallback callback){if(fullScreen!=null){callback.onCustomViewHidden();return;}fullScreen=view;fullScreenCallback=callback;root.setVisibility(View.GONE);host.addView(view,new FrameLayout.LayoutParams(-1,-1));
                WindowInsetsControllerCompat controller=WindowCompat.getInsetsController(getWindow(),host);controller.setSystemBarsBehavior(WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);controller.hide(WindowInsetsCompat.Type.systemBars());}
            @Override public void onHideCustomView(){closeFullScreen();}
        });
        root.addView(web,new LinearLayout.LayoutParams(-1,0,1));root.addView(Ui.button(this,"重新加载",false,v->web.reload()));web.loadUrl(Hongguo.playbackUrl(video));
        if(!started)web.onPause();
    }
    private void openBrowser(){String url=web==null?Hongguo.playbackUrl(video):web.getUrl();if(!Hongguo.officialUrl(url))url=Hongguo.playbackUrl(video);try{startActivity(new Intent(Intent.ACTION_VIEW,Uri.parse(url)));}catch(Exception ignored){Toast.makeText(this,"未找到浏览器",Toast.LENGTH_SHORT).show();}}
    private void closeFullScreen(){if(fullScreen!=null){host.removeView(fullScreen);fullScreen=null;root.setVisibility(View.VISIBLE);if(fullScreenCallback!=null){fullScreenCallback.onCustomViewHidden();fullScreenCallback=null;}WindowCompat.getInsetsController(getWindow(),host).show(WindowInsetsCompat.Type.systemBars());}}
    private void captureProgress(){
        if(web==null||video==null)return;String page=Hongguo.savedPlayerUrl(web.getUrl(),video.hongguoSeriesId);if(page.isEmpty())return;int token=navigation;
        web.evaluateJavascript("(function(){var v=document.querySelector('video');return v?JSON.stringify({time:v.currentTime,duration:v.duration,paused:v.paused,ended:v.ended,src:v.currentSrc,label:v.getAttribute('aria-label')||''}):null})()",value->{
            if(isDestroyed()||web==null||token!=navigation||!page.equals(Hongguo.savedPlayerUrl(web.getUrl(),video.hongguoSeriesId)))return;
            try{Object raw=new JSONTokener(value).nextValue();if(!(raw instanceof String))return;JSONObject data=new JSONObject((String)raw);
                double seconds=data.optDouble("time",0),length=data.optDouble("duration",0);if(!Double.isFinite(seconds)||!Double.isFinite(length)||length<=0||length>172800||Hongguo.preview(data.optString("src")))return;
                if(!restored&&page.equals(Hongguo.playbackUrl(video))&&video.position>0&&video.position<length*1000){restored=true;web.evaluateJavascript("(function(){var v=document.querySelector('video');if(v)v.currentTime="+(video.position/1000d)+"})()",null);return;}restored=true;
                if(seconds<=0||data.optBoolean("paused")&&!data.optBoolean("ended"))return;
                long position=data.optBoolean("ended")?0:Math.min((long)(seconds*1000),(long)(length*1000)),duration=(long)(length*1000);boolean completed=data.optBoolean("ended");
                java.util.regex.Matcher episode=java.util.regex.Pattern.compile("第[0-9]+集").matcher(data.optString("label"));String label=episode.find()?episode.group():"网页播放";
                String id=video.id;video.position=position;video.lastStream=page;video.lastEpisode=label;
                app.io.execute(()->{app.repo.progress(id,page,label,position,duration,completed);app.repo.played(id);});
            }catch(Exception ignored){}
        });
    }
    @Override protected void onStart(){super.onStart();started=true;if(web!=null)web.onResume();handler.post(ticker);}
    @Override protected void onStop(){captureProgress();started=false;handler.removeCallbacks(ticker);if(web!=null){web.evaluateJavascript("document.querySelectorAll('video').forEach(function(v){v.pause()})",null);web.onPause();}super.onStop();}
    @Override protected void onDestroy(){handler.removeCallbacksAndMessages(null);closeFullScreen();if(web!=null){web.stopLoading();root.removeView(web);web.destroy();web=null;}super.onDestroy();}
}
