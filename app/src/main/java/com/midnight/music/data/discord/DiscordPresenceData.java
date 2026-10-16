package com.midnight.music.data.discord;

/**
 * Data representing active Discord presence state.
 */
public class DiscordPresenceData {
    private final String songTitle;
    private final String artist;
    private final String album;
    private final String imageUrl;
    private final boolean isPlaying;
    private final long startTimestampMs;
    private final long endTimestampMs;

    public DiscordPresenceData(String songTitle, String artist, String album, String imageUrl,
                               boolean isPlaying, long startTimestampMs, long endTimestampMs) {
        this.songTitle = songTitle;
        this.artist = artist;
        this.album = album;
        this.imageUrl = imageUrl;
        this.isPlaying = isPlaying;
        this.startTimestampMs = startTimestampMs;
        this.endTimestampMs = endTimestampMs;
    }

    public String getSongTitle() { return songTitle; }
    public String getTitle() { return songTitle; }
    public String getArtist() { return artist; }
    public String getAlbum() { return album; }
    public String getImageUrl() { return imageUrl; }
    public boolean isPlaying() { return isPlaying; }
    public long getStartTimestampMs() { return startTimestampMs; }
    public long getStartTimestamp() { return startTimestampMs; }
    public long getEndTimestampMs() { return endTimestampMs; }
    public long getEndTimestamp() { return endTimestampMs; }
    public String getButtonLabel() { return "Listen on Midnight"; }
}
