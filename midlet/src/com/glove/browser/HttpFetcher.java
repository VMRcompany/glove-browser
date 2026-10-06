package com.glove.browser;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import javax.microedition.io.Connector;
import javax.microedition.io.HttpConnection;

/**
 * HTTP fetcher with optional site proxy for HTTPS / heavy pages.
 */
public final class HttpFetcher {

    private HttpFetcher() {
    }

    public static String fetch(String url, String proxyPrefix) throws IOException {
        IOException last = null;
        String[] candidates;
        if (url.startsWith("https://") && proxyPrefix != null && proxyPrefix.length() > 0) {
            candidates = new String[] {
                proxyPrefix + GloveMidlet.encode(url),
                url
            };
        } else {
            candidates = new String[] { url };
        }
        for (int i = 0; i < candidates.length; i++) {
            try {
                return fetchOnce(candidates[i]);
            } catch (IOException e) {
                last = e;
            }
        }
        if (last != null) {
            throw last;
        }
        throw new IOException("empty");
    }

    private static String fetchOnce(String url) throws IOException {
        HttpConnection conn = null;
        InputStream in = null;
        try {
            String current = url;
            for (int hop = 0; hop < 5; hop++) {
                if (conn != null) {
                    try { conn.close(); } catch (Exception e) { /* */ }
                }
                conn = (HttpConnection) Connector.open(current, Connector.READ, true);
                conn.setRequestMethod(HttpConnection.GET);
                conn.setRequestProperty("User-Agent", "GloveBrowser/1.0 (MIDP-2.0; CLDC-1.1)");
                conn.setRequestProperty("Accept", "text/html,text/plain,*/*");
                conn.setRequestProperty("Accept-Charset", "utf-8,windows-1251,iso-8859-1");
                int code = conn.getResponseCode();
                if (code == HttpConnection.HTTP_MOVED_PERM
                        || code == HttpConnection.HTTP_MOVED_TEMP
                        || code == HttpConnection.HTTP_SEE_OTHER
                        || code == 307) {
                    String loc = conn.getHeaderField("Location");
                    if (loc == null || loc.length() == 0) {
                        throw new IOException("redirect without Location");
                    }
                    current = absolutize(current, loc);
                    continue;
                }
                if (code != HttpConnection.HTTP_OK) {
                    throw new IOException("HTTP " + code);
                }
                String enc = conn.getEncoding();
                if (enc == null) {
                    String ct = conn.getType();
                    enc = charsetFromContentType(ct);
                }
                in = conn.openInputStream();
                byte[] data = readLimited(in, 96 * 1024);
                if (enc == null) {
                    enc = "UTF-8";
                }
                try {
                    return new String(data, enc);
                } catch (Exception e) {
                    return new String(data, "ISO-8859-1");
                }
            }
            throw new IOException("too many redirects");
        } finally {
            if (in != null) {
                try { in.close(); } catch (Exception e) { /* */ }
            }
            if (conn != null) {
                try { conn.close(); } catch (Exception e) { /* */ }
            }
        }
    }

    private static byte[] readLimited(InputStream in, int max) throws IOException {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        byte[] buf = new byte[1024];
        int total = 0;
        int n;
        while ((n = in.read(buf)) >= 0) {
            if (total + n > max) {
                bos.write(buf, 0, max - total);
                break;
            }
            bos.write(buf, 0, n);
            total += n;
        }
        return bos.toByteArray();
    }

    private static String charsetFromContentType(String ct) {
        if (ct == null) {
            return null;
        }
        String lower = ct.toLowerCase();
        int i = lower.indexOf("charset=");
        if (i < 0) {
            return null;
        }
        String c = ct.substring(i + 8).trim();
        int sc = c.indexOf(';');
        if (sc > 0) {
            c = c.substring(0, sc).trim();
        }
        if (c.length() >= 2 && c.charAt(0) == '"' && c.charAt(c.length() - 1) == '"') {
            c = c.substring(1, c.length() - 1);
        }
        return c;
    }

    private static String absolutize(String base, String loc) {
        if (loc.indexOf("://") >= 0) {
            return loc;
        }
        if (loc.startsWith("//")) {
            int p = base.indexOf("://");
            if (p > 0) {
                return base.substring(0, p) + ":" + loc;
            }
            return "http:" + loc;
        }
        if (loc.startsWith("/")) {
            int p = base.indexOf("://");
            if (p < 0) {
                return loc;
            }
            int slash = base.indexOf('/', p + 3);
            if (slash < 0) {
                return base + loc;
            }
            return base.substring(0, slash) + loc;
        }
        int slash = base.lastIndexOf('/');
        if (slash < 0) {
            return loc;
        }
        return base.substring(0, slash + 1) + loc;
    }
}
