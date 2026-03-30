package com.midnight.music.data.network;

import com.google.gson.annotations.SerializedName;
import java.util.List;

/**
 * Wrapper response for the new saavn.dev-style API.
 * Structure: { success: bool, data: { total, start, results: [...] } }
 */
public class SaavnSearchResponse {
    @SerializedName("success")
    private boolean success;

    @SerializedName("data")
    private SearchData data;

    public boolean isSuccess() { return success; }
    public SearchData getData() { return data; }

    public static class SearchData {
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
