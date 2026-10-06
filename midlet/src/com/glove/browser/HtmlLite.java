package com.glove.browser;

/**
 * Minimal HTML → plain text for MIDP TextBox display.
 */
public final class HtmlLite {

    private HtmlLite() {
    }

    public static String toText(String html) {
        if (html == null) {
            return "";
        }
        String s = stripScripts(html);
        StringBuffer out = new StringBuffer(s.length());
        boolean inTag = false;
        boolean space = true;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '<') {
                String rest = s.substring(i).toLowerCase();
                if (rest.startsWith("<br") || rest.startsWith("<p") || rest.startsWith("<div")
                        || rest.startsWith("<tr") || rest.startsWith("<li") || rest.startsWith("<h")) {
                    if (!space) {
                        out.append('\n');
                        space = true;
                    }
                }
                if (rest.startsWith("<a ")) {
                    int href = rest.indexOf("href=");
                    if (href > 0 && href < 120) {
                        int q = rest.charAt(href + 5);
                        int start = href + 6;
                        int end;
                        if (q == '"' || q == '\'') {
                            end = rest.indexOf((char) q, start);
                        } else {
                            end = rest.indexOf(' ', start);
                            int gt = rest.indexOf('>', start);
                            if (end < 0 || (gt > 0 && gt < end)) {
                                end = gt;
                            }
                        }
                        if (end > start) {
                            String link = s.substring(i + start, i + end);
                            out.append('[');
                            out.append(decode(link));
                            out.append("] ");
                            space = false;
                        }
                    }
                }
                inTag = true;
                continue;
            }
            if (c == '>') {
                inTag = false;
                continue;
            }
            if (inTag) {
                continue;
            }
            if (c == '&') {
                int semi = s.indexOf(';', i + 1);
                if (semi > i && semi - i < 10) {
                    String ent = s.substring(i, semi + 1);
                    out.append(decodeEntity(ent));
                    i = semi;
                    space = false;
                    continue;
                }
            }
            if (c == '\r') {
                continue;
            }
            if (c == '\n' || c == '\t') {
                c = ' ';
            }
            if (c == ' ') {
                if (!space) {
                    out.append(' ');
                    space = true;
                }
            } else {
                out.append(c);
                space = false;
            }
        }
        return out.toString().trim();
    }

    private static String stripScripts(String html) {
        StringBuffer sb = new StringBuffer(html.length());
        String lower = html.toLowerCase();
        int i = 0;
        while (i < html.length()) {
            int a = lower.indexOf("<script", i);
            int b = lower.indexOf("<style", i);
            int c = lower.indexOf("<!--", i);
            int next = minPos(a, b, c);
            if (next < 0) {
                sb.append(html.substring(i));
                break;
            }
            sb.append(html.substring(i, next));
            String end;
            if (next == a) {
                end = "</script>";
            } else if (next == b) {
                end = "</style>";
            } else {
                end = "-->";
            }
            int e = lower.indexOf(end, next);
            if (e < 0) {
                break;
            }
            i = e + end.length();
        }
        return sb.toString();
    }

    private static int minPos(int a, int b, int c) {
        int m = -1;
        if (a >= 0) {
            m = a;
        }
        if (b >= 0 && (m < 0 || b < m)) {
            m = b;
        }
        if (c >= 0 && (m < 0 || c < m)) {
            m = c;
        }
        return m;
    }

    public static String decode(String s) {
        if (s == null) {
            return "";
        }
        StringBuffer out = new StringBuffer(s.length());
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '&') {
                int semi = s.indexOf(';', i + 1);
                if (semi > i && semi - i < 10) {
                    out.append(decodeEntity(s.substring(i, semi + 1)));
                    i = semi;
                    continue;
                }
            }
            out.append(c);
        }
        return out.toString();
    }

    private static char decodeEntity(String ent) {
        if ("&amp;".equals(ent)) {
            return '&';
        }
        if ("&lt;".equals(ent)) {
            return '<';
        }
        if ("&gt;".equals(ent)) {
            return '>';
        }
        if ("&quot;".equals(ent)) {
            return '"';
        }
        if ("&nbsp;".equals(ent)) {
            return ' ';
        }
        if (ent.startsWith("&#x") || ent.startsWith("&#X")) {
            try {
                return (char) Integer.parseInt(ent.substring(3, ent.length() - 1), 16);
            } catch (Exception e) {
                return '?';
            }
        }
        if (ent.startsWith("&#")) {
            try {
                return (char) Integer.parseInt(ent.substring(2, ent.length() - 1));
            } catch (Exception e) {
                return '?';
            }
        }
        return '?';
    }
}
