package com.midnight.music.data.model.github;

import com.google.gson.annotations.SerializedName;

public class GitHubAsset {
    @SerializedName("name")
    private String name;

    @SerializedName("browser_download_url")
    private String browserDownloadUrl;
    
    @SerializedName("content_type")
    private String contentType;

    public String getName() {
        return name;
    }

    public String getBrowserDownloadUrl() {
        return browserDownloadUrl;
    }

    public String getContentType() {
        return contentType;
    }
}
