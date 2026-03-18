package com.midnight.music.data.network;

import com.google.gson.annotations.SerializedName;
import java.util.List;

public class PlaylistResponse {
    @SerializedName("list")
    private List<SongResponse> list1;

    @SerializedName("songs")
    private List<SongResponse> list2;

    public List<SongResponse> getSongs() {
        if (list1 != null)
            return list1;
        if (list2 != null)
            return list2;
        return null;
    }
}
