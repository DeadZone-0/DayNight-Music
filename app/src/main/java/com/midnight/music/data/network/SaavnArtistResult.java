package com.midnight.music.data.network;

import com.google.gson.annotations.SerializedName;
import com.midnight.music.data.model.Artist;

import java.util.List;

/**
 * Represents a single artist result from the API.
 */
public class SaavnArtistResult {
    @SerializedName("id")
    private String id;

    @SerializedName("name")
    private String name;

    @SerializedName("url")
    private String url;

    @SerializedName("image")
    private List<ImageUrl> image;

    @SerializedName("follower_count")
    private String followerCount;

    @SerializedName("language")
    private List<String> language;

    @SerializedName("is_verified")
    private boolean isVerified;

    public static class ImageUrl {
        @SerializedName("quality")
        private String quality;

        @SerializedName("url")
        private String url;

        public String getQuality() { return quality; }
        public String getUrl() { return url; }
    }

    public String getId() { return id; }
    public String getName() { return name; }
    public String getUrl() { return url; }
    public List<ImageUrl> getImage() { return image; }
    public String getFollowerCount() { return followerCount; }
    public List<String> getLanguage() { return language; }
    public boolean isVerified() { return isVerified; }

    private String getBestImageUrl() {
        if (image == null || image.isEmpty()) return null;
        return image.get(image.size() - 1).getUrl();
    }

    public Artist toArtist() {
        Artist artist = new Artist(id, name);
        artist.setImageUrl(getBestImageUrl());
        artist.setUrl(url);
        artist.setVerified(isVerified);
        try {
            if (followerCount != null) {
                String cleaned = followerCount.replace(",", "").replace(" ", "");
                artist.setFollowerCount(Integer.parseInt(cleaned));
            }
        } catch (NumberFormatException e) {
            artist.setFollowerCount(0);
        }
        if (language != null && !language.isEmpty()) {
            artist.setLanguage(String.join(", ", language));
        }
        return artist;
    }
}