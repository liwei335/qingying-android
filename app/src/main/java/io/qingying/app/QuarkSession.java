package io.qingying.app;

import android.os.Handler;
import android.os.Looper;
import android.webkit.CookieManager;
import java.net.HttpURLConnection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** The WebView cookie store is the only persistent credential store; never log cookies. */
public final class QuarkSession {
    public static final String HOME="https://pan.quark.cn";
    public static final String API="https://drive.quark.cn/1/clouddrive";
    public static final String UA="Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36";
    private static volatile String cookie="";
    private static volatile long generation;
    private static final Handler MAIN=new Handler(Looper.getMainLooper());
    public static long generation(){return generation;}
    public static void load() {
        if(Looper.myLooper()!=Looper.getMainLooper()){MAIN.post(QuarkSession::load);return;}
        Map<String,String> all=new LinkedHashMap<>();CookieManager manager=CookieManager.getInstance();
        for(String host:new String[]{HOME,API,"https://drive-pc.quark.cn"}) {
            String value=manager.getCookie(host);if(value==null)continue;
            for(String part:value.split(";")){int at=part.indexOf('=');if(at>0)all.put(part.substring(0,at).trim(),part.substring(at+1).trim());}
        }
        StringBuilder b=new StringBuilder();for(Map.Entry<String,String> e:all.entrySet()){if(b.length()>0)b.append("; ");b.append(e.getKey()).append('=').append(e.getValue());}cookie=b.toString();
    }
    public static boolean available(){return !cookie.isEmpty();}
    public static String header(String url){return QuarkProtocol.credentialHost(url)?cookie:"";}
    public static void update(HttpURLConnection connection,long expectedGeneration) {
        if(expectedGeneration!=generation)return;
        String url=connection.getURL().toString();if(!QuarkProtocol.credentialHost(url))return;
        for(Map.Entry<String,List<String>> e:connection.getHeaderFields().entrySet())if("set-cookie".equalsIgnoreCase(e.getKey())) {
            for(String value:e.getValue())MAIN.post(()->{
                if(generation!=expectedGeneration)return;
                CookieManager.getInstance().setCookie(url,value,ignored->{if(generation==expectedGeneration)load();});
            });
        }
    }
    public static void clear(Runnable done) {
        generation++;cookie="";
        CookieManager.getInstance().removeAllCookies(value->{CookieManager.getInstance().flush();done.run();});
    }
}
