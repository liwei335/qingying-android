package io.qingying.app;

import android.app.AlertDialog;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.CookieManager;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebStorage;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.BaseAdapter;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;
import androidx.activity.ComponentActivity;
import androidx.activity.OnBackPressedCallback;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import java.util.ArrayList;
import java.util.List;

public final class QuarkActivity extends ComponentActivity {
    private QingYingApp app;
    private FrameLayout host;
    private LinearLayout root,body;
    private TextView status,crumb,more;
    private WebView web;
    private View fullScreen;
    private WebChromeClient.CustomViewCallback fullScreenCallback;
    private final List<String> folderIds=new ArrayList<>(),folderNames=new ArrayList<>();
    private final List<QuarkProtocol.Entry> entries=new ArrayList<>();
    private Files adapter;
    private int request,page;
    private boolean busy,authenticated;

    @Override public void onCreate(Bundle state){
        super.onCreate(state);app=QingYingApp.app(this);QuarkSession.load();
        folderIds.add("0");folderNames.add("我的网盘");
        host=new FrameLayout(this);host.setBackgroundColor(Ui.BG);root=Ui.col(this);host.addView(root,new FrameLayout.LayoutParams(-1,-1));setContentView(host);
        ViewCompat.setOnApplyWindowInsetsListener(root,(v,insets)->{Insets i=insets.getInsets(WindowInsetsCompat.Type.systemBars()|WindowInsetsCompat.Type.displayCutout());v.setPadding(i.left,i.top,i.right,i.bottom);return insets;});
        LinearLayout top=Ui.row(this);Ui.pad(top,12,10);top.addView(Ui.button(this,"‹ 返回",false,v->back()));
        TextView name=Ui.title(this,"夸克网盘",21);Ui.pad(name,12,0);top.addView(name,new LinearLayout.LayoutParams(0,-2,1));
        top.addView(Ui.button(this,"退出登录",false,v->logout()));root.addView(top);
        status=Ui.text(this,"登录后可浏览自己的网盘并播放原画视频。",12,Ui.MUTED);Ui.pad(status,18,8);root.addView(status);
        body=Ui.col(this);root.addView(body,new LinearLayout.LayoutParams(-1,0,1));
        getOnBackPressedDispatcher().addCallback(this,new OnBackPressedCallback(true){public void handleOnBackPressed(){back();}});
        if(QuarkSession.available())showFiles();else showWeb(QuarkSession.HOME+"/",false);
    }
    private void back(){
        if(fullScreen!=null){closeFullScreen();return;}
        if(web!=null){if(web.canGoBack()){web.goBack();return;}if(authenticated){showFiles();return;}finish();return;}
        if(folderIds.size()>1){folderIds.remove(folderIds.size()-1);folderNames.remove(folderNames.size()-1);showFiles();}else finish();
    }
    private void destroyWeb(){if(web!=null){web.stopLoading();body.removeView(web);web.destroy();web=null;}}
    private void showFiles(){
        request++;destroyWeb();body.removeAllViews();entries.clear();page=0;busy=false;
        LinearLayout actions=Ui.row(this);Ui.pad(actions,12,5);
        actions.addView(Ui.button(this,"刷新目录",false,v->showFiles()),new LinearLayout.LayoutParams(0,-2,1));
        actions.addView(Ui.button(this,"打开分享链接",false,v->shareDialog()),new LinearLayout.LayoutParams(0,-2,1));body.addView(actions);
        crumb=Ui.text(this,String.join(" / ",folderNames),13,Ui.GREEN);Ui.pad(crumb,18,12);body.addView(crumb);
        ListView list=new ListView(this);list.setDividerHeight(0);adapter=new Files();list.setAdapter(adapter);body.addView(list,new LinearLayout.LayoutParams(-1,0,1));
        list.setOnItemClickListener((parent,view,position,id)->{QuarkProtocol.Entry e=entries.get(position);
            if(e.folder){folderIds.add(e.id);folderNames.add(e.name);showFiles();}else openVideo(e);});
        more=Ui.button(this,"加载更多",false,v->loadPage());body.addView(more);loadPage();
    }
    private void loadPage(){
        if(busy)return;busy=true;int generation=++request,next=page+1;String parent=folderIds.get(folderIds.size()-1);status.setText("正在读取网盘目录…");
        app.net.execute(()->{try{QuarkApi.Page result=QuarkApi.list(parent,next);runOnUiThread(()->{
            if(isDestroyed()||generation!=request)return;busy=false;authenticated=true;page=next;entries.addAll(result.entries);adapter.notifyDataSetChanged();more.setVisibility(result.more?View.VISIBLE:View.GONE);
            status.setText(entries.isEmpty()?"此目录暂无视频或文件夹":"选择视频播放；原画与转码画质取决于文件和账号权益。");
        });}catch(Exception e){runOnUiThread(()->{if(isDestroyed()||generation!=request)return;busy=false;status.setText(e instanceof QuarkApi.LoginRequired?e.getMessage():"目录读取失败，请刷新重试");
            if(e instanceof QuarkApi.LoginRequired){authenticated=false;showWeb(QuarkSession.HOME+"/",false);}
        });}});
    }
    private void openVideo(QuarkProtocol.Entry entry){
        Video v=new Video();v.id="quark:"+FeedParser.id("quark",entry.id);v.cloudFileId=entry.id;v.source="quark";v.title=entry.name;v.category="网盘视频";v.credit="我的夸克网盘";
        v.description="原文件 "+String.format(java.util.Locale.CHINA,"%.2f GB",entry.size/1073741824d);
        Video.Stream placeholder=new Video.Stream("","网盘原画",entry.height);placeholder.width=entry.width;v.streams.add(placeholder);
        if(entry.name.toLowerCase(java.util.Locale.ROOT).matches(".*(4k|2160p|uhd).*"))v.qualityHint="文件名含 4K 标记";
        int generation=request;app.io.execute(()->{app.repo.merge(java.util.Collections.singletonList(v));runOnUiThread(()->{if(!isDestroyed()&&generation==request)startActivity(new Intent(this,PlayerActivity.class).putExtra("id",v.id));});});
    }
    @android.annotation.SuppressLint("SetJavaScriptEnabled")
    private void showWeb(String url,boolean share){
        request++;busy=false;destroyWeb();body.removeAllViews();
        status.setText(share?"夸克官方分享页面 · 转存由你在官方页面操作":"夸克官方登录页面 · 完成登录后点击下方“进入我的网盘”");
        web=new WebView(this);WebSettings settings=web.getSettings();settings.setJavaScriptEnabled(true);settings.setDomStorageEnabled(true);
        settings.setAllowFileAccess(false);settings.setAllowContentAccess(false);settings.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);
        settings.setSafeBrowsingEnabled(true);settings.setUserAgentString(QuarkSession.UA);settings.setUseWideViewPort(true);settings.setLoadWithOverviewMode(true);
        settings.setBuiltInZoomControls(true);settings.setDisplayZoomControls(false);settings.setMediaPlaybackRequiresUserGesture(true);
        CookieManager.getInstance().setAcceptCookie(true);CookieManager.getInstance().setAcceptThirdPartyCookies(web,false);
        web.setWebViewClient(new WebViewClient(){
            @Override public boolean shouldOverrideUrlLoading(WebView view,WebResourceRequest req){
                if(QuarkProtocol.credentialHost(req.getUrl().toString()))return false;
                if(req.hasGesture()&&"https".equalsIgnoreCase(req.getUrl().getScheme()))try{startActivity(new Intent(Intent.ACTION_VIEW,req.getUrl()));}catch(Exception ignored){}
                return true;
            }
        });
        web.setWebChromeClient(new WebChromeClient(){
            @Override public void onShowCustomView(View view,CustomViewCallback callback){if(fullScreen!=null){callback.onCustomViewHidden();return;}fullScreen=view;fullScreenCallback=callback;host.addView(view,new FrameLayout.LayoutParams(-1,-1));root.setVisibility(View.GONE);}
            @Override public void onHideCustomView(){closeFullScreen();}
        });
        body.addView(web,new LinearLayout.LayoutParams(-1,0,1));
        LinearLayout actions=Ui.row(this);actions.addView(Ui.button(this,"进入我的网盘",true,v->{QuarkSession.load();CookieManager.getInstance().flush();showFiles();}),new LinearLayout.LayoutParams(0,-2,1));
        actions.addView(Ui.button(this,"系统浏览器打开",false,v->{try{startActivity(new Intent(Intent.ACTION_VIEW,Uri.parse(url)));}catch(Exception ignored){}}),new LinearLayout.LayoutParams(0,-2,1));
        body.addView(actions);body.addView(Ui.text(this,"系统浏览器的登录态与本应用不共享。",11,Ui.MUTED));web.loadUrl(url);
    }
    private void closeFullScreen(){if(fullScreen!=null){host.removeView(fullScreen);fullScreen=null;root.setVisibility(View.VISIBLE);if(fullScreenCallback!=null){fullScreenCallback.onCustomViewHidden();fullScreenCallback=null;}}}
    private void shareDialog(){EditText text=new EditText(this);text.setHint("粘贴 https://pan.quark.cn/s/… 分享链接");
        AlertDialog d=new AlertDialog.Builder(this).setTitle("打开夸克分享").setView(text).setNegativeButton("取消",null).setPositiveButton("打开",null).create();
        d.setOnShowListener(x->d.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v->{
            java.util.regex.Matcher m=java.util.regex.Pattern.compile("https://pan\\.quark\\.cn/s/[A-Za-z0-9]+(?:\\?[^\\s]*)?").matcher(text.getText());
            if(!m.find()){text.setError("未找到有效夸克分享链接");return;}String url=m.group();d.dismiss();showWeb(url,true);
        }));d.show();
    }
    private void logout(){new AlertDialog.Builder(this).setTitle("退出夸克网盘")
        .setMessage("清除本应用的夸克登录态、网盘收藏和播放记录。网盘中的文件不会改变。")
        .setNegativeButton("取消",null).setPositiveButton("退出并清除",(d,w)->{
            request++;authenticated=false;closeFullScreen();destroyWeb();WebStorage.getInstance().deleteAllData();
            QuarkSession.clear(()->app.io.execute(()->{app.repo.removeSource("quark");runOnUiThread(()->{if(isDestroyed())return;folderIds.clear();folderNames.clear();folderIds.add("0");folderNames.add("我的网盘");showWeb(QuarkSession.HOME+"/",false);});}));
        }).show();}
    @Override protected void onPause(){if(web!=null)web.onPause();super.onPause();}
    @Override protected void onResume(){super.onResume();if(web!=null)web.onResume();}
    @Override protected void onDestroy(){request++;closeFullScreen();destroyWeb();super.onDestroy();}
    private final class Files extends BaseAdapter {
        public int getCount(){return entries.size();}public Object getItem(int p){return entries.get(p);}public long getItemId(int p){return p;}
        public View getView(int p,View old,ViewGroup parent){QuarkProtocol.Entry e=entries.get(p);LinearLayout row=Ui.col(QuarkActivity.this);Ui.pad(row,20,16);
            row.addView(Ui.title(QuarkActivity.this,(e.folder?"▣  ":"▶  ")+e.name,15));
            row.addView(Ui.text(QuarkActivity.this,e.folder?"文件夹":String.format(java.util.Locale.CHINA,"%.1f MB · 点击播放",e.size/1048576d),12,Ui.MUTED),Ui.margin(QuarkActivity.this,-1,-2,8));return row;}
    }
}
