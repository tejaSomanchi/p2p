package com.appyhigh.p2pfiletransfer;

import android.net.Uri;

public class FileObject {
    String uri;
    String filename;
    String size;
    long fileSize;
    boolean isDownloaded;

    public FileObject(String uri, String filename, String size, long fileSize, boolean isDownloaded) {
        this.uri = uri;
        this.filename = filename;
        this.size = size;
        this.fileSize = fileSize;
        this.isDownloaded = isDownloaded;
    }

    public String getUri() {
        return uri;
    }

    public void setUri(String uri) {
        this.uri = uri;
    }

    public String getFilename() {
        return filename;
    }

    public void setFilename(String filename) {
        this.filename = filename;
    }

    public String getSize() {
        return size;
    }

    public void setSize(String size) {
        this.size = size;
    }

    public long getFileSize() {
        return fileSize;
    }

    public void setFileSize(long fileSize) {
        this.fileSize = fileSize;
    }

    public boolean isDownloaded() {
        return isDownloaded;
    }

    public void setDownloaded(boolean downloaded) {
        isDownloaded = downloaded;
    }
}
