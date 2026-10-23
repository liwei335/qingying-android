package io.qingying.app;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class Net {
    public static final class Response {
        public final int code;
        public final byte[] body;
        public final String url, type;
        Response(int code, byte[] body, String url, String type) {
            this.code = code; this.body = body; this.url = url; this.type = type == null ? "" : type;
        }
        public String text() { return new String(body, StandardCharsets.UTF_8); }
    }
    public static Response get(String address, int limit, boolean range) throws IOException {
        return get(address, limit, range, "QingYing/0.2 (Android; video client)", "");
    }
    public static Response get(String address, int limit, boolean range, String userAgent, String referer) throws IOException {
        String url = FeedParser.https(address);
        if (url.isEmpty()) throw new IOException("仅支持 HTTPS 地址");
        long deadline = System.nanoTime() + 15_000_000_000L;
        for (int redirect = 0; redirect < 5; redirect++) {
            if (Thread.currentThread().isInterrupted()) throw new IOException("已取消");
            HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
            try {
                c.setConnectTimeout(6000); c.setReadTimeout(6000); c.setInstanceFollowRedirects(false);
                c.setRequestProperty("User-Agent", userAgent);
                if (!referer.isEmpty()) c.setRequestProperty("Referer", referer);
                c.setRequestProperty("Accept-Encoding", "identity");
                if (range) c.setRequestProperty("Range", "bytes=0-" + (limit - 1));
                int code = c.getResponseCode();
                if (code == 301 || code == 302 || code == 303 || code == 307 || code == 308) {
                    String location = c.getHeaderField("Location");
                    if (location == null) throw new IOException("无效重定向");
                    url = FeedParser.https(URI.create(url).resolve(location).toString());
                    if (url.isEmpty()) throw new IOException("重定向地址不支持");
                    continue;
                }
                if (code < 200 || code >= 300) return new Response(code, new byte[0], url, c.getContentType());
                ByteArrayOutputStream out = new ByteArrayOutputStream();
                try (InputStream in = c.getInputStream()) {
                    byte[] buffer = new byte[4096]; int n;
                    while (out.size() < limit && (n = in.read(buffer, 0, Math.min(buffer.length, limit - out.size()))) != -1) {
                        if (Thread.currentThread().isInterrupted() || System.nanoTime() > deadline) throw new IOException("读取超时");
                        out.write(buffer, 0, n);
                    }
                    if (!range && out.size() == limit && in.read() != -1) throw new IOException("订阅内容过大");
                }
                return new Response(code, out.toByteArray(), url, c.getContentType());
            } finally { c.disconnect(); }
        }
        throw new IOException("重定向次数过多");
    }
    public static String text(String url) throws IOException {
        Response r = get(url, 2 * 1024 * 1024, false);
        if (r.code != 200) throw new IOException("HTTP " + r.code);
        return r.text();
    }
    public static String classify(Response r) {
        if (r.code == 404 || r.code == 410) return "missing";
        if (r.code != 200 && r.code != 206) return "temporary";
        if (r.body.length == 0) return "temporary";
        String text = r.text().trim().toLowerCase(Locale.ROOT);
        if (text.startsWith("<!doctype html") || text.startsWith("<html")) return "temporary";
        if (text.startsWith("#extm3u") || text.contains("<mpd")) return "ok";
        if (r.body.length >= 12 && new String(r.body, 4, 4, StandardCharsets.US_ASCII).equals("ftyp")) return "ok";
        if (r.body.length > 188 && r.body[0] == 0x47 && r.body[188] == 0x47) return "ok";
        return r.type.toLowerCase(Locale.ROOT).startsWith("video/") ? "ok" : "temporary";
    }
    /** A bounded request checks an HLS child playlist and its first media segment, when available. */
    public static String probe(Video.Stream stream) {
        try {
            String url = stream.url;
            for (int depth = 0; depth < 3; depth++) {
                Response r = get(url, 64 * 1024, true);
                String state = classify(r);
                if (!state.equals("ok")) return state;
                String text = r.text().trim();
                if (!text.startsWith("#EXTM3U")) return "ok";
                Matcher m = Pattern.compile("RESOLUTION=(\\d+)x(\\d+)").matcher(text);
                while (m.find()) {
                    stream.width = Math.max(stream.width, Integer.parseInt(m.group(1)));
                    stream.height = Math.max(stream.height, Integer.parseInt(m.group(2)));
                }
                String next = "";
                for (String line : text.split("\r?\n")) {
                    if (!line.trim().isEmpty() && !line.startsWith("#")) { next = line.trim(); break; }
                }
                if (next.isEmpty()) return "temporary";
                url = URI.create(r.url).resolve(next).toString();
                if (!text.contains("#EXT-X-STREAM-INF")) {
                    Response segment = get(url, 2048, true);
                    return segment.body.length > 0 && (segment.code == 200 || segment.code == 206)
                        && !segment.type.toLowerCase(Locale.ROOT).contains("text/html") ? "ok" : "temporary";
                }
            }
            return "temporary";
        } catch (Exception e) { return "temporary"; }
    }
}
