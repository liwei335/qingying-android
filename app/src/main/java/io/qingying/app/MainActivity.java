package io.qingying.app;

import android.app.AlertDialog;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;
import androidx.activity.ComponentActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;
import androidx.recyclerview.widget.ConcatAdapter;
import androidx.work.WorkInfo;
import androidx.work.WorkManager;
import org.json.JSONArray;
import org.json.JSONObject;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public final class MainActivity extends ComponentActivity {
    private QingYingApp app;
    private LinearLayout root, content, nav;
    private TextView status, empty, count;
    private EditText search;
    private Cards adapter;
    private final List<Video> videos = new ArrayList<>();
    private List<Repository.Source> sources = new ArrayList<>();
    private final java.util.Map<String,List<Video>> trends=new java.util.HashMap<>();
    private final java.util.Map<String,Long> trendTimes=new java.util.HashMap<>();
    private final java.util.Map<String,String> trendErrors=new java.util.HashMap<>();
    private int tab, filter, page = 1;
    private boolean searching;
    private String workStatus = "", query = "";
    private static final int HOME=0,LIBRARY=1,HISTORY=2,MINE=3,SETTINGS=4;
    private String selectedSource="",selectedTrendProvider="";
    private List<Video> playbackHistory=new ArrayList<>();
    private List<String> searchHistory=new ArrayList<>();
    private LinearLayout searchHistoryPreview,categoryChips;
    private HorizontalScrollView categoryScroller;
    private TextView trendProviderButton,sourceButton;
    private volatile int resourceRequest,searchRequest;
    private String resourceStatus="";
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable filterTask = this::filter;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state); app = QingYingApp.app(this);
        selectedSource=QingYingApp.prefs(this).getString("selectedSource","");
        if (state != null) { tab=state.getInt("tab"); filter=state.getInt("filter"); query=state.getString("query","");selectedTrendProvider=state.getString("trendProvider",""); }
        root = Ui.col(this); root.setBackgroundColor(Ui.BG); setContentView(root);
        ViewCompat.setOnApplyWindowInsetsListener(root,(v,insets) -> {
            Insets i=insets.getInsets(WindowInsetsCompat.Type.systemBars() | WindowInsetsCompat.Type.displayCutout());
            v.setPadding(i.left,i.top,i.right,i.bottom); return insets;
        });
        content=Ui.col(this); root.addView(content,new LinearLayout.LayoutParams(-1,0,1));
        nav=Ui.row(this); Ui.pad(nav,12,6); root.addView(nav,new LinearLayout.LayoutParams(-1,-2));
        showTab(tab);
        WorkManager.getInstance(this).getWorkInfosByTagLiveData("catalog").observe(this,infos -> {
            String next="";
            for (WorkInfo info:infos) if(info.getState()==WorkInfo.State.RUNNING) {
                next=info.getProgress().getString("message"); if(next==null) next="正在更新片源…"; break;
            }
            boolean changed=!workStatus.equals(next); workStatus=next; updateStatus(); if(changed) reload();
        });
        if(System.currentTimeMillis()-QingYingApp.prefs(this).getLong("checkedAt",0)>24*60*60*1000L) QingYingApp.refresh(this);
        app.io.execute(()->{boolean empty=false;for(RecommendationFeeds.Feed feed:RecommendationFeeds.ALL)if(app.repo.recommendationTime(feed.id)==0)empty=true;
            for(Repository.Source source:app.repo.sources())if(source.synced==0)empty=true;
            if(empty)runOnUiThread(()->{if(!isDestroyed())QingYingApp.refresh(this);});});
    }
    @Override protected void onResume() { super.onResume(); reload(); }
    @Override protected void onSaveInstanceState(Bundle out) { super.onSaveInstanceState(out); out.putInt("tab",tab);out.putInt("filter",filter);out.putString("query",query);out.putString("trendProvider",selectedTrendProvider); }
    @Override protected void onDestroy() { resourceRequest++;cancelSearch();handler.removeCallbacks(filterTask); super.onDestroy(); }
    @Override protected void onStop(){resourceRequest++;resourceStatus="";cancelSearch();super.onStop();}
    private void reload() {
        app.io.execute(() -> {
            List<Video> loaded=app.repo.videos(),watched=app.repo.playbackHistory();List<String> searches=app.repo.searchHistory(); List<Repository.Source> src=app.repo.sources();
            java.util.Map<String,List<Video>> ranked=new java.util.HashMap<>();java.util.Map<String,Long> times=new java.util.HashMap<>();java.util.Map<String,String> errors=new java.util.HashMap<>();
            for(String group:Recommendations.GROUPS){
                List<Video> entries=new ArrayList<>();long oldest=0;List<String> unavailable=new ArrayList<>();
                for(RecommendationFeeds.Feed feed:RecommendationFeeds.forGroup(group)){
                    List<Video> list=app.repo.recommendations(feed.id);
                    for(Video v:list){if(v.trendFeed.isEmpty())v.trendFeed=feed.id;if(v.trendProvider.isEmpty())v.trendProvider=feed.provider;}
                    entries.addAll(list);long at=app.repo.recommendationTime(feed.id);if(at>0)oldest=oldest==0?at:Math.min(oldest,at);
                    if((at==0||!app.repo.recommendationError(feed.id).isEmpty())&&!unavailable.contains(feed.provider))unavailable.add(feed.provider);
                }
                ranked.put(group,entries);times.put(group,oldest);errors.put(group,String.join("、",unavailable));
            }
            runOnUiThread(() -> { if(isDestroyed())return; videos.clear();videos.addAll(loaded);sources=src;playbackHistory=watched;searchHistory=searches;
                trends.clear();trends.putAll(ranked);trendTimes.clear();trendTimes.putAll(times);trendErrors.clear();trendErrors.putAll(errors);
                boolean exists=selectedSource.isEmpty()||selectedSource.equals("builtin");for(Repository.Source s:src)if(s.url.equals(selectedSource))exists=true;if(!exists)selectedSource="";
                if(tab==SETTINGS)sourceScreen();else if(tab==HISTORY)historyScreen();else{filter();renderSearchPreview();}updateStatus(); });
        });
    }
    private void showTab(int index) {
        resourceRequest++;resourceStatus="";
        if(tab!=index){filter=0;cancelSearch();}tab=index; content.removeAllViews(); nav.removeAllViews();
        String[] names={"热播","片库","记录","我的","设置"};
        for(int i=0;i<names.length;i++) { final int n=i;
            TextView b=Ui.button(this,names[i],i==tab,v->showTab(n));
            Ui.pad(b,2,8);
            LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(0,Ui.dp(this,50),1);p.setMargins(Ui.dp(this,3),0,Ui.dp(this,3),0);nav.addView(b,p);
        }
        if(tab==SETTINGS)sourceScreen();else if(tab==HISTORY)historyScreen();else libraryScreen();
    }
    private void libraryScreen() {
        LinearLayout header=Ui.col(this); Ui.pad(header,22,14); content.addView(header);
        LinearLayout brand=Ui.row(this);
        brand.addView(Ui.title(this,tab==HOME?"清影 · 热播":tab==LIBRARY?"资源片库":"我的片单",28),new LinearLayout.LayoutParams(0,-2,1));
        brand.addView(Ui.button(this,"↻ 更新",false,v->{workStatus="等待网络，准备更新…";updateStatus();QingYingApp.refresh(this);}));header.addView(brand);
        TextView subtitle=Ui.text(this,tab==HOME?"多平台推荐 · 中国热播优先":tab==LIBRARY?"选择资源与画质，找到合适的播放线路":"喜欢的内容，接着看。",13,Ui.MUTED);header.addView(subtitle,Ui.margin(this,-1,-2,8));
        status=Ui.text(this,"正在读取本地片单…",11,Ui.MUTED);header.addView(status,Ui.margin(this,-1,-2,12));
        LinearLayout input=Ui.row(this); input.setBackground(Ui.shape(this,Ui.CARD,16));Ui.pad(input,12,2);
        search=new EditText(this);search.setSingleLine();search.setTextSize(14);search.setTextColor(Ui.TEXT);search.setHintTextColor(Ui.MUTED);
        search.setHint("搜索片名、类型");search.setBackgroundColor(android.graphics.Color.TRANSPARENT);search.setText(query);
        input.addView(search,new LinearLayout.LayoutParams(0,Ui.dp(this,48),1));
        input.addView(Ui.button(this,"搜索资源",false,v->onlineSearch()));header.addView(input,Ui.margin(this,-1,-2,16));
        search.setImeOptions(android.view.inputmethod.EditorInfo.IME_ACTION_SEARCH);
        search.setOnEditorActionListener((v,action,event)->{if(action==android.view.inputmethod.EditorInfo.IME_ACTION_SEARCH){onlineSearch();return true;}return false;});
        searchHistoryPreview=Ui.col(this);header.addView(searchHistoryPreview);renderSearchPreview();
        search.addTextChangedListener(new TextWatcher(){public void beforeTextChanged(CharSequence s,int a,int c,int f){}
            public void onTextChanged(CharSequence s,int a,int b,int c){query=s.toString();renderSearchPreview();handler.removeCallbacks(filterTask);handler.postDelayed(filterTask,160);}
            public void afterTextChanged(Editable e){} });
        HorizontalScrollView scroller=new HorizontalScrollView(this);scroller.setHorizontalScrollBarEnabled(false);categoryScroller=scroller;
        LinearLayout sourceRow=Ui.row(this);
        sourceButton=Ui.button(this,(tab==HOME?"搜索片源：":"资源：")+sourceName(selectedSource),false,v->chooseSource());sourceRow.addView(sourceButton,new LinearLayout.LayoutParams(0,-2,1));
        sourceRow.addView(Ui.button(this,"夸克原画",false,v->openQuark()));header.addView(sourceRow,Ui.margin(this,-1,-2,8));
        LinearLayout chips=Ui.row(this);categoryChips=chips;String[] labels=tab==HOME?new String[]{"热播剧集","热门电影","动漫推荐","热播短剧"}:tab==LIBRARY?new String[]{"最近更新","4K 专区","已识别 4K","电影","剧集","动漫","短剧","链接可达"}:new String[]{"我的收藏","继续观看"};
        if(filter>=labels.length)filter=0;
        for(int i=0;i<labels.length;i++){final int n=i;TextView b=Ui.button(this,labels[i],filter==i,v->selectFilter(n));
            LinearLayout.LayoutParams p=Ui.margin(this,-2,44,0);p.rightMargin=Ui.dp(this,8);chips.addView(b,p);}
        scroller.addView(chips);header.addView(scroller,Ui.margin(this,-1,-2,14));
        LinearLayout summary=Ui.row(this);count=Ui.text(this,"",12,Ui.MUTED);summary.addView(count,new LinearLayout.LayoutParams(0,-2,1));
        if(tab==LIBRARY) summary.addView(Ui.button(this,"更多",false,v->more()));header.addView(summary,Ui.margin(this,-1,-2,10));
        if(tab==HOME){if(!selectedTrendProvider.isEmpty()&&!RecommendationFeeds.providers(currentGroup()).contains(selectedTrendProvider))selectedTrendProvider="";
            trendProviderButton=Ui.button(this,"榜单来源："+(selectedTrendProvider.isEmpty()?"全部平台":selectedTrendProvider),false,v->chooseTrendProvider());header.addView(trendProviderButton,Ui.margin(this,-1,-2,8));}
        FrameLayout frame=new FrameLayout(this);content.addView(frame,new LinearLayout.LayoutParams(-1,0,1));
        RecyclerView list=new RecyclerView(this);GridLayoutManager grid=new GridLayoutManager(this,1);list.setLayoutManager(grid);list.setItemAnimator(null);
        list.addOnLayoutChangeListener((view,l,t,r,b,ol,ot,or,ob)->{int span=CatalogLogic.columns((int)((r-l-list.getPaddingLeft()-list.getPaddingRight())/getResources().getDisplayMetrics().density));if(span!=grid.getSpanCount())grid.setSpanCount(span);});
        list.setPadding(Ui.dp(this,18),0,Ui.dp(this,18),Ui.dp(this,12));list.setClipToPadding(false);
        adapter=new Cards();frame.addView(list,new FrameLayout.LayoutParams(-1,-1));
        empty=Ui.text(this,"",15,Ui.MUTED);empty.setGravity(Gravity.CENTER);Ui.pad(empty,28,36);
        content.removeView(header);
        list.setAdapter(new ConcatAdapter(new StaticRow(header),adapter,new StaticRow(empty)));
        grid.setSpanSizeLookup(new GridLayoutManager.SpanSizeLookup(){public int getSpanSize(int position){return position==0||position==adapter.getItemCount()+1?grid.getSpanCount():1;}});
        filter();updateStatus();
    }
    private void selectFilter(int selected){
        if(selected==filter)return;filter=selected;resourceRequest++;resourceStatus="";
        for(int i=0;i<categoryChips.getChildCount();i++)Ui.buttonPrimary((TextView)categoryChips.getChildAt(i),i==filter);
        if(tab==HOME){if(!selectedTrendProvider.isEmpty()&&!RecommendationFeeds.providers(currentGroup()).contains(selectedTrendProvider))selectedTrendProvider="";
            trendProviderButton.setText("榜单来源："+(selectedTrendProvider.isEmpty()?"全部平台":selectedTrendProvider));}
        // Keep the header, both scroll containers and the adapter alive when changing categories.
        filter();
    }
    private void filter() {
        if(tab==SETTINGS||tab==HISTORY||adapter==null)return;
        String q=query.trim().toLowerCase(Locale.ROOT);List<Video> shown=new ArrayList<>();boolean ranked=tab==HOME;String group=currentGroup();
        List<Video> candidates=ranked?Recommendations.combine(trends.getOrDefault(group,java.util.Collections.emptyList()),selectedTrendProvider):tab==MINE&&filter==1?playbackHistory:videos;
        for(Video v:candidates){
            if(!q.isEmpty()&&!(v.title+v.category).toLowerCase(Locale.ROOT).contains(q))continue;
            if(tab==LIBRARY&&!selectedSource.isEmpty()&&!v.source.equals(selectedSource))continue;
            if(tab==MINE){if(filter==0&&!v.favorite)continue;if(filter==1&&(v.position<=0||v.completed))continue;}
            else if(!ranked){if(v.missing()||v.isCloud())continue;
                if(filter==1&&!v.is4k()&&v.qualityHint.isEmpty())continue;if(filter==2&&!v.is4k())continue;
                if(filter==3&&!(v.category.contains("片")&&!v.category.contains("连续")))continue;
                if(filter==4&&!(v.category.contains("剧")||v.category.contains("连续")))continue;
                if(filter==5&&!CatalogLogic.anime(v))continue;if(filter==6&&!v.category.contains("短剧"))continue;
                if(filter==7&&!v.reachable())continue;}
            shown.add(v);
        }
        adapter.submitList(shown);count.setText(shown.size()+" 部内容"+(ranked?" · 中国影视优先 · 多平台推荐":tab==MINE&&filter==1?" · 最近观看在前":tab==MINE?"":" · 按片源更新时间排序"));
        empty.setVisibility(shown.isEmpty()?View.VISIBLE:View.GONE);
        empty.setText(tab==MINE?"还没有内容\n\n长按影片收藏，播放后自动记录进度。":ranked?"暂无推荐数据\n\n点击更新重试，或切换榜单来源。":"暂无匹配内容\n\n可尝试搜索、更新片源或更换资源。");updateStatus();
    }
    private String currentGroup(){return Recommendations.GROUPS[Math.max(0,Math.min(filter,Recommendations.GROUPS.length-1))];}
    private void chooseTrendProvider(){
        List<String> names=new ArrayList<>();names.add("全部平台");names.addAll(RecommendationFeeds.providers(currentGroup()));
        new AlertDialog.Builder(this).setTitle("推荐来源").setSingleChoiceItems(names.toArray(new String[0]),selectedTrendProvider.isEmpty()?0:names.indexOf(selectedTrendProvider),(d,n)->{
            selectedTrendProvider=n==0?"":names.get(n);d.dismiss();trendProviderButton.setText("榜单来源："+(selectedTrendProvider.isEmpty()?"全部平台":selectedTrendProvider));filter();
        }).setNegativeButton("关闭",null).show();
    }
    private void updateStatus(){if(status==null||tab==SETTINGS||tab==HISTORY)return;
        if(!resourceStatus.isEmpty()){status.setText(resourceStatus);return;}
        if(!workStatus.isEmpty()){status.setText(workStatus);return;}
        if(tab==HOME){String group=currentGroup();long at=trendTimes.getOrDefault(group,0L);String error=trendErrors.getOrDefault(group,"");
            status.setText(at==0?"等待获取近期热榜":(error.isEmpty()?"榜单更新 ":"部分榜单保留缓存 · ")+time(at)+(System.currentTimeMillis()-at>48*60*60*1000L?" · 缓存已过期":""));return;}
        long time=QingYingApp.prefs(this).getLong("checkedAt",0);
        status.setText(time==0?"首次联网将更新片单 · 可先试播 4K 样片":"上次检查 "+time(time)+" · 链接状态仅供参考");}
    static String time(long millis){return new SimpleDateFormat("MM-dd HH:mm",Locale.CHINA).format(new Date(millis));}
    private void onlineSearch(){
        String term=query.trim();if(term.isEmpty()){toast("先输入片名");return;}
        InputMethodManager imm=(InputMethodManager)getSystemService(INPUT_METHOD_SERVICE);imm.hideSoftInputFromWindow(search.getWindowToken(),0);
        app.io.execute(()->{app.repo.rememberSearch(term);List<String> items=app.repo.searchHistory();runOnUiThread(()->{if(!isDestroyed()){searchHistory=items;renderSearchPreview();}});});
        showTab(LIBRARY);searchRemote(term,1);
    }
    private void more(){if(searching)return;searchRemote("",++page);}
    private void cancelSearch(){searchRequest++;if(searching)workStatus="";searching=false;}
    private void searchRemote(String term,int requestedPage){
        int token=++searchRequest;searching=true;workStatus=term.isEmpty()?"加载更多影片…":"正在搜索"+sourceName(selectedSource)+"…";updateStatus();
        String source=selectedSource;
        app.net.execute(()->{int found=0,ok=0;
            for(Repository.Source s:app.repo.sources())if(token==searchRequest&&(s.kind.equals("cms")||s.kind.equals("hongguo"))&&(source.isEmpty()||source.equals(s.url)))try{List<Video> result=Providers.fetch(s,term,requestedPage);app.repo.merge(result);found+=result.size();ok++;}catch(Exception ignored){}
            int total=found,success=ok;runOnUiThread(()->{if(isDestroyed()||token!=searchRequest)return;searching=false;workStatus="";
                toast(success==0?"片源暂时无法访问，请稍后重试":"已获取 "+total+" 条内容");reload();});
        });
    }
    private void renderSearchPreview(){
        if(searchHistoryPreview==null||tab==HISTORY||tab==SETTINGS)return;searchHistoryPreview.removeAllViews();
        if(!query.trim().isEmpty()||searchHistory.isEmpty())return;
        LinearLayout heading=Ui.row(this);heading.addView(Ui.text(this,"最近搜索",12,Ui.MUTED),new LinearLayout.LayoutParams(0,-2,1));
        heading.addView(Ui.button(this,"全部记录",false,v->{showTab(HISTORY);filter=1;historyScreen();}));searchHistoryPreview.addView(heading);
        HorizontalScrollView scroll=new HorizontalScrollView(this);scroll.setHorizontalScrollBarEnabled(false);LinearLayout chips=Ui.row(this);
        for(String term:searchHistory.subList(0,Math.min(5,searchHistory.size()))){TextView chip=Ui.button(this,term,false,v->repeatSearch(term));LinearLayout.LayoutParams p=Ui.margin(this,-2,48,4);p.rightMargin=Ui.dp(this,6);chips.addView(chip,p);}
        scroll.addView(chips);searchHistoryPreview.addView(scroll);
    }
    private void repeatSearch(String term){query=term;showTab(LIBRARY);onlineSearch();}
    private void historyScreen(){
        content.removeAllViews();status=null;searchHistoryPreview=null;filter=filter==1?1:0;
        LinearLayout header=Ui.col(this);Ui.pad(header,22,16);content.addView(header);
        LinearLayout title=Ui.row(this);title.addView(Ui.title(this,"观看与搜索记录",24),new LinearLayout.LayoutParams(0,-2,1));
        title.addView(Ui.button(this,"清空",false,v->clearHistory()));header.addView(title);
        LinearLayout tabs=Ui.row(this);String[] names={"播放记录","搜索记录"};
        for(int i=0;i<names.length;i++){final int n=i;tabs.addView(Ui.button(this,names[i],filter==i,v->{filter=n;historyScreen();}),new LinearLayout.LayoutParams(0,Ui.dp(this,48),1));}
        header.addView(tabs,Ui.margin(this,-1,-2,16));header.addView(Ui.text(this,filter==0?"最近观看在前 · 点击继续播放":"最近搜索在前 · 点击再次搜索",12,Ui.MUTED),Ui.margin(this,-1,-2,10));
        if(filter==0){
            RecyclerView list=new RecyclerView(this);GridLayoutManager grid=new GridLayoutManager(this,CatalogLogic.columns(getResources().getConfiguration().screenWidthDp));list.setLayoutManager(grid);list.setItemAnimator(null);Ui.pad(list,18,8);
            list.addOnLayoutChangeListener((v,l,t,r,b,ol,ot,or,ob)->{int span=CatalogLogic.columns((int)((r-l)/getResources().getDisplayMetrics().density));if(span!=grid.getSpanCount())grid.setSpanCount(span);});
            adapter=new Cards();list.setAdapter(adapter);adapter.submitList(new ArrayList<>(playbackHistory));content.addView(list,new LinearLayout.LayoutParams(-1,0,1));
            if(playbackHistory.isEmpty()){TextView message=Ui.text(this,"还没有播放记录，开始观看后会自动保存。",14,Ui.MUTED);Ui.pad(message,22,24);content.addView(message);}
        }else{
            ScrollView scroll=new ScrollView(this);LinearLayout rows=Ui.col(this);Ui.pad(rows,22,8);scroll.addView(rows);content.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));
            if(searchHistory.isEmpty())rows.addView(Ui.text(this,"还没有搜索记录，搜索片名后会保存在这里。",14,Ui.MUTED));
            for(String term:searchHistory){LinearLayout row=Ui.row(this);TextView text=Ui.button(this,term,false,v->repeatSearch(term));text.setSingleLine();text.setEllipsize(TextUtils.TruncateAt.END);text.setGravity(Gravity.START|Gravity.CENTER_VERTICAL);row.addView(text,new LinearLayout.LayoutParams(0,-2,1));
                TextView remove=Ui.button(this,"删除",false,v->app.io.execute(()->{app.repo.removeSearch(term);runOnUiThread(this::reload);}));remove.setContentDescription("删除搜索记录："+term);row.addView(remove);rows.addView(row,Ui.margin(this,-1,-2,8));}
        }
    }
    private void clearHistory(){
        boolean searches=filter==1;
        new AlertDialog.Builder(this).setTitle(searches?"清空搜索记录":"清空播放记录")
            .setMessage(searches?"清除本机保存的搜索词。":"清除观看历史和续播位置，收藏的影片仍会保留。")
            .setNegativeButton("取消",null).setPositiveButton("清空",(d,w)->app.io.execute(()->{if(searches)app.repo.clearSearchHistory();else app.repo.clearPlaybackHistory();runOnUiThread(this::reload);})).show();
    }
    private void sourceScreen(){
        content.removeAllViews();ScrollView scroll=new ScrollView(this);LinearLayout body=Ui.col(this);Ui.pad(body,22,22);scroll.addView(body);content.addView(scroll);
        body.addView(Ui.title(this,"片源与设置",28));body.addView(Ui.text(this,"自动更新，保留选择。",14,Ui.MUTED),Ui.margin(this,-1,-2,10));
        body.addView(Ui.button(this,"＋ 添加片源",true,v->addSourceDialog()),Ui.margin(this,-1,-2,24));
        body.addView(Ui.button(this,"打开视频直链",false,v->directDialog()),Ui.margin(this,-1,-2,10));
        body.addView(Ui.button(this,"夸克网盘 · 登录与原画播放",true,v->openQuark()),Ui.margin(this,-1,-2,10));
        for(Repository.Source s:sources){LinearLayout box=Ui.col(this);Ui.pad(box,16,16);box.setBackground(Ui.shape(this,Ui.CARD,16));
            box.addView(Ui.title(this,s.name,16));TextView url=Ui.text(this,s.url,11,Ui.MUTED);url.setMaxLines(2);box.addView(url,Ui.margin(this,-1,-2,8));
            box.addView(Ui.text(this,!s.error.isEmpty()?"暂未更新："+s.error:s.synced==0?"等待首次更新":"已更新 "+time(s.synced),12,s.error.isEmpty()?Ui.GREEN:Ui.GOLD),Ui.margin(this,-1,-2,10));
            box.addView(Ui.button(this,"移除此片源",false,v->new AlertDialog.Builder(this).setTitle("移除片源")
                .setMessage("将移除该片源的片单、收藏和记录。").setNegativeButton("取消",null)
                .setPositiveButton("移除",(d,w)->app.io.execute(()->{app.repo.removeSource(s.url);runOnUiThread(this::reload);})).show()),Ui.margin(this,-1,-2,10));
            body.addView(box,Ui.margin(this,-1,-2,16));}
        body.addView(Ui.title(this,"自动维护",20),Ui.margin(this,-1,-2,28));
        toggle(body,"每天自动更新并检查资源","daily",true);toggle(body,"自动检查仅使用 Wi-Fi / 非计费网络","wifi",true);
        TextView note=Ui.text(this,"约每 24 小时执行一次；系统省电、强行停止和网络条件可能延后任务。每轮最多检查 120 条地址，较旧条目轮换检查；所有分集在播放时再次连接。",12,Ui.MUTED);
        note.setLineSpacing(Ui.dp(this,4),1);body.addView(note,Ui.margin(this,-1,-2,10));
        body.addView(Ui.button(this,"立即更新并检查",true,v->{QingYingApp.refresh(this);toast("任务已加入，将在有网络时运行");}),Ui.margin(this,-1,-2,16));
        body.addView(Ui.title(this,"关于清影",20),Ui.margin(this,-1,-2,28));
        TextView about=Ui.text(this,"0.5.0 · Android 8.0+\n\n应用无广告 SDK、无行为统计。夸克登录在官方页面完成；登录态和播放记录保存在本应用内，不上传到清影服务器。\n\n推荐聚合红果短剧、腾讯视频、爱奇艺、哔哩哔哩、芒果TV及豆瓣公开内容，中国影视优先，点击后自动查找资源并播放。4K 专区标注与实际分辨率分开显示。夸克原画和转码清晰度由文件与账号权益决定。\n\n按住视频临时 2 倍速，松手恢复。上下滑动调音量，左右滑动调进度。点击画面显示或隐藏控件，侧边按钮锁定触控。画面默认等比缩放铺满，可切换自适应或拉伸。\n\n第三方片内广告不由本应用控制。支持纯数据接口，不执行远程 JAR / JS 插件。\n\nSintel：© Blender Foundation，CC BY 3.0，演示源 Shaka Player。",13,Ui.MUTED);
        about.setLineSpacing(Ui.dp(this,4),1);body.addView(about,Ui.margin(this,-1,-2,14));
    }
    private void toggle(LinearLayout box,String label,String key,boolean fallback){Switch s=new Switch(this);s.setText(label);s.setTextColor(Ui.TEXT);s.setTextSize(14);Ui.pad(s,0,14);
        s.setChecked(QingYingApp.prefs(this).getBoolean(key,fallback));s.setOnCheckedChangeListener((b,checked)->{QingYingApp.prefs(this).edit().putBoolean(key,checked).apply();QingYingApp.schedule(this);});box.addView(s);}
    private void addSourceDialog(){
        LinearLayout fields=Ui.col(this);Ui.pad(fields,22,8);EditText name=new EditText(this);name.setHint("片源名称（可选）");fields.addView(name);
        EditText url=new EditText(this);url.setHint("https://…");url.setInputType(InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_VARIATION_URI);fields.addView(url);
        fields.addView(Ui.text(this,"粘贴采集 API、TVBox type 1 配置或 JSON / M3U 订阅地址。",12,Ui.MUTED));
        AlertDialog d=new AlertDialog.Builder(this).setTitle("添加片源").setView(fields).setNegativeButton("取消",null).setPositiveButton("检查并添加",null).create();
        d.setOnShowListener(x->d.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v->{String address=FeedParser.https(url.getText().toString());
            if(address.isEmpty()){url.setError("请输入有效 HTTPS 地址");return;}String label=name.getText().toString().trim();d.dismiss();toast("正在检查片源…");
            app.net.execute(()->{String message;
                try{if(app.repo.sources().size()>=12)throw new Exception("最多添加 12 个片源");
                    String body=Net.text(address);JSONObject object=null;try{object=new JSONObject(body);}catch(Exception ignored){}
                    if(object!=null&&object.has("sites")){int added=0;JSONArray sites=object.getJSONArray("sites");
                        for(int i=0;i<sites.length()&&app.repo.sources().size()<12;i++){JSONObject site=sites.optJSONObject(i);if(site==null||site.optInt("type")!=1)continue;
                            String api=FeedParser.https(site.optString("api"));if(api.isEmpty()||app.repo.hasSource(api))continue;
                            app.repo.addSource(api,site.optString("name","采集片源"),"cms");added++;}
                        if(added==0)throw new Exception("未找到可导入的 HTTPS type 1 线路；爬虫插件线路暂不支持");message="已导入 "+added+" 个采集片源";
                    }else{String kind=object!=null&&object.has("list")?"cms":"feed";
                        if(kind.equals("feed")&&FeedParser.parse(body,address,address).isEmpty())throw new Exception("没有可播放条目");
                        app.repo.addSource(address,label.isEmpty()?java.net.URI.create(address).getHost():label,kind);message="片源已添加";}
                    QingYingApp.refresh(this);
                }catch(Exception e){message="添加失败："+(e.getMessage()==null?"连接或格式错误":e.getMessage());}
                String result=message;runOnUiThread(()->{if(isDestroyed())return;toast(result);reload();});
            });}));d.show();
    }
    private void directDialog(){EditText field=new EditText(this);field.setHint("https://…mp4 / m3u8 / mpd");field.setInputType(InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_VARIATION_URI);
        AlertDialog d=new AlertDialog.Builder(this).setTitle("打开视频直链").setView(field).setNegativeButton("取消",null).setPositiveButton("播放",null).create();
        d.setOnShowListener(x->d.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v->{String url=FeedParser.https(field.getText().toString());if(url.isEmpty()){field.setError("请输入 HTTPS 视频直链");return;}
            Video video=new Video();video.id=FeedParser.id("builtin",url);video.source="builtin";video.title="自定义视频";video.streams.add(new Video.Stream(url,"原画 / 自适应",0));
            app.io.execute(()->{app.repo.merge(java.util.Collections.singletonList(video));runOnUiThread(()->{d.dismiss();play(video);});});}));d.show();}
    private void play(Video video){startActivity(new Intent(this,video.isHongguo()?HongguoActivity.class:PlayerActivity.class).putExtra("id",video.id));}
    private void openQuark(){startActivity(new Intent(this,QuarkActivity.class));}
    private String sourceName(String url){if(url.isEmpty())return "全部片源";if(url.equals("builtin"))return "直链 / 演示";for(Repository.Source s:sources)if(s.url.equals(url))return s.name;return "所选片源";}
    private void chooseSource(){List<String> urls=new ArrayList<>(),names=new ArrayList<>();urls.add("");names.add("全部片源");for(Repository.Source s:sources){urls.add(s.url);names.add(s.name);}urls.add("builtin");names.add("直链 / 演示");
        new AlertDialog.Builder(this).setTitle("选择资源来源").setSingleChoiceItems(names.toArray(new String[0]),urls.indexOf(selectedSource),(d,which)->{resourceRequest++;resourceStatus="";cancelSearch();selectedSource=urls.get(which);page=1;QingYingApp.prefs(this).edit().putString("selectedSource",selectedSource).apply();d.dismiss();sourceButton.setText((tab==HOME?"搜索片源：":"资源：")+sourceName(selectedSource));filter();}).setNegativeButton("取消",null).show();}
    private void selectResource(Video wanted){
        int token=++resourceRequest;resourceStatus="";
        if(wanted.isCloud()||(!wanted.isRecommendation()&&!wanted.streams.isEmpty()&&!wanted.missing())){play(wanted);return;}
        String chosenSource=selectedSource;
        if(!Hongguo.seriesId(wanted).isEmpty()&&(chosenSource.isEmpty()||chosenSource.equals(Hongguo.SOURCE))&&sources.stream().anyMatch(s->s.url.equals(Hongguo.SOURCE))){
            Video official=Hongguo.catalog(wanted);app.io.execute(()->{app.repo.merge(java.util.Collections.singletonList(official));runOnUiThread(()->{if(!isDestroyed()&&token==resourceRequest)play(official);});});return;
        }
        Video cached=CatalogLogic.bestResource(wanted,videos,chosenSource);
        if(cached!=null){play(cached);return;}
        resourceStatus="正在查找《"+wanted.title+"》的播放资源…";updateStatus();
        app.net.execute(()->{
            Video found=null;boolean failed=false;
            for(Repository.Source source:app.repo.sources()){
                if(token!=resourceRequest)return;
                if((!source.kind.equals("cms")&&!source.kind.equals("hongguo"))||(!chosenSource.isEmpty()&&!source.url.equals(chosenSource)))continue;
                try{List<Video> result=Providers.fetch(source,wanted.title,1);app.repo.merge(result);
                    found=CatalogLogic.bestResource(wanted,result,chosenSource);if(found!=null)break;
                }catch(Exception ignored){failed=true;}
            }
            Video selected=found;boolean unavailable=failed;
            runOnUiThread(()->{
                if(isDestroyed()||isFinishing()||token!=resourceRequest)return;
                resourceStatus="";updateStatus();
                if(selected!=null){play(selected);return;}
                new AlertDialog.Builder(this).setTitle(unavailable?"暂未获取到播放资源":"未找到对应播放资源")
                    .setMessage(unavailable?"部分片源暂时无法连接，可以重试或更换片源搜索。":"当前片源没有找到对应片名和年份的资源，可更换片源或查看搜索结果。")
                    .setPositiveButton("查看搜索结果",(d,w)->{query=wanted.title;showTab(LIBRARY);onlineSearch();})
                    .setNeutralButton("重试",(d,w)->selectResource(wanted)).setNegativeButton("关闭",null).show();
            });
        });
    }
    private void toast(String text){Toast.makeText(this,text,Toast.LENGTH_LONG).show();}

    private final class Cards extends ListAdapter<Video,Card> {
        Cards(){super(new DiffUtil.ItemCallback<Video>(){
            public boolean areItemsTheSame(Video a,Video b){return a.id.equals(b.id);}
            public boolean areContentsTheSame(Video a,Video b){try{return a.json().toString().equals(b.json().toString());}catch(Exception e){return false;}}
        });}
        @Override public Card onCreateViewHolder(ViewGroup parent,int type){return new Card();}
        @Override public void onBindViewHolder(Card holder,int position){holder.bind(getItem(position));}
    }
    private static final class StaticRow extends RecyclerView.Adapter<RecyclerView.ViewHolder> {
        private final View child;
        StaticRow(View child){this.child=child;}
        public int getItemCount(){return 1;}
        public RecyclerView.ViewHolder onCreateViewHolder(ViewGroup parent,int type){FrameLayout frame=new FrameLayout(parent.getContext());frame.setLayoutParams(new RecyclerView.LayoutParams(-1,-2));return new RecyclerView.ViewHolder(frame){};}
        public void onBindViewHolder(RecyclerView.ViewHolder holder,int position){if(child.getParent()!=holder.itemView){if(child.getParent()!=null)((ViewGroup)child.getParent()).removeView(child);((FrameLayout)holder.itemView).addView(child,new FrameLayout.LayoutParams(-1,-2));}}
    }
    private final class Card extends RecyclerView.ViewHolder {
        final ImageView poster;final TextView title,meta,badge,checked,description,removeHistory;final LinearLayout card;
        Card(){super(Ui.row(MainActivity.this));card=(LinearLayout)itemView;Ui.pad(card,10,10);card.setBackground(Ui.shape(MainActivity.this,Ui.CARD,18));
            RecyclerView.LayoutParams params=new RecyclerView.LayoutParams(-1,-2);params.bottomMargin=Ui.dp(MainActivity.this,12);params.leftMargin=Ui.dp(MainActivity.this,4);params.rightMargin=Ui.dp(MainActivity.this,4);card.setLayoutParams(params);
            poster=new ImageView(MainActivity.this);poster.setScaleType(ImageView.ScaleType.CENTER_CROP);poster.setBackground(Ui.shape(MainActivity.this,0xff263a34,12));poster.setClipToOutline(true);
            card.addView(poster,new LinearLayout.LayoutParams(Ui.dp(MainActivity.this,94),Ui.dp(MainActivity.this,126)));
            LinearLayout words=Ui.col(MainActivity.this);Ui.pad(words,14,5);card.addView(words,new LinearLayout.LayoutParams(0,-2,1));
            badge=Ui.text(MainActivity.this,"",11,Ui.GREEN);words.addView(badge);
            title=Ui.title(MainActivity.this,"",17);title.setMaxLines(2);title.setEllipsize(TextUtils.TruncateAt.END);words.addView(title,Ui.margin(MainActivity.this,-1,-2,10));
            meta=Ui.text(MainActivity.this,"",11,Ui.MUTED);meta.setMaxLines(2);words.addView(meta,Ui.margin(MainActivity.this,-1,-2,10));
            description=Ui.text(MainActivity.this,"",12,Ui.MUTED);description.setMaxLines(2);description.setEllipsize(TextUtils.TruncateAt.END);description.setLineSpacing(Ui.dp(MainActivity.this,3),1);words.addView(description,Ui.margin(MainActivity.this,-1,-2,8));
            checked=Ui.text(MainActivity.this,"",11,Ui.MUTED);checked.setMaxLines(3);checked.setEllipsize(TextUtils.TruncateAt.END);words.addView(checked,Ui.margin(MainActivity.this,-1,-2,10));
            removeHistory=Ui.button(MainActivity.this,"移除记录",false,null);words.addView(removeHistory,Ui.margin(MainActivity.this,-2,48,8));
        }
        void bind(Video video){title.setText(video.title);badge.setText(video.isRecommendation()?(Recommendations.domestic(video)?"中国影视 · ":"")+video.category:(video.is4k()?"4K UHD  ·  ":!video.qualityHint.isEmpty()?"4K 候选 · ":"")+video.category+(video.favorite?"  ★":""));
            badge.setTextColor(video.is4k()?Ui.GOLD:Ui.GREEN);meta.setText(video.updated.isEmpty()?video.dateLabel():"源更新 "+video.updated.substring(0,10));
            checked.setText(video.isRecommendation()?video.credit+" · 点击播放":video.isCloud()?"夸克网盘 · 播放前获取地址":video.isHongguo()?"红果官方网页 · 点击打开播放器":video.position>0?"续播 "+PlayerActivity.duration(video.position)+"  ·  "+video.status():video.status()+(video.checked()>0?" · "+time(video.checked()):""));
            meta.setVisibility(View.VISIBLE);
            if(video.isRecommendation()){
                List<String> details=new ArrayList<>();if(!video.year.isEmpty())details.add(video.year);if(!video.region.isEmpty())details.add(video.region);if(video.trendScore>0)details.add("豆瓣 "+video.trendScore);
                meta.setText(String.join(" · ",details));if(details.isEmpty())meta.setVisibility(View.GONE);
            }
            description.setText(android.text.Html.fromHtml(video.description,android.text.Html.FROM_HTML_MODE_LEGACY).toString().replaceAll("\\s+"," ").trim());
            description.setVisibility(video.isRecommendation()?View.VISIBLE:View.GONE);
            if(video.isRecommendation()&&description.getText().length()==0)description.setText("简介暂未提供");
            removeHistory.setVisibility(tab==HISTORY?View.VISIBLE:View.GONE);
            if(tab==HISTORY){badge.setText(sourceName(video.source));meta.setText(video.lastPlayed>0?"观看 "+time(video.lastPlayed):"旧版播放记录");
                checked.setText((video.lastEpisode.isEmpty()?"":video.lastEpisode+" · ")+(video.completed?"已看完 · 点击重播":video.position>0?"已播放 "+PlayerActivity.duration(video.position)+(video.duration>0?" / "+PlayerActivity.duration(video.duration):""):"点击从头播放"));
                removeHistory.setContentDescription("移除播放记录："+video.title);removeHistory.setOnClickListener(v->app.io.execute(()->{app.repo.removePlaybackHistory(video.id);runOnUiThread(MainActivity.this::reload);}));}
            poster.setContentDescription(video.title+" 海报");
            Posters.load(poster,video.poster);card.setContentDescription(video.title+"，"+(video.isRecommendation()?video.description:video.status()));card.setOnClickListener(v->selectResource(video));
            card.setOnLongClickListener(v->{if(video.isRecommendation()){selectResource(video);return true;}app.io.execute(()->{app.repo.favorite(video.id);runOnUiThread(()->{toast(video.favorite?"已取消收藏":"已收藏");reload();});});return true;});
        }
    }
}
