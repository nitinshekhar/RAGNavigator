package com.nitin.dto;

public class IndexRequest {
    private String directoryPath;

    public IndexRequest() {}

    public IndexRequest(String directoryPath) {
        this.directoryPath = directoryPath;
    }

    public String getDirectoryPath() { return directoryPath; }
    public void setDirectoryPath(String directoryPath) { this.directoryPath = directoryPath; }
}
