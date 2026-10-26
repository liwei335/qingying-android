package io.qingying.app;

import org.json.JSONObject;
import org.junit.Test;
import java.util.Arrays;
import java.util.List;
import static org.junit.Assert.*;

public class RecommendationPresentationTest {
    @Test public void realFeedFieldsSupplyPostersDescriptionsAndDomesticPriority() throws Exception {
        String body="{\"subject_collection_items\":[{\"id\":\"a\",\"title\":\"海外剧\",\"card_subtitle\":\"2026 / 美国 / 剧情\",\"comment\":\"海外故事\"},{\"id\":\"b\",\"title\":\"国产剧\",\"card_subtitle\":\"2026 / 中国大陆 / 剧情\",\"comment\":\"医生回到家乡。\\n为邻里治病。\",\"pic\":{\"large\":\"https://img2.doubanio.com/poster.jpg\"}},{\"id\":\"c\",\"title\":\"港剧\",\"countries\":[\"中国香港\"],\"card_subtitle\":\"2026 / 中国香港 / 悬疑\"}]}";
        List<Video> original=Recommendations.parse(body,Recommendations.TV),ordered=Recommendations.domesticFirst(original);
        assertEquals("海外剧",original.get(0).title);assertEquals("国产剧",ordered.get(0).title);assertEquals("港剧",ordered.get(1).title);assertEquals("海外剧",ordered.get(2).title);
        assertEquals(2,ordered.get(0).trendRank);assertEquals("医生回到家乡。 为邻里治病。",ordered.get(0).description);assertTrue(ordered.get(0).poster.startsWith("https://"));
        Video restored=Video.from(new JSONObject(ordered.get(0).json().toString()));assertEquals("中国大陆",restored.region);assertTrue(Recommendations.domestic(restored));assertEquals(ordered.get(0).description,restored.description);
    }
    @Test public void movieCoverAndBriefInfoDoNotDependOnTvFields() throws Exception {
        List<Video> items=Recommendations.parse("{\"subject_collection_items\":[{\"id\":\"film\",\"title\":\"国产电影\",\"cover\":{\"url\":\"https://img9.doubanio.com/film.jpg\"},\"description\":\"\",\"card_subtitle\":\"2026 / 中国大陆 / 剧情 悬疑 / 某导演 / 某演员\"}]}",Recommendations.MOVIE);
        assertEquals("https://img9.doubanio.com/film.jpg",items.get(0).poster);assertTrue(Recommendations.domestic(items.get(0)));assertTrue(items.get(0).description.startsWith("剧情 悬疑"));
    }
    @Test public void resourceChoicePrefersResumeAndHonorsSourceAndYear(){
        Video wanted=video("热播剧","2026","trend"),first=video("热播剧","2026","a"),resume=video("热播剧","2026","b"),remake=video("热播剧","2020","a");resume.position=50000;remake.position=90000;
        assertSame(resume,CatalogLogic.bestResource(wanted,Arrays.asList(first,resume,remake),""));
        assertSame(first,CatalogLogic.bestResource(wanted,Arrays.asList(first,resume,remake),"a"));
        first.streams.get(0).state="missing";assertNull(CatalogLogic.bestResource(wanted,Arrays.asList(first,resume,remake),"a"));
        assertNull(CatalogLogic.bestResource(wanted,Arrays.asList(first,resume,remake),"c"));
    }
    private static Video video(String title,String year,String source){Video v=new Video();v.title=title;v.year=year;v.source=source;v.streams.add(new Video.Stream("https://example.test/video.mp4","第1集",0));return v;}
}
