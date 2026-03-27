package com.midnight.music.ui.search;

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

import com.midnight.music.R;
import com.midnight.music.databinding.FragmentSearchBinding;
import com.midnight.music.data.model.Song;
import com.midnight.music.data.network.JioSaavnService;
import com.google.android.material.textfield.TextInputEditText;
import com.midnight.music.player.MusicPlayerManager;
import com.midnight.music.ui.player.PlayerActivity;
import com.midnight.music.data.db.AppDatabase;
import com.midnight.music.data.model.PlaylistSongCrossRef;
import com.midnight.music.data.model.Playlist;
import com.midnight.music.data.network.SongResponse;
import com.midnight.music.data.model.PlaylistWithSongs;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

public class SearchFragment extends Fragment {
    private FragmentSearchBinding binding;
    private SearchAdapter searchAdapter;
    private JioSaavnService api;
    private RecyclerView.LayoutManager searchLayoutManager;

    // This will track the currently displayed playlist dialog
    private AlertDialog currentPlaylistDialog = null;

    // Search stream properties
    private final Handler searchHandler = new Handler(Looper.getMainLooper());
    private Runnable searchRunnable;
    private Call<List<SongResponse>> currentSearchCall;

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
        showInitialState();
    }

    private void setupLayoutManagers() {
        searchLayoutManager = new LinearLayoutManager(requireContext());
    }

    private void setupSearchInput() {
        android.widget.EditText searchInput = binding.searchInput;
        searchInput.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_SEARCH ||
                (event != null && event.getKeyCode() == KeyEvent.KEYCODE_ENTER)) {
                String query = searchInput.getText().toString().trim();
                if (query.isEmpty()) {
                    showInitialState();
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
                String query = s.toString().trim();
                
                // Cancel any pending search executions
                if (searchRunnable != null) {
                    searchHandler.removeCallbacks(searchRunnable);
                }

                if (query.isEmpty()) {
                    // If empty, cancel any ongoing API call and show initial state immediately
                    if (currentSearchCall != null && !currentSearchCall.isCanceled()) {
                        currentSearchCall.cancel();
                    }
                    showInitialState();
                } else {
                    // Show a subtle loading state while typing
                    binding.progressBar.setVisibility(View.VISIBLE);
                    binding.emptyStateContainer.setVisibility(View.GONE);
                    
                    // Schedule a new search after user stops typing for 500ms
                    searchRunnable = () -> performSearch(query);
                    searchHandler.postDelayed(searchRunnable, 500);
                }
            }
        });
    }

    private void setupRecyclerView() {
        searchAdapter = new SearchAdapter(new SearchAdapter.SearchAdapterListener() {
            @Override
            public void onSongClick(Song song) {
                MusicPlayerManager player = MusicPlayerManager.getInstance(requireContext());
                player.setAutoQueueEnabled(true);
                player.playSong(song);
            }

            @Override
            public void onPlayNow(Song song) {
                MusicPlayerManager player = MusicPlayerManager.getInstance(requireContext());
                player.setAutoQueueEnabled(true);
                player.playSong(song);
            }

            @Override
            public void onAddToPlaylist(Song song) {
                // Show playlist selection dialog
                showPlaylistsDialog(song);
            }

            @Override
            public void onQueueNext(Song song) {
                MusicPlayerManager.getInstance(requireContext()).addToQueue(song);
            }

            @Override
            public void onToggleLike(Song song) {
                if (song == null || song.getId() == null) {
                    Toast.makeText(requireContext(), "Error: Invalid song data", Toast.LENGTH_SHORT).show();
                    return;
                }

                com.midnight.music.data.repository.MusicRepository.getInstance(requireContext()).toggleLikeSong(song, new com.midnight.music.data.repository.MusicRepository.LikeCallback() {
                    @Override
                    public void onComplete(boolean isLiked) {
                        new Handler(Looper.getMainLooper()).post(() -> {
                            String message = isLiked ? "Added to Liked Songs" : "Removed from Liked Songs";
                            Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show();
                            searchAdapter.notifyDataSetChanged();
                        });
                    }

                    @Override
                    public void onError(Exception e) {
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
            showInitialState();
            return;
        }

        // Cancel the previous active search if one is running
        if (currentSearchCall != null && !currentSearchCall.isCanceled()) {
            currentSearchCall.cancel();
        }

        showLoadingState();
        
        currentSearchCall = api.searchSongs(query.trim(), true);
        currentSearchCall.enqueue(new Callback<List<SongResponse>>() {
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
                
                // Don't show an error if we intentionally canceled the call
                if (!call.isCanceled()) {
                    showError("Network error");
                }
            }
        });
    }

    private void showLoadingState() {
        binding.progressBar.setVisibility(View.VISIBLE);
        binding.searchResultsRecycler.setVisibility(View.GONE);
        binding.emptyStateContainer.setVisibility(View.GONE);
    }

    private void showSearchResults(List<Song> songs) {
        binding.searchResultsRecycler.setLayoutManager(searchLayoutManager);
        binding.searchResultsRecycler.setAdapter(searchAdapter);
        
        // Check if songs are liked in database before displaying
        AppDatabase db = AppDatabase.getInstance(requireContext());
        Executor executor = Executors.newSingleThreadExecutor();
        
        // First show the results with default state (not liked)
        binding.progressBar.setVisibility(View.GONE);
        binding.emptyStateContainer.setVisibility(View.GONE);
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
        binding.emptyStateContainer.setVisibility(View.VISIBLE);
        binding.emptyState.setText("No results found");
        binding.emptyStateSubtitle.setText("Try searching for something else.");
        binding.emptyStateIcon.setImageResource(R.drawable.ic_search); // Subtle search icon
    }

    private void showError(String message) {
        binding.progressBar.setVisibility(View.GONE);
        binding.searchResultsRecycler.setVisibility(View.GONE);
        binding.emptyStateContainer.setVisibility(View.VISIBLE);
        
        if (message.equals("Network error")) {
            binding.emptyState.setText("No internet connection");
            binding.emptyStateSubtitle.setText("Please check your network and try again.");
            binding.emptyStateIcon.setImageResource(R.drawable.ic_search); // Fallback icon
        } else {
            binding.emptyState.setText("Error");
            binding.emptyStateSubtitle.setText(message);
            binding.emptyStateIcon.setImageResource(R.drawable.ic_search);
        }
    }

    private void showInitialState() {
        binding.progressBar.setVisibility(View.GONE);
        binding.searchResultsRecycler.setVisibility(View.GONE);
        binding.emptyStateContainer.setVisibility(View.VISIBLE);
        binding.emptyState.setText("Find your next favorite song");
        binding.emptyStateSubtitle.setText("Search for songs, artists, or full albums to explore new music.");
        binding.emptyStateIcon.setImageResource(R.drawable.ic_search);
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
        
        // Use MusicRepository which correctly resets isLiked when removing from Liked Songs
        com.midnight.music.data.repository.MusicRepository.getInstance(requireContext())
                .removeSongFromPlaylist(playlistId, song.getId(), null);
    }

    private void addSongToPlaylist(long playlistId, Song song) {
        if (song == null || song.getId() == null) {
            Toast.makeText(requireContext(), "Error: Invalid song data", Toast.LENGTH_SHORT).show();
            return;
        }
        
        // Use MusicRepository which preserves isLiked/isDownloaded flags
        com.midnight.music.data.repository.MusicRepository.getInstance(requireContext())
                .addSongToPlaylist(playlistId, song, () -> {
                    // Item added, no toast needed for quiet aesthetic
                });
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        // Dismiss any open dialog when the fragment is destroyed
        if (currentPlaylistDialog != null && currentPlaylistDialog.isShowing()) {
            currentPlaylistDialog.dismiss();
        }
        
        // Clean up search resources
        if (searchRunnable != null) {
            searchHandler.removeCallbacks(searchRunnable);
        }
        if (currentSearchCall != null && !currentSearchCall.isCanceled()) {
            currentSearchCall.cancel();
        }
        
        binding = null;
    }
} 
