package com.github.catvod.bean.samba;

public class Item {

    private final String name;
    private final boolean folder;

    public Item(String name, boolean folder) {
        this.name = name;
        this.folder = folder;
    }

    public String getName() {
        return name;
    }

    public boolean isFolder() {
        return folder;
    }
}
