package servlet;

import java.io.File;

public class Upload {
    private String fileName;
    private String extension;
    private String contentType;
    private long size;
    private String path;

    public Upload() {
    }

    public Upload(String fileName, String extension, String contentType, long size, String path) {
        this.fileName = fileName;
        this.extension = extension;
        this.contentType = contentType;
        this.size = size;
        this.path = path;
    }

    public String getFileName() {
        return fileName;
    }

    public void setFileName(String fileName) {
        this.fileName = fileName;
    }

    public String getExtension() {
        return extension;
    }

    public void setExtension(String extension) {
        this.extension = extension;
    }

    public String getContentType() {
        return contentType;
    }

    public void setContentType(String contentType) {
        this.contentType = contentType;
    }

    public long getSize() {
        return size;
    }

    public void setSize(long size) {
        this.size = size;
    }

    public String getPath() {
        return path;
    }

    public void setPath(String path) {
        this.path = path;
    }

    public File getFile() {
        return path != null ? new File(path) : null;
    }

    @Override
    public String toString() {
        return "Upload{" +
                "fileName='" + fileName + '\'' +
                ", extension='" + extension + '\'' +
                ", contentType='" + contentType + '\'' +
                ", size=" + size +
                ", path='" + path + '\'' +
                '}';
    }
}
