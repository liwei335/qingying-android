package io.qingying.app;

import org.json.JSONArray;
import org.json.JSONObject;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class QuarkProtocol {
    public static boolean credentialHost(String address) {
        try {
            URI u=new URI(address);String h=u.getHost();
            if(!"https".equalsIgnoreCase(u.getScheme())||h==null||u.getUserInfo()!=null||(u.getPort()!=-1&&u.getPort()!=443))return false;
            h=h.toLowerCase(Locale.ROOT);
            return h.equals("quark.cn")||h.endsWith(".quark.cn")||h.equals("uc.cn")||h.endsWith(".uc.cn")||h.equals("ucweb.com")||h.endsWith(".ucweb.com");
        }catch(Exception e){return false;}
    }
    public static final class Entry {
        public String id,name;public boolean folder,video;public long size;
        public int width,height;
    }
    public static List<Entry> entries(JSONObject json) throws Exception {
        List<Entry> result=new ArrayList<>();JSONArray array=json.getJSONObject("data").getJSONArray("list");
        for(int i=0;i<array.length();i++) {
            JSONObject j=array.getJSONObject(i);Entry e=new Entry();e.id=j.optString("fid");e.name=j.optString("file_name");
            e.folder=!j.optBoolean("file",true);e.video=j.optInt("category")==1||e.name.toLowerCase(Locale.ROOT).matches(".*\\.(mp4|mkv|mov|avi|m4v|ts|webm|m2ts)$");
            e.size=j.optLong("size");e.width=j.optInt("video_width");e.height=j.optInt("video_height");
            if(!e.id.isEmpty()&&(e.folder||e.video))result.add(e);
        }
        return result;
    }
    public static List<Video.Stream> transcodes(JSONObject json) {
        List<Video.Stream> list=new ArrayList<>();JSONObject data=json.optJSONObject("data");if(data==null)return list;
        JSONArray array=data.optJSONArray("video_list");if(array==null)return list;
        for(int i=0;i<array.length();i++) {
            JSONObject item=array.optJSONObject(i);if(item==null)continue;JSONObject info=item.optJSONObject("video_info");if(info==null)continue;
            String url=FeedParser.https(info.optString("url"));if(url.isEmpty())continue;
            int width=info.optInt("width"),height=info.optInt("height");String resolution=item.optString("resolution","auto");
            Video.Stream s=new Video.Stream(url,height>0?"网盘转码 · "+width+" × "+height:"网盘转码 · "+resolution,height);s.width=width;s.episode=resolution;
            String format=info.optString("format");if(format.contains("m3u8")||format.contains("hls"))s.mimeType="application/x-mpegURL";
            if(format.contains("dash")||format.contains("mpd"))s.mimeType="application/dash+xml";list.add(s);
        }
        list.sort((a,b)->Integer.compare(b.width,a.width));return list;
    }
    public static String download(JSONObject json) {
        JSONArray array=json.optJSONArray("data");if(array==null||array.length()==0)return "";
        JSONObject first=array.optJSONObject(0);return first==null?"":FeedParser.https(first.optString("download_url"));
    }
}
