package com.github.catvod.bean.samba;

import com.hierynomus.msfscc.FileAttributes;
import com.hierynomus.msfscc.fileinformation.FileIdBothDirectoryInformation;
import com.hierynomus.protocol.commons.EnumWithValue;
import com.hierynomus.smbj.SMBClient;
import com.hierynomus.smbj.auth.AuthenticationContext;
import com.hierynomus.smbj.connection.Connection;
import com.hierynomus.smbj.session.Session;
import com.hierynomus.smbj.share.DiskShare;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * 所有用到 smbj 的代码都集中在这个类里，
 * 这样即使宿主 App 没有内置 smbj，配置解析和首页分类也不会受影响。
 */
public class Client {

    private final Drive drive;
    private SMBClient client;
    private Connection connection;
    private Session session;
    private DiskShare share;

    public Client(Drive drive) {
        this.drive = drive;
    }

    public synchronized List<Item> list(String path) throws IOException {
        List<Item> items = new ArrayList<>();
        for (FileIdBothDirectoryInformation item : getShare().list(path)) {
            boolean folder = EnumWithValue.EnumUtils.isSet(item.getFileAttributes(), FileAttributes.FILE_ATTRIBUTE_DIRECTORY);
            items.add(new Item(item.getFileName(), folder));
        }
        return items;
    }

    private DiskShare getShare() throws IOException {
        if (share == null || !share.isConnected()) connect();
        return share;
    }

    private void connect() throws IOException {
        release();
        int port = drive.getPort();
        client = new SMBClient();
        connection = port > 0 ? client.connect(drive.getHost(), port) : client.connect(drive.getHost());
        AuthenticationContext auth = drive.getUser().isEmpty() ? AuthenticationContext.guest() : new AuthenticationContext(drive.getUser(), drive.getPass().toCharArray(), drive.getDomain());
        session = connection.authenticate(auth);
        share = (DiskShare) session.connectShare(drive.getShareName());
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
}
