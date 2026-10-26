package io.qingying.app;

import android.app.Application;
import android.content.Intent;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowAlertDialog;
import org.robolectric.util.ReflectionHelpers;
import java.util.List;
import static org.junit.Assert.*;
import static org.robolectric.Shadows.shadowOf;

@RunWith(RobolectricTestRunner.class)
@Config(sdk=28,application=Application.class)
public class DirectPlaybackTest {
    @Test public void searchResultOpensItsPlayerWithoutConfirmation(){
        MainActivity activity=Robolectric.buildActivity(MainActivity.class).get();Video video=resource();
        ReflectionHelpers.callInstanceMethod(activity,"selectResource",ReflectionHelpers.ClassParameter.from(Video.class,video));
        Intent next=shadowOf(activity).getNextStartedActivity();assertEquals(PlayerActivity.class.getName(),next.getComponent().getClassName());assertEquals(video.id,next.getStringExtra("id"));assertNull(ShadowAlertDialog.getLatestAlertDialog());activity.finish();
    }
    @Test public void recommendationWithCachedMatchOpensPlayerWithoutConfirmation(){
        MainActivity activity=Robolectric.buildActivity(MainActivity.class).get();Video video=resource(),wanted=new Video();wanted.title=video.title;wanted.trendGroup=Recommendations.TV;
        List<Video> cached=ReflectionHelpers.getField(activity,"videos");cached.add(video);
        ReflectionHelpers.callInstanceMethod(activity,"selectResource",ReflectionHelpers.ClassParameter.from(Video.class,wanted));
        assertEquals(video.id,shadowOf(activity).getNextStartedActivity().getStringExtra("id"));assertNull(ShadowAlertDialog.getLatestAlertDialog());activity.finish();
    }
    @Test public void hongguoUsesOfficialWebPlayerInsteadOfNativeSeoPreview(){
        MainActivity activity=Robolectric.buildActivity(MainActivity.class).get();Video wanted=new Video();wanted.hongguoSeriesId="123";wanted.title="红果短剧";Video video=Hongguo.catalog(wanted);
        ReflectionHelpers.callInstanceMethod(activity,"selectResource",ReflectionHelpers.ClassParameter.from(Video.class,video));
        Intent next=shadowOf(activity).getNextStartedActivity();assertEquals(HongguoActivity.class.getName(),next.getComponent().getClassName());assertEquals(video.id,next.getStringExtra("id"));assertNull(ShadowAlertDialog.getLatestAlertDialog());activity.finish();
    }
    private Video resource(){Video video=new Video();video.id="test-resource";video.title="国产热播剧";video.streams.add(new Video.Stream("https://example.test/video.mp4","第1集",0));return video;}
}
