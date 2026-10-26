package io.qingying.app;

import android.app.Application;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.LooperMode;
import org.robolectric.util.ReflectionHelpers;
import java.util.Arrays;
import java.util.Collections;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk=28,application=Application.class,qualifiers="mdpi")
@LooperMode(LooperMode.Mode.PAUSED)
public class CatalogNavigationTest {
    private MainActivity activity;private LinearLayout root;private int windowWidth=420;
    @Before public void create(){
        activity=Robolectric.buildActivity(MainActivity.class).get();activity.setTheme(R.style.AppTheme);
        root=Ui.col(activity);LinearLayout content=Ui.col(activity),nav=Ui.row(activity);root.addView(content,new LinearLayout.LayoutParams(-1,0,1));root.addView(nav);activity.setContentView(root);
        ReflectionHelpers.setField(activity,"root",root);ReflectionHelpers.setField(activity,"content",content);ReflectionHelpers.setField(activity,"nav",nav);
    }
    private void show(int tab){ReflectionHelpers.callInstanceMethod(activity,"showTab",ReflectionHelpers.ClassParameter.from(int.class,tab));layout();}
    private void layout(){root.measure(View.MeasureSpec.makeMeasureSpec(windowWidth,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(1000,View.MeasureSpec.EXACTLY));root.layout(0,0,windowWidth,1000);}
    private TextView find(View view,String text){if(view instanceof TextView&&((TextView)view).getText().toString().equals(text))return (TextView)view;if(view instanceof ViewGroup){ViewGroup group=(ViewGroup)view;for(int i=0;i<group.getChildCount();i++){TextView child=find(group.getChildAt(i),text);if(child!=null)return child;}}return null;}
    @Test public void animeAndShortDramaLiveAlongsideTvAndMovies(){
        show(0);for(String title:new String[]{"热播剧集","热门电影","动漫推荐","热播短剧"})assertNotNull(title,find(root,title));
        LinearLayout nav=ReflectionHelpers.getField(activity,"nav");assertNull(find(nav,"动漫"));assertNotNull(find(nav,"记录"));
        find(root,"动漫推荐").performClick();assertEquals(Recommendations.ANIME,ReflectionHelpers.callInstanceMethod(activity,"currentGroup"));layout();
        find(root,"热播短剧").performClick();assertEquals(Recommendations.SHORT,ReflectionHelpers.callInstanceMethod(activity,"currentGroup"));
    }
    @Test public void selectingShortDramaRetainsCategoryStripAndScrollPosition(){
        windowWidth=240;show(0);layout();android.widget.HorizontalScrollView strip=ReflectionHelpers.getField(activity,"categoryScroller");
        strip.scrollTo(10000,0);int before=strip.getScrollX();assertTrue("fixture must overflow horizontally",before>0);
        View header=strip.getParent() instanceof View?(View)strip.getParent():null;
        find(root,"热播短剧").performClick();layout();
        assertSame(strip,ReflectionHelpers.getField(activity,"categoryScroller"));assertSame(header,strip.getParent());assertEquals(before,strip.getScrollX());assertTrue(find(root,"热播短剧").isSelected());assertFalse(find(root,"热播剧集").isSelected());
        find(root,"动漫推荐").performClick();layout();assertEquals(before,strip.getScrollX());
    }
    @Test public void recordsPageShowsSearchesAndCompletedPlayback(){
        Video v=new Video();v.id="done";v.title="看完的影片";v.source="builtin";v.lastPlayed=1000;v.completed=true;v.lastEpisode="第1集";v.streams.add(new Video.Stream("https://example.test/video.mp4","第1集",0));
        ReflectionHelpers.setField(activity,"playbackHistory",Collections.singletonList(v));ReflectionHelpers.setField(activity,"searchHistory",Arrays.asList("国产电影","热播短剧"));
        show(2);assertNotNull(find(root,"看完的影片"));assertNotNull(find(root,"第1集 · 已看完 · 点击重播"));assertNotNull(find(root,"移除记录"));
        find(root,"搜索记录").performClick();layout();assertNotNull(find(root,"国产电影"));assertNotNull(find(root,"热播短剧"));assertNotNull(find(root,"清空"));
    }
    @After public void destroy(){activity.finish();}
}
