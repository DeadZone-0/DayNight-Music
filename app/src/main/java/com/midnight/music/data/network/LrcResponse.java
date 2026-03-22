package com.midnight.music.data.network;

import com.google.gson.annotations.SerializedName;

/**
 * Response model for the LRCLib API.
 * Contains both plain and synced (timestamped) lyrics.
 */
public class LrcResponse {
    @SerializedName("id")
    private long id;

    @SerializedName("trackName")
    private String trackName;

    @SerializedName("artistName")
    private String artistName;

    @SerializedName("albumName")
    private String albumName;

    @SerializedName("duration")
    private double duration;

    @SerializedName("instrumental")
    private boolean instrumental;

    @SerializedName("plainLyrics")
    private String plainLyrics;

    @SerializedName("syncedLyrics")
    private String syncedLyrics;

    public long getId() { return id; }
    public String getTrackName() { return trackName; }
    public String getArtistName() { return artistName; }
    public String getAlbumName() { return albumName; }
    public double getDuration() { return duration; }
    public boolean isInstrumental() { return instrumental; }
    public String getPlainLyrics() { return plainLyrics; }
    public String getSyncedLyrics() { return syncedLyrics; }

    /**
     * Returns true if this response contains synced lyrics with timestamps.
     */
    public boolean hasSyncedLyrics() {
        return syncedLyrics != null && !syncedLyrics.isEmpty();
    }
}
