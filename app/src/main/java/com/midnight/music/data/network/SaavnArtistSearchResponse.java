package com.midnight.music.data.network;

import com.google.gson.annotations.SerializedName;
import java.util.List;

/**
 * Wrapper response for artist search API.
 * Structure: { success: bool, data: { total, start, results: [...] } }
 */
public class SaavnArtistSearchResponse {
    @SerializedName("success")
    private boolean success;

    @SerializedName("data")
    private ArtistSearchData data;

    public boolean isSuccess() { return success; }
    public ArtistSearchData getData() { return data; }

    public static class ArtistSearchData {
        @SerializedName("total")
        private int total;

        @SerializedName("start")
        private int start;

        @SerializedName("results")
        private List<SaavnArtistResult> results;

        public int getTotal() { return total; }
        public int getStart() { return start; }
        public List<SaavnArtistResult> getResults() { return results; }
    }
}