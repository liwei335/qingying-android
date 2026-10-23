package io.qingying.app;

import android.net.Uri;
import androidx.media3.common.C;
import androidx.media3.datasource.BaseDataSource;
import androidx.media3.datasource.DataSpec;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Re-evaluate the credential host at EVERY redirect and every HLS segment request. */
@androidx.annotation.OptIn(markerClass=androidx.media3.common.util.UnstableApi.class)
public final class CloudHttpDataSource extends BaseDataSource {
    private HttpURLConnection connection;
    private InputStream input;
    private Uri uri;
    private long remaining, generation;
    private boolean opened;
    public CloudHttpDataSource(){super(true);}
    @Override public long open(DataSpec spec) throws IOException {
        transferInitializing(spec);generation=QuarkSession.generation();String url=FeedParser.https(spec.uri.toString());
        if(url.isEmpty())throw new IOException("仅支持 HTTPS 网盘资源");
        try {
            for(int redirects=0;redirects<6;redirects++) {
                connection=(HttpURLConnection)new URL(url).openConnection();
                connection.setConnectTimeout(10000);connection.setReadTimeout(15000);connection.setInstanceFollowRedirects(false);
                connection.setRequestProperty("User-Agent",QuarkSession.UA);connection.setRequestProperty("Referer",QuarkSession.HOME+"/");connection.setRequestProperty("Accept-Encoding","identity");
                String cookie=QuarkSession.header(url);if(!cookie.isEmpty())connection.setRequestProperty("Cookie",cookie);
                if(spec.position>0||spec.length!=C.LENGTH_UNSET)connection.setRequestProperty("Range","bytes="+spec.position+"-"+(spec.length==C.LENGTH_UNSET?"":spec.position+spec.length-1));
                int code=connection.getResponseCode();
                if(code==301||code==302||code==303||code==307||code==308) {
                    String location=connection.getHeaderField("Location");if(location==null)throw new IOException("无效网盘跳转");
                    url=FeedParser.https(URI.create(url).resolve(location).toString());connection.disconnect();connection=null;
                    if(url.isEmpty())throw new IOException("不支持此网盘跳转地址");continue;
                }
                if(code!=200&&code!=206)throw new IOException("网盘播放请求失败（HTTP "+code+"）");
                if(generation!=QuarkSession.generation())throw new IOException("网盘已退出登录");
                if(code==206) {
                    String contentRange=connection.getHeaderField("Content-Range");
                    if(contentRange==null||!contentRange.startsWith("bytes "+spec.position+"-"))throw new IOException("视频字节范围不匹配");
                }
                input=connection.getInputStream();uri=Uri.parse(url);
                long skip=code==200?spec.position:0, length=connection.getContentLengthLong();
                remaining=spec.length!=C.LENGTH_UNSET?spec.length:length<0?C.LENGTH_UNSET:Math.max(0,length-skip);
                opened=true;transferStarted(spec);
                if(skip>0){byte[] buffer=new byte[8192];long deadline=System.nanoTime()+15_000_000_000L;while(skip>0){
                    if(System.nanoTime()>deadline||Thread.currentThread().isInterrupted())throw new IOException("此线路不支持快速定位，请换线路");
                    int n=input.read(buffer,0,(int)Math.min(buffer.length,skip));if(n<0)throw new IOException("视频位置超出文件范围");skip-=n;bytesTransferred(n);
                }}
                return remaining;
            }
            throw new IOException("网盘跳转次数过多");
        }catch(IOException|RuntimeException e){close();throw e;}
    }
    @Override public int read(byte[] buffer,int offset,int length) throws IOException {
        if(length==0)return 0;if(generation!=QuarkSession.generation())throw new IOException("网盘已退出登录");
        if(remaining==0)return C.RESULT_END_OF_INPUT;
        int n=input.read(buffer,offset,remaining==C.LENGTH_UNSET?length:(int)Math.min(remaining,length));
        if(n<0)return C.RESULT_END_OF_INPUT;
        if(remaining!=C.LENGTH_UNSET)remaining-=n;bytesTransferred(n);return n;
    }
    @Override public Uri getUri(){return uri;}
    @Override public Map<String,List<String>> getResponseHeaders(){
        if(connection==null)return Collections.emptyMap();Map<String,List<String>> safe=new LinkedHashMap<>();
        for(Map.Entry<String,List<String>> entry:connection.getHeaderFields().entrySet())if(entry.getKey()!=null&&!entry.getKey().equalsIgnoreCase("set-cookie"))safe.put(entry.getKey(),entry.getValue());
        return safe;
    }
    @Override public void close() throws IOException {
        try{if(input!=null)input.close();}finally{input=null;uri=null;if(connection!=null){connection.disconnect();connection=null;}if(opened){opened=false;transferEnded();}}
    }
}
