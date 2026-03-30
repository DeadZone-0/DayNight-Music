package com.midnight.music.data.network;

import com.google.gson.annotations.SerializedName;
import com.midnight.music.data.model.Song;

import java.util.List;

/**
 * Represents a single song result from the new saavn.dev-style API.
 * Uses nested arrays for image qualities and download URLs.
 */
public class SaavnSongResult {
    @SerializedName("id")
    private String id;

    @SerializedName("name")
    private String name;

    @SerializedName("duration")
    private int duration;

    @SerializedName("year")
    private String year;

    @SerializedName("language")
    private String language;

    @SerializedName("url")
    private String url;

    @SerializedName("label")
    private String label;

    @SerializedName("hasLyrics")
    private boolean hasLyrics;

    @SerializedName("image")
    private List<QualityUrl> image;

    @SerializedName("downloadUrl")
    private List<QualityUrl> downloadUrl;

    @SerializedName("album")
    private Album album;

    @SerializedName("artists")
    private Artists artists;

    // Nested classes
    public static class QualityUrl {
        @SerializedName("quality")
        private String quality;

        @SerializedName("url")
        private String url;

        public String getQuality() { return quality; }
        public String getUrl() { return url; }
    }

    public static class Album {
        @SerializedName("id")
        private String id;

        @SerializedName("name")
        private String name;

        @SerializedName("url")
        private String url;

        public String getId() { return id; }
        public String getName() { return name; }
        public String getUrl() { return url; }
    }

    public static class Artists {
        @SerializedName("primary")
        private List<Artist> primary;

        @SerializedName("featured")
        private List<Artist> featured;

        @SerializedName("all")
        private List<Artist> all;

        public List<Artist> getPrimary() { return primary; }
        public List<Artist> getFeatured() { return featured; }
        public List<Artist> getAll() { return all; }
    }

    public static class Artist {
        @SerializedName("id")
        private String id;

        @SerializedName("name")
        private String name;

        public String getId() { return id; }
        public String getName() { return name; }
    }

    // Getters
    public String getId() { return id; }
    public String getName() { return name; }
    public int getDuration() { return duration; }
    public String getYear() { return year; }
    public String getLanguage() { return language; }
    public String getUrl() { return url; }
    public String getLabel() { return label; }
    public boolean isHasLyrics() { return hasLyrics; }
    public List<QualityUrl> getImage() { return image; }
    public List<QualityUrl> getDownloadUrl() { return downloadUrl; }
    public Album getAlbum() { return album; }
    public Artists getArtists() { return artists; }

    /**
     * Get the best quality image URL available.
     * Picks the last item in the array (usually highest quality).
     */
    private String getBestImageUrl() {
        if (image == null || image.isEmpty()) return null;
        return image.get(image.size() - 1).getUrl();
    }

    /**
     * Get the best quality download URL available.
     * Picks the last item (usually 320kbps).
     */
    private String getBestDownloadUrl() {
        if (downloadUrl == null || downloadUrl.isEmpty()) return null;
        return downloadUrl.get(downloadUrl.size() - 1).getUrl();
    }

    /**
     * Get a download URL matching the preferred quality.
     * Falls back to the highest available if the preferred quality isn't found.
     */
    private String getDownloadUrlForQuality(String preferredQuality) {
        if (downloadUrl == null || downloadUrl.isEmpty()) return null;
        if (preferredQuality == null) return getBestDownloadUrl();

        // Search for exact match
        for (QualityUrl q : downloadUrl) {
            if (preferredQuality.equals(q.getQuality())) {
                return q.getUrl();
            }
        }
        // Fallback to highest available
        return getBestDownloadUrl();
    }

    /**
     * Build a comma-separated artist string from the primary artists list.
     */
    private String getArtistString() {
        if (artists == null || artists.getPrimary() == null || artists.getPrimary().isEmpty()) {
            return "Unknown Artist";
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < artists.getPrimary().size(); i++) {
            if (i > 0) sb.append(", ");
            sb.append(artists.getPrimary().get(i).getName());
        }
        return sb.toString();
    }

    /**
     * Convert this new API response to the app's common Song model.
     * Uses the highest quality by default.
     */
    public Song toSong() {
        return toSong(null);
    }

    /**
     * Convert this new API response to the app's common Song model.
     * @param preferredQuality e.g. "96kbps", "160kbps", or "320kbps". Null = highest.
     */
    public Song toSong(String preferredQuality) {
        Song song = new Song(id);
        song.setSong(name);
        song.setSingers(getArtistString());
        song.setAlbum(album != null ? album.getName() : "");
        song.setAlbumUrl(album != null ? album.getUrl() : "");
        song.setDuration(String.valueOf(duration));
        song.setImageUrl(getBestImageUrl());
        song.setLanguage(language);
        String selectedUrl = getDownloadUrlForQuality(preferredQuality);
        song.setDownloadUrl(selectedUrl);
        song.setMediaUrl(selectedUrl);
        song.setYear(year);
        song.setPermaUrl(url);
        song.setLabel(label);
        song.setHasLyrics(hasLyrics);
        return song;
    }
}
