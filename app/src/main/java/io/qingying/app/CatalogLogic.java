package io.qingying.app;

import java.util.Locale;

/** Pure policy shared by catalog, resource selection and player resume. */
public final class CatalogLogic {
    public static String titleKey(String title) {
        return title.toLowerCase(Locale.ROOT).replaceAll("[\\p{P}\\p{Z}\\s]", "");
    }
    public static boolean sameTitle(Video left, Video right) {
        if (!titleKey(left.title).equals(titleKey(right.title))) return false;
        return left.year.isEmpty() || right.year.isEmpty() || left.year.equals(right.year);
    }
    public static String episodeKey(String episode) {
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("^第?0*(\\d+)(?:集|话|話|期)?$").matcher(episode.trim());
        return m.matches() ? "episode:" + m.group(1) : titleKey(episode);
    }
    public static boolean anime(Video video) { return video.category.matches(".*(动漫|动画|番剧).*"); }
    public static int columns(int widthDp) { return Math.max(1, Math.min(4, widthDp / 330)); }
    public static int playerHeight(int width, int height, float ratio) {
        return Math.max(1, Math.min(Math.round(width / Math.max(0.5f, ratio)), Math.round(height * .57f)));
    }
    public static Video bestResource(Video wanted, java.util.List<Video> options, String source) {
        Video best=null;
        for(Video item:options){
            if(item.isCloud()||item.streams.isEmpty()||item.missing()||!sameTitle(wanted,item)||(!source.isEmpty()&&!source.equals(item.source)))continue;
            if(best==null||resourceScore(item)>resourceScore(best))best=item;
        }
        return best;
    }
    private static int resourceScore(Video video){return (video.position>0?8:0)+(video.reachable()?4:0)+(video.favorite?2:0)+(video.is4k()?1:0);}
    public static int resumeIndex(Video video) {
        for (int i=0;i<video.streams.size();i++) if (!video.lastStream.isEmpty() && video.streams.get(i).url.equals(video.lastStream)) return i;
        for (int i=0;i<video.streams.size();i++) if (!video.lastEpisode.isEmpty() && episodeKey(video.streams.get(i).episode).equals(episodeKey(video.lastEpisode))) return i;
        if(!video.qualityHint.isEmpty())for(int i=0;i<video.streams.size();i++)if(video.streams.get(i).label.toLowerCase(Locale.ROOT).matches(".*(4k|2160p|uhd).*"))return i;
        return 0;
    }
}
