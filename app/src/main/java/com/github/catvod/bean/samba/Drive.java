package com.github.catvod.bean.samba;

import android.net.Uri;
import android.text.TextUtils;

import com.github.catvod.bean.Class;
import com.google.gson.Gson;
import com.google.gson.annotations.SerializedName;
import com.google.gson.reflect.TypeToken;
import com.hierynomus.smbj.SMBClient;
import com.hierynomus.smbj.auth.AuthenticationContext;
import com.hierynomus.smbj.connection.Connection;
import com.hierynomus.smbj.session.Session;
import com.hierynomus.smbj.share.DiskShare;

import java.io.IOException;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;

public class Drive {

    @SerializedName("drives")
    private List<Drive> drives;
    @SerializedName("name")
    private String name;
    @SerializedName("server")
    private String server;
    @SerializedName(value = "user", alternate = {"username"})
    private String user;
    @SerializedName(value = "pass", alternate = {"password"})
    private String pass;
    @SerializedName("domain")
    private String domain;

    private transient SMBClient client;
    private transient Connection connection;
    private transient Session session;
    private transient DiskShare share;
    private transient Uri uri;

    /**
     * 支持两种配置：
     * 1. 数组：[{"name":"NAS","server":"smb://192.168.1.2/video/movies","user":"","pass":""}]
     * 2. 对象：{"drives":[ ...同上... ]}
     */
    public static List<Drive> arrayFrom(String str) {
        str = str == null ? "" : str.trim();
        if (str.startsWith("{")) {
            Drive root = new Gson().fromJson(str, Drive.class);
            return root == null || root.drives == null ? new ArrayList<>() : root.drives;
        }
        Type type = new TypeToken<List<Drive>>() {}.getType();
        List<Drive> items = new Gson().fromJson(str, type);
        return items == null ? new ArrayList<>() : items;
    }

    public Drive() {
    }

    public Drive(String name) {
        this.name = name;
    }

    public String getName() {
        return TextUtils.isEmpty(name) ? "" : name;
    }

    private Uri getUri() {
        if (uri == null) uri = Uri.parse(TextUtils.isEmpty(server) ? "" : server.trim());
        return uri;
    }

    private String getHost() {
        String host = getUri().getHost();
        return host == null ? "" : host;
    }

    private String getShareName() {
        List<String> segments = getUri().getPathSegments();
        return segments.isEmpty() ? "" : segments.get(0);
    }

    /**
     * 共享名之后的子目录，例如 smb://host/video/movies/2024 → movies/2024
     */
    public String getSubPath() {
        List<String> segments = getUri().getPathSegments();
        return segments.size() <= 1 ? "" : TextUtils.join("/", segments.subList(1, segments.size()));
    }

    private String getUser() {
        if (!TextUtils.isEmpty(user)) return user;
        String info = getUri().getUserInfo();
        if (TextUtils.isEmpty(info)) return "";
        return info.contains(":") ? info.substring(0, info.indexOf(":")) : info;
    }

    private String getPass() {
        if (!TextUtils.isEmpty(user)) return TextUtils.isEmpty(pass) ? "" : pass;
        String info = getUri().getUserInfo();
        return TextUtils.isEmpty(info) || !info.contains(":") ? "" : info.substring(info.indexOf(":") + 1);
    }

    private String getDomain() {
        return TextUtils.isEmpty(domain) ? "" : domain;
    }

    /**
     * 播放用的基础地址：smb://[用户:密码@]主机[:端口]/共享名[/子目录]
     */
    public String getServer() {
        StringBuilder sb = new StringBuilder("smb://");
        if (!getUser().isEmpty()) {
            sb.append(Uri.encode(getUser()));
            if (!getPass().isEmpty()) sb.append(":").append(Uri.encode(getPass()));
            sb.append("@");
        }
        sb.append(getHost());
        if (getUri().getPort() > 0) sb.append(":").append(getUri().getPort());
        sb.append("/").append(getShareName());
        if (!getSubPath().isEmpty()) sb.append("/").append(getSubPath());
        return sb.toString();
    }

    public synchronized DiskShare getShare() throws IOException {
        if (share == null || !share.isConnected()) connect();
        return share;
    }

    private void connect() throws IOException {
        release();
        int port = getUri().getPort();
        client = new SMBClient();
        connection = port > 0 ? client.connect(getHost(), port) : client.connect(getHost());
        AuthenticationContext auth = getUser().isEmpty() ? AuthenticationContext.guest() : new AuthenticationContext(getUser(), getPass().toCharArray(), getDomain());
        session = connection.authenticate(auth);
        share = (DiskShare) session.connectShare(getShareName());
    }

    public synchronized void release() {
        try {
            if (share != null) share.close();
        } catch (Exception ignored) {
        }
        try {
            if (session != null) session.close();
        } catch (Exception ignored) {
        }
        try {
            if (connection != null) connection.close();
        } catch (Exception ignored) {
        }
        try {
            if (client != null) client.close();
        } catch (Exception ignored) {
        }
        share = null;
        session = null;
        connection = null;
        client = null;
    }

    public Class toType() {
        return new Class(getName(), getName(), "1");
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (!(obj instanceof Drive)) return false;
        Drive it = (Drive) obj;
        return getName().equals(it.getName());
    }

    @Override
    public int hashCode() {
        return getName().hashCode();
    }
}
