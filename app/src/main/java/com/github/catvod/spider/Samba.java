package com.github.catvod.spider;

import android.content.Context;
import android.text.TextUtils;

import com.github.catvod.bean.Class;
import com.github.catvod.bean.Result;
import com.github.catvod.bean.Vod;
import com.github.catvod.bean.samba.Client;
import com.github.catvod.bean.samba.Drive;
import com.github.catvod.bean.samba.Item;
import com.github.catvod.crawler.Spider;
import com.github.catvod.net.OkHttp;
import com.github.catvod.utils.Image;
import com.github.catvod.utils.Util;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class Samba extends Spider {

    private final Map<String, Client> clients = new HashMap<>();
    private List<Drive> drives;
    private String extend;
    private String error;

    private void fetchRule() {
        if (drives != null && !drives.isEmpty()) return;
        if (extend.startsWith("http")) extend = OkHttp.string(extend);
        drives = Drive.arrayFrom(extend);
    }

    private Drive getDrive(String name) throws IOException {
        int index = drives == null ? -1 : drives.indexOf(new Drive(name));
        if (index < 0) throw new IOException("未找到共享: " + name);
        return drives.get(index);
    }

    private synchronized Client getClient(Drive drive) {
        Client client = clients.get(drive.getName());
        if (client == null) {
            client = new Client(drive);
            clients.put(drive.getName(), client);
        }
        return client;
    }

    /**
     * 把异常变成能直接在界面上看到的文字。
     */
    private String describe(Throwable e) {
        if (e instanceof LinkageError || e instanceof ClassNotFoundException) return "缺少 SMB 库（当前 App 没有内置 smbj）";
        String msg = e.getMessage();
        return e.getClass().getSimpleName() + (TextUtils.isEmpty(msg) ? "" : ": " + msg);
    }

    private String errorList(Throwable e) {
        List<Vod> list = new ArrayList<>();
        list.add(new Vod("error", "⚠ " + describe(e), Image.VIDEO, "", false));
        return Result.get().vod(list).page().string();
    }

    @Override
    public void init(Context context, String extend) {
        this.extend = extend == null ? "" : extend;
        try {
            fetchRule();
        } catch (Throwable e) {
            error = describe(e);
        }
    }

    @Override
    public String homeContent(boolean filter) {
        List<Class> classes = new ArrayList<>();
        if (drives != null) for (Drive drive : drives) classes.add(drive.toType());
        if (classes.isEmpty()) {
            String text = error != null ? error : "没有读取到共享配置";
            classes.add(new Class("error", "⚠ " + text, "1"));
        }
        return Result.get().classes(classes).string();
    }

    @Override
    public String categoryContent(String tid, String pg, boolean filter, HashMap<String, String> extend) throws Exception {
        try {
            String key = tid.contains("/") ? tid.substring(0, tid.indexOf("/")) : tid;
            String path = tid.contains("/") ? tid.substring(tid.indexOf("/") + 1) : "";
            Drive drive = getDrive(key);
            List<Vod> list = new ArrayList<>();
            for (Item item : getList(drive, path)) {
                String vodId = getPath(key, path, item.getName());
                list.add(new Vod(vodId, item.getName(), item.isFolder() ? Image.FOLDER : Image.VIDEO, "", item.isFolder()));
            }
            return Result.get().vod(list).page().string();
        } catch (Throwable e) {
            return errorList(e);
        }
    }

    @Override
    public String detailContent(List<String> ids) throws Exception {
        Vod vod = new Vod();
        vod.setVodPic(Image.VIDEO);
        try {
            String tid = ids.get(0);
            String key = tid.contains("/") ? tid.substring(0, tid.indexOf("/")) : tid;
            String path = tid.contains("/") ? tid.substring(tid.indexOf("/") + 1) : "";
            String parent = path.contains("/") ? path.substring(0, path.lastIndexOf("/")) : "";
            String name = parent.contains("/") ? parent.substring(parent.lastIndexOf("/") + 1) : key;
            Drive drive = getDrive(key);
            List<String> playUrls = new ArrayList<>();
            for (Item item : getList(drive, parent)) {
                if (!item.isFolder()) {
                    playUrls.add(item.getName() + "$" + getPath(drive.getServer(), parent, item.getName()));
                }
            }
            vod.setVodId(name);
            vod.setVodName(name);
            vod.setVodPlayFrom(key);
            vod.setVodPlayUrl(TextUtils.join("#", playUrls));
        } catch (Throwable e) {
            vod.setVodId("error");
            vod.setVodName("⚠ " + describe(e));
        }
        return Result.string(vod);
    }

    @Override
    public String playerContent(String flag, String id, List<String> vipFlags) {
        return Result.get().url(id).string();
    }

    private String getPath(String... texts) {
        List<String> items = new ArrayList<>();
        for (String text : texts) if (!TextUtils.isEmpty(text)) items.add(text);
        return TextUtils.join("/", items);
    }

    private List<Item> getList(Drive drive, String path) throws IOException {
        List<Item> items = getClient(drive).list(getPath(drive.getSubPath(), path));
        Iterator<Item> iterator = items.iterator();
        while (iterator.hasNext()) {
            Item item = iterator.next();
            if (item.isFolder() && item.getName().startsWith(".")) iterator.remove();
            else if (!item.isFolder() && !Util.isMedia(item.getName())) iterator.remove();
        }
        Collections.sort(items, new Comparator<Item>() {
            @Override
            public int compare(Item o1, Item o2) {
                if (o1.isFolder() && !o2.isFolder()) return -1;
                if (!o1.isFolder() && o2.isFolder()) return 1;
                return o1.getName().toLowerCase(Locale.ROOT).compareTo(o2.getName().toLowerCase(Locale.ROOT));
            }
        });
        return items;
    }

    @Override
    public void destroy() {
        for (Client client : clients.values()) client.release();
        clients.clear();
    }
}
