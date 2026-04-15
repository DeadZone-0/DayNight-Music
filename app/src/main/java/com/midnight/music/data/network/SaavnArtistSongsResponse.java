package com.midnight.music.data.network;

import com.google.gson.annotations.SerializedName;
import java.util.List;

/**
 * Wrapper response for artist top songs API.
 * Structure: { success: bool, data: { total, start, results: [...] } }
 */
public class SaavnArtistSongsResponse {
    @SerializedName("success")
    private boolean success;

    @SerializedName("data")
    private ArtistSongsData data;

    public boolean isSuccess() { return success; }
    public ArtistSongsData getData() { return data; }

    public static class ArtistSongsData {
        @SerializedName("total")
        private int total;

        @SerializedName("start")
        private int start;

        @SerializedName("results")
        private List<SaavnSongResult> results;

        public int getTotal() { return total; }
        public int getStart() { return start; }
        public List<SaavnSongResult> getResults() { return results; }
    }
}