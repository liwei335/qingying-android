package io.qingying.app;

import org.json.JSONObject;
import org.json.JSONArray;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.ArrayList;

/** Read-only directory and playback calls; no remote save, delete or account mutation. */
public final class QuarkApi {
    public static final class LoginRequired extends IOException { LoginRequired(){super("请先登录夸克网盘，或重新登录已过期的账号");} }
    private static JSONObject call(String path,JSONObject body) throws Exception {
        if(!QuarkSession.available())throw new LoginRequired();
        long generation=QuarkSession.generation();
        String url=QuarkSession.API+path+(path.contains("?")?"&":"?")+"pr=ucpro&fr=pc";
        HttpURLConnection c=(HttpURLConnection)new URL(url).openConnection();
        try {
            c.setConnectTimeout(10000);c.setReadTimeout(15000);c.setInstanceFollowRedirects(false);
            c.setRequestProperty("Cookie",QuarkSession.header(url));c.setRequestProperty("Referer",QuarkSession.HOME+"/");
            c.setRequestProperty("User-Agent",QuarkSession.UA);c.setRequestProperty("Accept","application/json");c.setRequestProperty("Accept-Encoding","identity");
            if(body!=null){byte[] bytes=body.toString().getBytes(StandardCharsets.UTF_8);c.setRequestMethod("POST");c.setDoOutput(true);c.setRequestProperty("Content-Type","application/json");c.setFixedLengthStreamingMode(bytes.length);try(java.io.OutputStream out=c.getOutputStream()){out.write(bytes);}}
            int status=c.getResponseCode();QuarkSession.update(c,generation);
            if(status==401||status==403)throw new LoginRequired();
            if(status!=200)throw new IOException("夸克接口暂不可用（HTTP "+status+"）");
            ByteArrayOutputStream out=new ByteArrayOutputStream();long deadline=System.nanoTime()+20_000_000_000L;
            try(InputStream in=c.getInputStream()){byte[] buffer=new byte[4096];int n;while((n=in.read(buffer))!=-1){if(out.size()+n>2*1024*1024||System.nanoTime()>deadline)throw new IOException("夸克响应过大或超时");out.write(buffer,0,n);}}
            if(generation!=QuarkSession.generation())throw new LoginRequired();
            JSONObject j=new JSONObject(new String(out.toByteArray(),StandardCharsets.UTF_8));
            if(j.optInt("status",200)==401||j.optInt("code")==31001)throw new LoginRequired();
            if(j.optInt("code")!=0||j.optInt("status",200)>=400)throw new IOException("夸克返回："+j.optString("message","当前账号或文件暂不支持此操作"));
            return j;
        }finally{c.disconnect();}
    }
    public static final class Page { public List<QuarkProtocol.Entry> entries;public boolean more; }
    public static Page list(String parent,int page) throws Exception {
        JSONObject result=call("/file/sort?pdir_fid="+URLEncoder.encode(parent,"UTF-8")+"&_page="+page+"&_size=100&_fetch_total=1&_sort=file_type:asc,file_name:asc",null);
        Page p=new Page();p.entries=QuarkProtocol.entries(result);JSONObject m=result.optJSONObject("metadata");
        p.more=m!=null&&page*100<m.optInt("_total");return p;
    }
    public static List<Video.Stream> resolve(String fid) throws Exception {
        List<Video.Stream> streams=new ArrayList<>();Exception last=null;
        try {
            String url=QuarkProtocol.download(call("/file/download",new JSONObject().put("fids",new JSONArray().put(fid))));
            if(!url.isEmpty()){Video.Stream raw=new Video.Stream(url,"原画 · 原文件",0);raw.episode="original";streams.add(raw);}
        }catch(LoginRequired e){throw e;}catch(Exception e){last=e;}
        try {streams.addAll(QuarkProtocol.transcodes(call("/file/v2/play/project",new JSONObject().put("fid",fid).put("resolutions","low,normal,high,super,2k,4k").put("supports","fmp4_av,m3u8"))));}
        catch(LoginRequired e){throw e;}catch(Exception e){last=e;}
        if(streams.isEmpty())throw last==null?new IOException("账号未返回可用画质，请在夸克官方页面检查该文件"):last;
        return streams;
    }
}
