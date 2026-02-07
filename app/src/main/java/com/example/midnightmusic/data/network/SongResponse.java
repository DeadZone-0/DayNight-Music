package com.example.midnightmusic.data.network;

import com.example.midnightmusic.data.model.Song;
import com.google.gson.annotations.SerializedName;

/**
 * Response object for JioSaavn API
 * Maps API response to Song model
 */
public class SongResponse {
    @SerializedName("id")
    private String id;

    @SerializedName("song")
    private String song;

    @SerializedName("singers")
    private String singers;

    @SerializedName("album")
    private String album;

    @SerializedName("album_url")
    private String albumUrl;

    @SerializedName("duration")
    private String duration;

    @SerializedName("image")
    private String image;

    @SerializedName("image_url")
    private String imageUrl;

    @SerializedName("language")
    private String language;

    @SerializedName("url")
    private String downloadUrl;

    @SerializedName("media_url")
    private String mediaUrl;

    @SerializedName("year")
    private String year;

    @SerializedName("lyrics")
    private String lyrics;

    @SerializedName("perma_url")
    private String permaUrl;

    @SerializedName("label")
    private String label;

    @SerializedName("has_lyrics")
    private boolean hasLyrics;

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getSong() {
        return song;
    }

    public void setSong(String song) {
        this.song = song;
    }

    public String getSingers() {
        return singers;
    }

    public void setSingers(String singers) {
        this.singers = singers;
    }

    public String getAlbum() {
        return album;
    }

    public void setAlbum(String album) {
        this.album = album;
    }

    public String getAlbumUrl() {
        return albumUrl;
    }

    public void setAlbumUrl(String albumUrl) {
        this.albumUrl = albumUrl;
    }

    public String getDuration() {
        return duration;
    }

    public void setDuration(String duration) {
        this.duration = duration;
    }

    public String getImage() {
        return image;
    }

    public void setImage(String image) {
        this.image = image;
    }

    public String getImageUrl() {
        return imageUrl;
    }

    public void setImageUrl(String imageUrl) {
        this.imageUrl = imageUrl;
    }

    public String getLanguage() {
        return language;
    }

    public void setLanguage(String language) {
        this.language = language;
    }

    public String getDownloadUrl() {
        return downloadUrl;
    }

    public void setDownloadUrl(String downloadUrl) {
        this.downloadUrl = downloadUrl;
    }

    public String getMediaUrl() {
        return mediaUrl;
    }

    public void setMediaUrl(String mediaUrl) {
        this.mediaUrl = mediaUrl;
    }

    public String getYear() {
        return year;
    }

    public void setYear(String year) {
        this.year = year;
    }

    public String getLyrics() {
        return lyrics;
    }

    public void setLyrics(String lyrics) {
        this.lyrics = lyrics;
    }

    public String getPermaUrl() {
        return permaUrl;
    }

    public void setPermaUrl(String permaUrl) {
        this.permaUrl = permaUrl;
    }

    public String getLabel() {
        return label;
    }

    public void setLabel(String label) {
        this.label = label;
    }

    public boolean isHasLyrics() {
        return hasLyrics;
    }

    public void setHasLyrics(boolean hasLyrics) {
        this.hasLyrics = hasLyrics;
    }

    /**
     * Convert SongResponse to Song model
     * @return Song object
     */
    public Song toSong() {
        Song song = new Song(id);
        song.setSong(this.song);
        song.setSingers(this.singers);
        song.setAlbum(this.album);
        song.setAlbumUrl(this.albumUrl);
        song.setDuration(this.duration);
        if (this.imageUrl != null) {
            song.setImageUrl(this.imageUrl);
        } else if (this.image != null) {
            song.setImage(this.image);
        }
        song.setLanguage(this.language);
        song.setDownloadUrl(this.downloadUrl);
        song.setMediaUrl(this.mediaUrl);
        song.setYear(this.year);
        song.setLyrics(this.lyrics);
        song.setPermaUrl(this.permaUrl);
        song.setLabel(this.label);
        song.setHasLyrics(this.hasLyrics);
        return song;
    }
} 