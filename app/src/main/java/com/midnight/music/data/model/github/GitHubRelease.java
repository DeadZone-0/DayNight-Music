package com.midnight.music.data.model.github;

import com.google.gson.annotations.SerializedName;
import java.util.List;

public class GitHubRelease {
    @SerializedName("tag_name")
    private String tagName;

    @SerializedName("name")
    private String name;

    @SerializedName("body")
    private String body; // Description/release notes

    @SerializedName("assets")
    private List<GitHubAsset> assets;

    public String getTagName() {
        return tagName;
    }

    public String getName() {
        return name;
    }

    public String getBody() {
        return body;
    }

    public List<GitHubAsset> getAssets() {
        return assets;
    }
    
    public boolean isMandatory() {
        return body != null && (body.contains("[MANDATORY]") || body.contains("[URGENT]"));
    }
}
