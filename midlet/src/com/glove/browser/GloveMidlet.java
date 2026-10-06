package com.glove.browser;

import javax.microedition.lcdui.Alert;
import javax.microedition.lcdui.AlertType;
import javax.microedition.lcdui.Command;
import javax.microedition.lcdui.CommandListener;
import javax.microedition.lcdui.Display;
import javax.microedition.lcdui.Displayable;
import javax.microedition.lcdui.Form;
import javax.microedition.lcdui.List;
import javax.microedition.lcdui.TextBox;
import javax.microedition.lcdui.TextField;
import javax.microedition.midlet.MIDlet;
import javax.microedition.midlet.MIDletStateChangeException;
import javax.microedition.rms.RecordStore;
import javax.microedition.rms.RecordStoreException;

/**
 * Installable Glove Browser MIDlet for Nokia Series 40 / Java MIDP phones.
 * Opens as its own application, not inside another browser.
 */
public class GloveMidlet extends MIDlet implements CommandListener, Runnable {

    private static final String RS_BOOKMARKS = "glove_bm";
    private static final String RS_HOME = "glove_home";
    private static final String DEFAULT_HOME = "http://ya.ru";
    private static final String PROXY_PREFIX = "http://glove.mineholde.pro/proxy?u=";

    private Display display;
    private Form homeForm;
    private TextField urlField;
    private TextBox pageBox;
    private List menuList;
    private List bookmarksList;

    private Command cmdGo;
    private Command cmdMenu;
    private Command cmdBack;
    private Command cmdExit;
    private Command cmdOk;
    private Command cmdAddBm;
    private Command cmdOpenBm;
    private Command cmdDelBm;

    private String currentUrl = DEFAULT_HOME;
    private String pendingUrl;
    private String pageTitle = "Glove";
    private boolean loading;
    private String loadError;

    protected void startApp() throws MIDletStateChangeException {
        display = Display.getDisplay(this);
        buildUi();
        String home = readHome();
        if (home != null && home.length() > 0) {
            currentUrl = home;
            urlField.setString(home);
        }
        display.setCurrent(homeForm);
    }

    protected void pauseApp() {
    }

    protected void destroyApp(boolean unconditional) {
    }

    private void buildUi() {
        cmdGo = new Command("Открыть", Command.OK, 1);
        cmdMenu = new Command("Меню", Command.SCREEN, 2);
        cmdBack = new Command("Назад", Command.BACK, 3);
        cmdExit = new Command("Выход", Command.EXIT, 4);
        cmdOk = new Command("OK", Command.OK, 1);
        cmdAddBm = new Command("В закладки", Command.SCREEN, 2);
        cmdOpenBm = new Command("Открыть", Command.ITEM, 1);
        cmdDelBm = new Command("Удалить", Command.ITEM, 2);

        homeForm = new Form("Glove Browser");
        urlField = new TextField("Адрес", currentUrl, 512, TextField.ANY);
        homeForm.append(urlField);
        homeForm.append("Отдельное Java-приложение. Яндекс, закладки, простой просмотр страниц.");
        homeForm.addCommand(cmdGo);
        homeForm.addCommand(cmdMenu);
        homeForm.addCommand(cmdExit);
        homeForm.setCommandListener(this);

        pageBox = new TextBox("Страница", "", 8192, TextField.ANY);
        pageBox.addCommand(cmdBack);
        pageBox.addCommand(cmdMenu);
        pageBox.addCommand(cmdAddBm);
        pageBox.addCommand(cmdGo);
        pageBox.setCommandListener(this);

        menuList = new List("Меню", List.IMPLICIT);
        menuList.append("Яндекс", null);
        menuList.append("Обновить", null);
        menuList.append("Закладки", null);
        menuList.append("Сохранить адрес как дом", null);
        menuList.append("О программе", null);
        menuList.addCommand(cmdBack);
        menuList.setCommandListener(this);

        bookmarksList = new List("Закладки", List.IMPLICIT);
        bookmarksList.addCommand(cmdBack);
        bookmarksList.addCommand(cmdOpenBm);
        bookmarksList.addCommand(cmdDelBm);
        bookmarksList.setCommandListener(this);
    }

    public void commandAction(Command c, Displayable d) {
        if (c == cmdExit) {
            notifyDestroyed();
            return;
        }
        if (c == cmdBack) {
            if (d == pageBox || d == menuList || d == bookmarksList) {
                display.setCurrent(homeForm);
            }
            return;
        }
        if (c == cmdGo) {
            startLoad(urlField.getString());
            return;
        }
        if (c == cmdMenu) {
            display.setCurrent(menuList);
            return;
        }
        if (c == cmdAddBm) {
            addBookmark(currentUrl);
            showInfo("Закладка сохранена");
            return;
        }
        if (d == menuList) {
            int i = menuList.getSelectedIndex();
            if (i == 0) {
                startLoad("http://ya.ru");
            } else if (i == 1) {
                startLoad(currentUrl);
            } else if (i == 2) {
                refreshBookmarks();
                display.setCurrent(bookmarksList);
            } else if (i == 3) {
                writeHome(urlField.getString());
                showInfo("Домашний адрес сохранён");
            } else if (i == 4) {
                showInfo("Glove Browser 1.0\nJava MIDP 2.0\nУстанавливается как .jar/.jad");
            }
            return;
        }
        if (d == bookmarksList) {
            int i = bookmarksList.getSelectedIndex();
            if (i < 0) {
                return;
            }
            String u = bookmarksList.getString(i);
            if (c == cmdDelBm) {
                deleteBookmark(i);
                refreshBookmarks();
            } else {
                startLoad(u);
            }
        }
    }

    private void startLoad(String raw) {
        if (raw == null) {
            return;
        }
        String url = raw.trim();
        if (url.length() == 0) {
            return;
        }
        if (url.indexOf("://") < 0) {
            if (url.indexOf('.') > 0 || url.indexOf('/') > 0) {
                url = "http://" + url;
            } else {
                url = "http://yandex.ru/yandsearch?text=" + encode(url);
            }
        }
        pendingUrl = url;
        urlField.setString(url);
        loading = true;
        loadError = null;
        pageBox.setString("Загрузка...\n" + url);
        pageBox.setTitle("Загрузка");
        display.setCurrent(pageBox);
        new Thread(this).start();
    }

    public void run() {
        String url = pendingUrl;
        String body = null;
        try {
            body = HttpFetcher.fetch(url, PROXY_PREFIX);
            currentUrl = url;
            pageTitle = extractTitle(body, url);
            String text = HtmlLite.toText(body);
            if (text.length() > 8000) {
                text = text.substring(0, 8000) + "\n...(обрезано)";
            }
            pageBox.setTitle(pageTitle);
            pageBox.setString(text);
            urlField.setString(url);
        } catch (Throwable t) {
            loadError = t.toString();
            pageBox.setTitle("Ошибка");
            pageBox.setString("Не удалось открыть:\n" + url + "\n\n" + loadError
                    + "\n\nПопробуйте http://ya.ru или другой HTTP-адрес.");
        }
        loading = false;
        display.setCurrent(pageBox);
    }

    private static String extractTitle(String html, String fallback) {
        if (html == null) {
            return fallback;
        }
        String lower = html.toLowerCase();
        int a = lower.indexOf("<title");
        if (a < 0) {
            return fallback;
        }
        a = lower.indexOf('>', a);
        int b = lower.indexOf("</title>", a);
        if (a < 0 || b < 0 || b <= a + 1) {
            return fallback;
        }
        String t = HtmlLite.decode(html.substring(a + 1, b)).trim();
        if (t.length() == 0) {
            return fallback;
        }
        if (t.length() > 28) {
            t = t.substring(0, 28);
        }
        return t;
    }

    private void showInfo(String msg) {
        Alert a = new Alert("Glove", msg, null, AlertType.INFO);
        a.setTimeout(2500);
        a.addCommand(cmdOk);
        display.setCurrent(a, homeForm);
    }

    private void refreshBookmarks() {
        bookmarksList.deleteAll();
        String[] all = readAllBookmarks();
        for (int i = 0; i < all.length; i++) {
            bookmarksList.append(all[i], null);
        }
        if (all.length == 0) {
            bookmarksList.append("(пусто)", null);
        }
    }

    private void addBookmark(String url) {
        if (url == null || url.length() == 0) {
            return;
        }
        try {
            RecordStore rs = RecordStore.openRecordStore(RS_BOOKMARKS, true);
            byte[] data = url.getBytes("UTF-8");
            rs.addRecord(data, 0, data.length);
            rs.closeRecordStore();
        } catch (Exception e) {
            // ignore
        }
    }

    private void deleteBookmark(int index) {
        try {
            RecordStore rs = RecordStore.openRecordStore(RS_BOOKMARKS, false);
            int[] ids = new int[rs.getNumRecords()];
            int n = 0;
            for (int id = 1; id <= rs.getNextRecordID() && n < ids.length; id++) {
                try {
                    rs.getRecord(id);
                    ids[n++] = id;
                } catch (RecordStoreException e) {
                    // hole
                }
            }
            if (index >= 0 && index < n) {
                rs.deleteRecord(ids[index]);
            }
            rs.closeRecordStore();
        } catch (Exception e) {
            // ignore
        }
    }

    private String[] readAllBookmarks() {
        try {
            RecordStore rs = RecordStore.openRecordStore(RS_BOOKMARKS, false);
            int count = rs.getNumRecords();
            String[] out = new String[count];
            int n = 0;
            for (int id = 1; id <= rs.getNextRecordID() && n < count; id++) {
                try {
                    byte[] data = rs.getRecord(id);
                    out[n++] = new String(data, "UTF-8");
                } catch (RecordStoreException e) {
                    // hole
                }
            }
            rs.closeRecordStore();
            if (n == count) {
                return out;
            }
            String[] trimmed = new String[n];
            System.arraycopy(out, 0, trimmed, 0, n);
            return trimmed;
        } catch (Exception e) {
            return new String[0];
        }
    }

    private String readHome() {
        try {
            RecordStore rs = RecordStore.openRecordStore(RS_HOME, false);
            byte[] data = rs.getRecord(1);
            rs.closeRecordStore();
            return new String(data, "UTF-8");
        } catch (Exception e) {
            return null;
        }
    }

    private void writeHome(String url) {
        if (url == null) {
            return;
        }
        try {
            try {
                RecordStore.deleteRecordStore(RS_HOME);
            } catch (Exception e) {
                // first time
            }
            RecordStore rs = RecordStore.openRecordStore(RS_HOME, true);
            byte[] data = url.getBytes("UTF-8");
            rs.addRecord(data, 0, data.length);
            rs.closeRecordStore();
        } catch (Exception e) {
            // ignore
        }
    }

    static String encode(String s) {
        StringBuffer sb = new StringBuffer();
        try {
            byte[] b = s.getBytes("UTF-8");
            for (int i = 0; i < b.length; i++) {
                int c = b[i] & 0xff;
                if ((c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z') || (c >= '0' && c <= '9')
                        || c == '-' || c == '_' || c == '.' || c == '~') {
                    sb.append((char) c);
                } else if (c == ' ') {
                    sb.append('+');
                } else {
                    sb.append('%');
                    String h = Integer.toHexString(c).toUpperCase();
                    if (h.length() < 2) {
                        sb.append('0');
                    }
                    sb.append(h);
                }
            }
        } catch (Exception e) {
            return s;
        }
        return sb.toString();
    }
}
