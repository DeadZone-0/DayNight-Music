package com.example.midnightmusic.ui.search;

import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.LiveData;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.midnightmusic.R;
import com.example.midnightmusic.databinding.FragmentSearchBinding;
import com.example.midnightmusic.models.Genre;
import com.example.midnightmusic.ui.adapters.GenreAdapter;
import com.example.midnightmusic.data.model.Song;
import com.example.midnightmusic.data.network.JioSaavnService;
import com.google.android.material.textfield.TextInputEditText;
import com.example.midnightmusic.player.MusicPlayerManager;
import com.example.midnightmusic.ui.player.PlayerActivity;
import com.example.midnightmusic.data.db.AppDatabase;
import com.example.midnightmusic.data.model.PlaylistSongCrossRef;
import com.example.midnightmusic.data.model.Playlist;
import com.example.midnightmusic.data.network.SongResponse;
import com.example.midnightmusic.data.model.PlaylistWithSongs;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

public class SearchFragment extends Fragment implements GenreAdapter.OnGenreClickListener {
    private FragmentSearchBinding binding;
    private SearchAdapter searchAdapter;
    private GenreAdapter genreAdapter;
    private JioSaavnService api;
    private RecyclerView.LayoutManager searchLayoutManager;
    private RecyclerView.LayoutManager genreLayoutManager;
    private boolean isShowingGenres = true;

    // This will track the currently displayed playlist dialog
    private AlertDialog currentPlaylistDialog = null;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                           @Nullable ViewGroup container,
                           @Nullable Bundle savedInstanceState) {
        binding = FragmentSearchBinding.inflate(inflater, container, false);
        setupRetrofit();
        return binding.getRoot();
    }

    private void setupRetrofit() {
        Retrofit retrofit = new Retrofit.Builder()
                .baseUrl(JioSaavnService.BASE_URL)
                .addConverterFactory(GsonConverterFactory.create())
                .build();
        api = retrofit.create(JioSaavnService.class);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        setupLayoutManagers();
        setupSearchInput();
        setupRecyclerView();
        setupGenresGrid();
        showGenresView();
    }

    private void setupLayoutManagers() {
        searchLayoutManager = new LinearLayoutManager(requireContext());
        genreLayoutManager = new GridLayoutManager(requireContext(), 2);
    }

    private void setupSearchInput() {
        TextInputEditText searchInput = binding.searchInput;
        searchInput.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_SEARCH ||
                (event != null && event.getKeyCode() == KeyEvent.KEYCODE_ENTER)) {
                String query = searchInput.getText().toString().trim();
                if (query.isEmpty()) {
                    showGenresView();
                } else {
                    performSearch(query);
                }
                return true;
            }
            return false;
        });

        searchInput.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {}

            @Override
            public void afterTextChanged(Editable s) {
                if (s.toString().trim().isEmpty()) {
                    showGenresView();
                }
            }
        });
    }

    private void setupRecyclerView() {
        searchAdapter = new SearchAdapter(new SearchAdapter.SearchAdapterListener() {
            @Override
            public void onSongClick(Song song) {
                MusicPlayerManager.getInstance(requireContext()).playSong(song);
            }

            @Override
            public void onPlayNow(Song song) {
                MusicPlayerManager.getInstance(requireContext()).playSong(song);
            }

            @Override
            public void onAddToPlaylist(Song song) {
                // Show playlist selection dialog
                showPlaylistsDialog(song);
            }

            @Override
            public void onQueueNext(Song song) {
                MusicPlayerManager.getInstance(requireContext()).addToQueue(song);
                Toast.makeText(requireContext(), "Added to queue: " + song.getSong(), Toast.LENGTH_SHORT).show();
            }

            @Override
            public void onToggleLike(Song song) {
                if (song == null || song.getId() == null) {
                    Toast.makeText(requireContext(), "Error: Invalid song data", Toast.LENGTH_SHORT).show();
                    return;
                }

                // Toggle the like status in memory
                final boolean newIsLiked = !song.isLiked();
                song.setLiked(newIsLiked);
                
                // Immediately update the UI
                searchAdapter.notifyDataSetChanged();
                
                // Show feedback to user based on action
                String message = newIsLiked ? "Added to Liked Songs" : "Removed from Liked Songs";
                Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show();
                
                // Save the change to the database in background
                AppDatabase db = AppDatabase.getInstance(requireContext());
                Executor executor = Executors.newSingleThreadExecutor();
                
                executor.execute(() -> {
                    try {
                        // Get the Liked Songs playlist
                        Playlist likedSongsPlaylist = db.playlistDao().getPlaylistByNameSync(getString(R.string.liked_songs));
                        
                        // Create it if it doesn't exist
                        if (likedSongsPlaylist == null) {
                            likedSongsPlaylist = new Playlist(getString(R.string.liked_songs));
                            likedSongsPlaylist.setCreatedAt(System.currentTimeMillis());
                            long newPlaylistId = db.playlistDao().insert(likedSongsPlaylist);
                            likedSongsPlaylist.setId(newPlaylistId);
                        }
                        
                        // Update song liked status in the database
                        Song existingSong = db.songDao().getSongByIdSync(song.getId());
                        if (existingSong != null) {
                            // Update the existing song's liked status
                            existingSong.setLiked(newIsLiked);
                            db.songDao().updateLikedStatus(existingSong.getId(), newIsLiked);
                        } else {
                            // First time seeing this song, insert it
                            db.songDao().insert(song);
                        }
                        
                        // Add to or remove from Liked Songs playlist
                        if (newIsLiked) {
                            // Add to Liked Songs playlist
                            PlaylistSongCrossRef crossRef = new PlaylistSongCrossRef(likedSongsPlaylist.getId(), song.getId());
                            db.playlistDao().insert(crossRef);
                        } else {
                            // Remove from Liked Songs playlist
                            db.playlistDao().removeSongFromPlaylist(likedSongsPlaylist.getId(), song.getId());
                        }
                    } catch (Exception e) {
                        e.printStackTrace();
                        new Handler(Looper.getMainLooper()).post(() -> 
                            Toast.makeText(requireContext(), 
                                "Error updating like status: " + e.getMessage(), 
                                Toast.LENGTH_SHORT).show()
                        );
                    }
                });
            }
        });
    }

    private void performSearch(String query) {
        if (query == null || query.trim().isEmpty()) {
            showGenresView();
            return;
        }

        showLoadingState();
        
        api.searchSongs(query.trim(), true).enqueue(new Callback<List<SongResponse>>() {
            @Override
            public void onResponse(@NonNull Call<List<SongResponse>> call, @NonNull Response<List<SongResponse>> response) {
                if (!isAdded()) return;
                
                if (response.isSuccessful() && response.body() != null) {
                    List<SongResponse> songResponses = response.body();
                    if (songResponses.isEmpty()) {
                        showEmptyState();
                    } else {
                        // Convert SongResponse to Song
                        List<Song> songs = new ArrayList<>();
                        for (SongResponse songResponse : songResponses) {
                            songs.add(songResponse.toSong());
                        }
                        showSearchResults(songs);
                    }
                } else {
                    showError("Failed to get search results");
                }
            }

            @Override
            public void onFailure(@NonNull Call<List<SongResponse>> call, @NonNull Throwable t) {
                if (!isAdded()) return;
                showError("Network error: " + t.getMessage());
            }
        });
    }

    private void showLoadingState() {
        binding.progressBar.setVisibility(View.VISIBLE);
        binding.searchResultsRecycler.setVisibility(View.GONE);
        binding.emptyState.setVisibility(View.GONE);
    }

    private void showSearchResults(List<Song> songs) {
        if (isShowingGenres) {
            isShowingGenres = false;
            binding.searchResultsRecycler.setLayoutManager(searchLayoutManager);
            binding.searchResultsRecycler.setAdapter(searchAdapter);
        }
        
        // Check if songs are liked in database before displaying
        AppDatabase db = AppDatabase.getInstance(requireContext());
        Executor executor = Executors.newSingleThreadExecutor();
        
        // First show the results with default state (not liked)
        binding.progressBar.setVisibility(View.GONE);
        binding.emptyState.setVisibility(View.GONE);
        binding.searchResultsRecycler.setVisibility(View.VISIBLE);
        searchAdapter.submitList(new ArrayList<>(songs));  // Use a copy
        
        // Then update in background with actual like status
        executor.execute(() -> {
            try {
                // Create a copy we can modify
                List<Song> updatedSongs = new ArrayList<>(songs.size());
                
                // Check each song's liked status
                for (Song song : songs) {
                    // Get a fresh copy with the correct like status
                    Song existingSong = db.songDao().getSongByIdSync(song.getId());
                    if (existingSong != null) {
                        // If the song exists in the database, copy its liked status
                        song.setLiked(existingSong.isLiked());
                    }
                    updatedSongs.add(song);
                }
                
                // Update UI on main thread with the correct like status
                new Handler(Looper.getMainLooper()).post(() -> {
                    searchAdapter.submitList(updatedSongs);
                });
            } catch (Exception e) {
                e.printStackTrace();
            }
        });
    }

    private void showEmptyState() {
        binding.progressBar.setVisibility(View.GONE);
        binding.searchResultsRecycler.setVisibility(View.GONE);
        binding.emptyState.setVisibility(View.VISIBLE);
        binding.emptyState.setText("No results found");
    }

    private void showError(String message) {
        binding.progressBar.setVisibility(View.GONE);
        binding.searchResultsRecycler.setVisibility(View.GONE);
        binding.emptyState.setVisibility(View.VISIBLE);
        binding.emptyState.setText(message);
    }

    private void showGenresView() {
        if (!isShowingGenres) {
            isShowingGenres = true;
            binding.searchResultsRecycler.setLayoutManager(genreLayoutManager);
            binding.searchResultsRecycler.setAdapter(genreAdapter);
        }
        
        binding.progressBar.setVisibility(View.GONE);
        binding.emptyState.setVisibility(View.GONE);
        binding.searchResultsRecycler.setVisibility(View.VISIBLE);
    }

    private void setupGenresGrid() {
        List<Genre> genres = getGenres();
        genreAdapter = new GenreAdapter(genres, this);
    }

    private List<Genre> getGenres() {
        List<Genre> genres = new ArrayList<>();
        
        genres.add(new Genre("1", "Pop", null, Color.parseColor("#FF1DB954")));
        genres.add(new Genre("2", "Rock", null, Color.parseColor("#FF1E3264")));
        genres.add(new Genre("3", "Hip-Hop", null, Color.parseColor("#FF7358FF")));
        genres.add(new Genre("4", "Jazz", null, Color.parseColor("#FFE8115B")));
        genres.add(new Genre("5", "Electronic", null, Color.parseColor("#FF148A08")));
        genres.add(new Genre("6", "Classical", null, Color.parseColor("#FF8400E7")));
        genres.add(new Genre("7", "R&B", null, Color.parseColor("#FFE91429")));
        genres.add(new Genre("8", "Metal", null, Color.parseColor("#FF777777")));
        genres.add(new Genre("9", "Folk", null, Color.parseColor("#FF537AA1")));
        genres.add(new Genre("10", "Latin", null, Color.parseColor("#FFBC5900")));
        genres.add(new Genre("11", "Indie", null, Color.parseColor("#FF006450")));
        genres.add(new Genre("12", "Podcasts", null, Color.parseColor("#FF8C1932")));
        
        return genres;
    }

    @Override
    public void onGenreClick(Genre genre) {
        performSearch(genre.getName());
    }

    private void showPlaylistsDialog(Song song) {
        // If a dialog is already showing, dismiss it first
        if (currentPlaylistDialog != null && currentPlaylistDialog.isShowing()) {
            currentPlaylistDialog.dismiss();
        }

        // Get the playlists from the database
        AppDatabase db = AppDatabase.getInstance(requireContext());
        
        // Create a new LiveData instance each time to avoid multiple observers
        LiveData<List<PlaylistWithSongs>> playlistsLiveData = db.playlistDao().getAllPlaylistsWithSongs();
        
        // Make sure we unregister any previous observers first
        playlistsLiveData.removeObservers(getViewLifecycleOwner());
        
        // Use AtomicBoolean to ensure we only handle the callback once
        final boolean[] observerCalled = {false};
        
        // Now observe only once
        playlistsLiveData.observe(getViewLifecycleOwner(), playlists -> {
            // Prevent multiple callbacks
            if (observerCalled[0]) {
                return;
            }
            observerCalled[0] = true;
            
            // Remove the observer immediately to prevent multiple callbacks
            playlistsLiveData.removeObservers(getViewLifecycleOwner());
            
            if (playlists == null || playlists.isEmpty()) {
                Toast.makeText(requireContext(), "No playlists available", Toast.LENGTH_SHORT).show();
                return;
            }

            String[] playlistNames = new String[playlists.size()];
            long[] playlistIds = new long[playlists.size()];
            boolean[] checkedItems = new boolean[playlists.size()];
            
            for (int i = 0; i < playlists.size(); i++) {
                playlistNames[i] = playlists.get(i).playlist.getName();
                playlistIds[i] = playlists.get(i).playlist.getId();
                
                // Check if the song is already in this playlist
                if (playlists.get(i).songs != null) {
                    for (Song playlistSong : playlists.get(i).songs) {
                        if (playlistSong.getId().equals(song.getId())) {
                            checkedItems[i] = true;
                            break;
                        }
                    }
                }
            }
            
            // Use our custom dialog style with multi-choice items
            AlertDialog.Builder builder = new AlertDialog.Builder(requireContext(), R.style.PlaylistDialogStyle)
                .setTitle(R.string.add_to_playlist)
                .setMultiChoiceItems(playlistNames, checkedItems, (dialog, which, isChecked) -> {
                    // This will be handled in the positive button click
                })
                .setPositiveButton(R.string.save, (dialog, which) -> {
                    // Add the song to all selected playlists
                    for (int i = 0; i < checkedItems.length; i++) {
                        if (checkedItems[i]) {
                            addSongToPlaylist(playlistIds[i], song);
                        } else {
                            // If it was unchecked, remove from this playlist
                            removeSongFromPlaylist(playlistIds[i], song);
                        }
                    }
                    dialog.dismiss();
                    Toast.makeText(requireContext(), "Playlists updated", Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton(R.string.cancel, (dialog, which) -> dialog.dismiss());
            
            // Store the dialog and show it
            currentPlaylistDialog = builder.create();
            currentPlaylistDialog.show();
        });
    }

    private void removeSongFromPlaylist(long playlistId, Song song) {
        if (song == null || song.getId() == null) {
            return;
        }
        
        AppDatabase db = AppDatabase.getInstance(requireContext());
        Executor executor = Executors.newSingleThreadExecutor();
        
        executor.execute(() -> {
            try {
                // Remove from playlist with additional null check
                String songId = song.getId();
                if (songId != null && !songId.isEmpty()) {
                    db.playlistDao().removeSongFromPlaylist(playlistId, songId);
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        });
    }

    private void addSongToPlaylist(long playlistId, Song song) {
        if (song == null || song.getId() == null) {
            Toast.makeText(requireContext(), "Error: Invalid song data", Toast.LENGTH_SHORT).show();
            return;
        }
        
        AppDatabase db = AppDatabase.getInstance(requireContext());
        Executor executor = Executors.newSingleThreadExecutor();
        
        executor.execute(() -> {
            try {
                // Make sure song exists in database with a valid ID
                db.songDao().insert(song);
                
                // Add to playlist with additional null check
                String songId = song.getId();
                if (songId != null && !songId.isEmpty()) {
                    PlaylistSongCrossRef crossRef = new PlaylistSongCrossRef(playlistId, songId);
                    db.playlistDao().insert(crossRef);
                    
                    // Show toast on UI thread
                    new Handler(Looper.getMainLooper()).post(() -> 
                        Toast.makeText(requireContext(), R.string.song_added_to_playlist, Toast.LENGTH_SHORT).show()
                    );
                } else {
                    new Handler(Looper.getMainLooper()).post(() -> 
                        Toast.makeText(requireContext(), "Error: Song ID is missing", Toast.LENGTH_SHORT).show()
                    );
                }
            } catch (Exception e) {
                e.printStackTrace();
                new Handler(Looper.getMainLooper()).post(() -> 
                    Toast.makeText(requireContext(), 
                        "Error adding to playlist: " + e.getMessage(), 
                        Toast.LENGTH_SHORT).show()
                );
            }
        });
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        // Dismiss any open dialog when the fragment is destroyed
        if (currentPlaylistDialog != null && currentPlaylistDialog.isShowing()) {
            currentPlaylistDialog.dismiss();
        }
        binding = null;
    }
} 