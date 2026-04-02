package com.midnight.music.data.network;

import com.google.gson.annotations.SerializedName;
import java.util.List;

/**
 * Wrapper response for the new saavn.dev-style API for suggestions/similar songs.
 * Structure: { success: bool, data: [ { result1 }, { result2 } ] }
 */
public class SaavnSuggestionsResponse {
    @SerializedName("success")
    private boolean success;

    @SerializedName("data")
    private List<SaavnSongResult> data;

    public boolean isSuccess() { return success; }
    public List<SaavnSongResult> getData() { return data; }
}
