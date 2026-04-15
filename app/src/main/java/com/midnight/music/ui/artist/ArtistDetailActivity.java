package com.midnight.music.ui.artist;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.midnight.music.R;
import com.midnight.music.data.model.Artist;
import com.midnight.music.data.model.Song;
import com.midnight.music.data.network.LastFmService;
import com.midnight.music.data.network.SaavnApiService;
import com.midnight.music.data.network.SaavnSearchResponse;
import com.midnight.music.data.network.SaavnSongResult;
import com.midnight.music.databinding.ActivityArtistDetailBinding;
import com.midnight.music.BuildConfig;
import com.midnight.music.player.MusicPlayerManager;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

public class ArtistDetailActivity extends AppCompatActivity {

    private static final String EXTRA_ARTIST_ID = "artist_id";
    private static final String EXTRA_ARTIST_NAME = "artist_name";
    private static final String EXTRA_ARTIST_IMAGE = "artist_image";

    private ActivityArtistDetailBinding binding;
    private ArtistSongAdapter adapter;
    private SaavnApiService saavnApi;
    private LastFmService lastFmApi;
    private String artistId;
    private String artistName;
    private String artistImage;

    public static void start(Context context, Artist artist) {
        if (artist == null || artist.getId() == null) {
            return;
        }
        Intent intent = new Intent(context, ArtistDetailActivity.class);
        intent.putExtra(EXTRA_ARTIST_ID, artist.getId());
        intent.putExtra(EXTRA_ARTIST_NAME, artist.getName());
        intent.putExtra(EXTRA_ARTIST_IMAGE, artist.getImageUrl());
        context.startActivity(intent);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityArtistDetailBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        artistId = getIntent().getStringExtra(EXTRA_ARTIST_ID);
        artistName = getIntent().getStringExtra(EXTRA_ARTIST_NAME);
        artistImage = getIntent().getStringExtra(EXTRA_ARTIST_IMAGE);

        if (artistId == null || artistId.isEmpty()) {
            Toast.makeText(this, "Invalid artist", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        setupApi();
        setupToolbar();
        setupRecyclerView();
        loadArtistSongs();
    }

    private void setupApi() {
        // Saavn API
        Retrofit saavnRetrofit = new Retrofit.Builder()
                .baseUrl(SaavnApiService.BASE_URL)
                .addConverterFactory(GsonConverterFactory.create())
                .build();
        saavnApi = saavnRetrofit.create(SaavnApiService.class);
        
        // Last.fm API
        Retrofit lastFmRetrofit = new Retrofit.Builder()
                .baseUrl(LastFmService.BASE_URL)
                .addConverterFactory(GsonConverterFactory.create())
                .build();
        lastFmApi = lastFmRetrofit.create(LastFmService.class);
    }

    private void setupToolbar() {
        binding.artistName.setText(artistName);
        binding.toolbar.setNavigationOnClickListener(v -> finish());

        if (artistImage != null && !artistImage.isEmpty()) {
            com.midnight.music.utils.ImageLoader.loadImage(binding.artistImage, artistImage);
        }
        
        // Show verified badge (could be fetched from artist data)
        binding.verifiedBadge.setVisibility(View.GONE);
    }

    private void setupRecyclerView() {
        adapter = new ArtistSongAdapter(new ArtistSongAdapter.SongClickListener() {
            @Override
            public void onSongClick(Song song) {
                MusicPlayerManager.getInstance(ArtistDetailActivity.this).playSong(song);
            }

            @Override
            public void onPlayClick(Song song, int position) {
                MusicPlayerManager.getInstance(ArtistDetailActivity.this).playSong(song);
            }
        });

        binding.songsRecycler.setLayoutManager(new LinearLayoutManager(this));
        binding.songsRecycler.setAdapter(adapter);
    }

    private void loadArtistSongs() {
        binding.progressBar.setVisibility(View.VISIBLE);

        // Use Last.fm to get artist top tracks
        String apiKey = BuildConfig.LASTFM_API_KEY;
        Call<LastFmService.ArtistTopTracksResponse> call = lastFmApi.getArtistTopTracks(artistName, apiKey, 20);
        call.enqueue(new Callback<LastFmService.ArtistTopTracksResponse>() {
            @Override
            public void onResponse(@NonNull Call<LastFmService.ArtistTopTracksResponse> call,
                               @NonNull Response<LastFmService.ArtistTopTracksResponse> response) {
                if (!isFinishing()) {
                    if (response.isSuccessful() && response.body() != null
                            && response.body().topTracks != null
                            && response.body().topTracks.tracks != null
                            && !response.body().topTracks.tracks.isEmpty()) {
                        
                        List<LastFmService.LastFmTrack> lastFmTracks = response.body().topTracks.tracks;

                        // Search all tracks in parallel for faster results
                        searchAllTracksInParallel(lastFmTracks, new ArrayList<>());
                    } else {
                        // Fallback: search Saavn
                        loadFromSaavn();
                    }
                }
            }

            @Override
            public void onFailure(@NonNull Call<LastFmService.ArtistTopTracksResponse> call, @NonNull Throwable t) {
                if (!isFinishing()) {
                    loadFromSaavn();
                }
            }
        });
    }
    
    private void loadFromSaavn() {
        // Fallback: search for songs by artist name
        Call<SaavnSearchResponse> call = saavnApi.searchSongs(artistName + " songs", 1, 20);
        call.enqueue(new Callback<SaavnSearchResponse>() {
            @Override
            public void onResponse(@NonNull Call<SaavnSearchResponse> call,
                               @NonNull Response<SaavnSearchResponse> response) {
                if (!isFinishing()) {
                    binding.progressBar.setVisibility(View.GONE);
                    
                    if (response.isSuccessful() && response.body() != null
                            && response.body().isSuccess() && response.body().getData() != null) {

                        List<SaavnSongResult> results = response.body().getData().getResults();
                        if (results != null && !results.isEmpty()) {
                            String preferredQuality = "320kbps";
                            List<Song> songs = new ArrayList<>();
                            for (SaavnSongResult result : results) {
                                songs.add(result.toSong(preferredQuality));
                            }
                            adapter.submitList(songs);
                        } else {
                            showEmptyState("No songs found");
                        }
                    } else {
                        showEmptyState("Failed to load songs");
                    }
                }
            }

            @Override
            public void onFailure(@NonNull Call<SaavnSearchResponse> call, @NonNull Throwable t) {
                if (!isFinishing()) {
                    binding.progressBar.setVisibility(View.GONE);
                    showEmptyState("Network error");
                }
            }

            private void showEmptyState(String message) {
                Toast.makeText(ArtistDetailActivity.this, message, Toast.LENGTH_SHORT).show();
                adapter.submitList(new ArrayList<>());
            }
        });
    }
    
    private void searchNextTrack(List<LastFmService.LastFmTrack> tracks, int index, List<Song> songs) {
        if (index >= tracks.size()) {
            binding.progressBar.setVisibility(View.GONE);
            if (!songs.isEmpty()) {
                adapter.submitList(songs);
            } else {
                showEmptyState("No songs found");
            }
            return;
        }

        if (isFinishing() || isDestroyed()) {
            return;
        }

        LastFmService.LastFmTrack track = tracks.get(index);
        String searchQuery = track.name + " " + track.getArtistName();

        saavnApi.searchSongs(searchQuery, 1, 1).enqueue(new Callback<SaavnSearchResponse>() {
            @Override
            public void onResponse(@NonNull Call<SaavnSearchResponse> call,
                               @NonNull Response<SaavnSearchResponse> response) {
                if (isFinishing() || isDestroyed()) {
                    return;
                }

                if (response.isSuccessful() && response.body() != null
                        && response.body().isSuccess() && response.body().getData() != null
                        && response.body().getData().getResults() != null
                        && !response.body().getData().getResults().isEmpty()) {

                    SaavnSongResult result = response.body().getData().getResults().get(0);
                    songs.add(result.toSong("320kbps"));
                }

                searchNextTrack(tracks, index + 1, songs);
            }

            @Override
            public void onFailure(@NonNull Call<SaavnSearchResponse> call, @NonNull Throwable t) {
                if (isFinishing() || isDestroyed()) {
                    return;
                }
                searchNextTrack(tracks, index + 1, songs);
            }
        });
    }

    private void searchAllTracksInParallel(List<LastFmService.LastFmTrack> tracks, List<Song> songs) {
        if (tracks.isEmpty()) {
            binding.progressBar.setVisibility(View.GONE);
            adapter.submitList(songs);
            return;
        }

        if (isFinishing() || isDestroyed()) {
            return;
        }

        binding.progressBar.setVisibility(View.VISIBLE);

        ExecutorService executor = Executors.newFixedThreadPool(Math.min(tracks.size(), 5));
        CountDownLatch latch = new CountDownLatch(tracks.size());

        for (LastFmService.LastFmTrack track : tracks) {
            executor.submit(() -> {
                String searchQuery = track.name + " " + track.getArtistName();
                try {
                    // Using synchronous call for parallel execution
                    retrofit2.Response<SaavnSearchResponse> response = saavnApi.searchSongs(searchQuery, 1, 1).execute();

                    if (!isFinishing() && !isDestroyed()) {
                        if (response.isSuccessful() && response.body() != null
                                && response.body().isSuccess() && response.body().getData() != null
                                && response.body().getData().getResults() != null
                                && !response.body().getData().getResults().isEmpty()) {

                            SaavnSongResult result = response.body().getData().getResults().get(0);
                            synchronized (songs) {
                                songs.add(result.toSong("320kbps"));
                            }
                        }
                    }
                } catch (Exception e) {
                    // Failure - skip this track
                } finally {
                    latch.countDown();
                }
            });
        }

        executor.submit(() -> {
            try {
                boolean completed = latch.await(30, TimeUnit.SECONDS);
                if (!isFinishing() && !isDestroyed()) {
                    new Handler(Looper.getMainLooper()).post(() -> {
                        binding.progressBar.setVisibility(View.GONE);
                        if (!songs.isEmpty()) {
                            adapter.submitList(new ArrayList<>(songs));
                        } else {
                            showEmptyState("No songs found");
                        }
                    });
                }
            } catch (InterruptedException e) {
                if (!isFinishing() && !isDestroyed()) {
                    new Handler(Looper.getMainLooper()).post(() -> {
                        binding.progressBar.setVisibility(View.GONE);
                        if (!songs.isEmpty()) {
                            adapter.submitList(new ArrayList<>(songs));
                        } else {
                            showEmptyState("No songs found");
                        }
                    });
                }
            } finally {
                executor.shutdown();
            }
        });
    }
    
    private void showEmptyState(String message) {
        Toast.makeText(ArtistDetailActivity.this, message, Toast.LENGTH_SHORT).show();
        adapter.submitList(new ArrayList<>());
    }
}