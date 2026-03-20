package com.midnight.music.data.api;

import com.midnight.music.data.model.github.GitHubRelease;
import retrofit2.Call;
import retrofit2.http.GET;
import retrofit2.http.Path;

public interface GitHubApiService {
    @GET("repos/{owner}/{repo}/releases/latest")
    Call<GitHubRelease> getLatestRelease(
            @Path("owner") String owner,
            @Path("repo") String repo
    );
}
