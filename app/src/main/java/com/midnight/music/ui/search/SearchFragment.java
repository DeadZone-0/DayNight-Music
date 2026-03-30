package com.midnight.music.ui.search;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
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
import com.midnight.music.data.network.SaavnApiService;
import com.midnight.music.data.network.SaavnSearchResponse;
import com.midnight.music.data.network.SaavnSongResult;
import com.google.android.material.textfield.TextInputEditText;
import com.midnight.music.player.MusicPlayerManager;
import com.midnight.music.ui.player.PlayerActivity;
import com.midnight.music.data.db.AppDatabase;
import com.midnight.music.data.model.PlaylistSongCrossRef;
import com.midnight.music.data.model.Playlist;
import com.midnight.music.data.network.SongResponse;
import com.midnight.music.data.model.PlaylistWithSongs;
import com.midnight.music.ui.settings.SettingsActivity;

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

    // Audio quality preference
    private SharedPreferences sharedPreferences;

    // Animation duration for smooth transitions
    private static final int CROSSFADE_DURATION = 200;
    
    // Dual API clients
    private SaavnApiService primaryApi;     // New API (primary, with pagination)
    private JioSaavnService fallbackApi;    // Vercel API (fallback)
    
    private RecyclerView.LayoutManager searchLayoutManager;

    // Playlist dialog tracker
    private AlertDialog currentPlaylistDialog = null;

    // Search stream properties
    private final Handler searchHandler = new Handler(Looper.getMainLooper());
    private Runnable searchRunnable;
    private Call<?> currentSearchCall;

    // Pagination state
    private static final int PAGE_LIMIT = 10;
    private int currentPage = 1;
    private boolean isLoadingMore = false;
    private boolean hasMorePages = true;
    private boolean usingFallback = false;
    private String currentQuery = "";
    private final List<Song> allSongs = new ArrayList<>();

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                           @Nullable ViewGroup container,
                           @Nullable Bundle savedInstanceState) {
        binding = FragmentSearchBinding.inflate(inflater, container, false);
        sharedPreferences = requireContext().getSharedPreferences("DaynightMusicPrefs", Context.MODE_PRIVATE);
        setupRetrofit();
        return binding.getRoot();
    }

    private void setupRetrofit() {
        // Primary API (new one with pagination)
        Retrofit primaryRetrofit = new Retrofit.Builder()
                .baseUrl(SaavnApiService.BASE_URL)
                .addConverterFactory(GsonConverterFactory.create())
                .build();
        primaryApi = primaryRetrofit.create(SaavnApiService.class);

        // Fallback API (old Vercel one)
        Retrofit fallbackRetrofit = new Retrofit.Builder()
                .baseUrl(JioSaavnService.BASE_URL)
                .addConverterFactory(GsonConverterFactory.create())
                .build();
        fallbackApi = fallbackRetrofit.create(JioSaavnService.class);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        setupLayoutManagers();
        setupSearchInput();
        setupRecyclerView();
        setupPaginationListener();
        
        // Attach adapter and layout manager ONCE to preserve scroll position
        binding.searchResultsRecycler.setLayoutManager(searchLayoutManager);
        binding.searchResultsRecycler.setAdapter(searchAdapter);
        
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
                    resetPagination();
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
                    // Cancel any ongoing API call and show initial state
                    cancelCurrentCall();
                    resetPagination();
                    crossfadeToInitialState();
                } else {
                    // Schedule a new search after user stops typing for 400ms
                    // Don't show spinner instantly — feels jarring. Let the debounce handle it.
                    searchRunnable = () -> {
                        resetPagination();
                        performSearch(query);
                    };
                    searchHandler.postDelayed(searchRunnable, 400);
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

    /**
     * Sets up infinite scroll pagination listener on the RecyclerView.
     */
    private void setupPaginationListener() {
        binding.searchResultsRecycler.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrolled(@NonNull RecyclerView recyclerView, int dx, int dy) {
                super.onScrolled(recyclerView, dx, dy);
                
                // Only trigger when scrolling down
                if (dy <= 0) return;
                
                // Skip if already loading, no more pages, or using fallback (no pagination)
                if (isLoadingMore || !hasMorePages || usingFallback) return;
                
                LinearLayoutManager layoutManager = (LinearLayoutManager) recyclerView.getLayoutManager();
                if (layoutManager == null) return;
                
                int visibleItemCount = layoutManager.getChildCount();
                int totalItemCount = layoutManager.getItemCount();
                int firstVisibleItemPosition = layoutManager.findFirstVisibleItemPosition();
                
                // Load more when user has scrolled to within 3 items of the bottom
                if ((visibleItemCount + firstVisibleItemPosition) >= totalItemCount - 3
                        && firstVisibleItemPosition >= 0) {
                    loadNextPage();
                }
            }
        });
    }

    /**
     * Resets all pagination state for a new search query.
     */
    private void resetPagination() {
        currentPage = 1;
        isLoadingMore = false;
        hasMorePages = true;
        usingFallback = false;
        allSongs.clear();
    }

    /**
     * Cancel the currently running API call, if any.
     */
    private void cancelCurrentCall() {
        if (currentSearchCall != null && !currentSearchCall.isCanceled()) {
            currentSearchCall.cancel();
        }
    }

    /**
     * Primary search entry point. Tries the new API first, falls back to Vercel.
     */
    private void performSearch(String query) {
        if (query == null || query.trim().isEmpty()) {
            showInitialState();
            return;
        }

        cancelCurrentCall();
        currentQuery = query.trim();

        if (currentPage == 1) {
            showLoadingState();
        }

        // Try the primary API first
        searchWithPrimaryApi(currentQuery, currentPage);
    }

    /**
     * Search using the new paginated API.
     */
    private void searchWithPrimaryApi(String query, int page) {
        Call<SaavnSearchResponse> call = primaryApi.searchSongs(query, page, PAGE_LIMIT);
        currentSearchCall = call;
        
        call.enqueue(new Callback<SaavnSearchResponse>() {
            @Override
            public void onResponse(@NonNull Call<SaavnSearchResponse> call, 
                                   @NonNull Response<SaavnSearchResponse> response) {
                if (!isAdded()) return;
                
                if (response.isSuccessful() && response.body() != null 
                        && response.body().isSuccess()
                        && response.body().getData() != null
                        && response.body().getData().getResults() != null) {
                    
                    List<SaavnSongResult> results = response.body().getData().getResults();
                    
                    if (results.isEmpty() && page == 1) {
                        showEmptyState();
                        return;
                    }
                    
                    // Convert to Song objects using preferred quality
                    String preferredQuality = sharedPreferences.getString(
                            SettingsActivity.AUDIO_QUALITY_KEY, SettingsActivity.DEFAULT_QUALITY);
                    List<Song> songs = new ArrayList<>();
                    for (SaavnSongResult result : results) {
                        songs.add(result.toSong(preferredQuality));
                    }
                    
                    // If fewer results than the limit, we've reached the end
                    if (results.size() < PAGE_LIMIT) {
                        hasMorePages = false;
                    }
                    
                    if (page == 1) {
                        allSongs.clear();
                    }
                    allSongs.addAll(songs);
                    
                    isLoadingMore = false;
                    showSearchResults(new ArrayList<>(allSongs));
                } else {
                    // Primary API failed or returned bad data - fallback for page 1
                    if (page == 1) {
                        searchWithFallbackApi(query);
                    } else {
                        // For subsequent pages, just mark no more pages
                        isLoadingMore = false;
                        hasMorePages = false;
                    }
                }
            }

            @Override
            public void onFailure(@NonNull Call<SaavnSearchResponse> call, @NonNull Throwable t) {
                if (!isAdded()) return;
                if (call.isCanceled()) return;
                
                // Primary API network failure - fallback for page 1
                if (page == 1) {
                    searchWithFallbackApi(query);
                } else {
                    isLoadingMore = false;
                    hasMorePages = false;
                }
            }
        });
    }

    /**
     * Fallback to the old Vercel API (no pagination support).
     */
    private void searchWithFallbackApi(String query) {
        usingFallback = true;
        hasMorePages = false; // Fallback doesn't support pagination
        
        Call<List<SongResponse>> call = fallbackApi.searchSongs(query, true);
        currentSearchCall = call;
        
        call.enqueue(new Callback<List<SongResponse>>() {
            @Override
            public void onResponse(@NonNull Call<List<SongResponse>> call, 
                                   @NonNull Response<List<SongResponse>> response) {
                if (!isAdded()) return;
                
                if (response.isSuccessful() && response.body() != null) {
                    List<SongResponse> songResponses = response.body();
                    if (songResponses.isEmpty()) {
                        showEmptyState();
                    } else {
                        List<Song> songs = new ArrayList<>();
                        for (SongResponse songResponse : songResponses) {
                            songs.add(songResponse.toSong());
                        }
                        allSongs.clear();
                        allSongs.addAll(songs);
                        showSearchResults(new ArrayList<>(allSongs));
                    }
                } else {
                    showError("Failed to get search results");
                }
            }

            @Override
            public void onFailure(@NonNull Call<List<SongResponse>> call, @NonNull Throwable t) {
                if (!isAdded()) return;
                if (!call.isCanceled()) {
                    showError("Network error");
                }
            }
        });
    }

    /**
     * Loads the next page of results from the primary API.
     */
    private void loadNextPage() {
        if (isLoadingMore || !hasMorePages || usingFallback) return;
        
        isLoadingMore = true;
        currentPage++;
        
        // Show a subtle loading indicator at the bottom (reuse progress bar)
        binding.progressBar.setVisibility(View.VISIBLE);
        
        searchWithPrimaryApi(currentQuery, currentPage);
    }

    private void showLoadingState() {
        // Only hide the list if this is a fresh search (not a pagination load)
        if (!isLoadingMore) {
            binding.searchResultsRecycler.setAlpha(0.5f); // Dim existing results
            binding.emptyStateContainer.setVisibility(View.GONE);
        }
        binding.progressBar.setVisibility(View.VISIBLE);
    }

    private void showSearchResults(List<Song> songs) {
        // Check if songs are liked in database before displaying
        AppDatabase db = AppDatabase.getInstance(requireContext());
        Executor executor = Executors.newSingleThreadExecutor();
        
        // Show results immediately
        binding.progressBar.setVisibility(View.GONE);
        binding.emptyStateContainer.setVisibility(View.GONE);
        binding.searchResultsRecycler.setVisibility(View.VISIBLE);
        
        // Smooth fade-in for fresh results
        if (currentPage <= 1) {
            binding.searchResultsRecycler.setAlpha(0f);
            binding.searchResultsRecycler.animate()
                    .alpha(1f)
                    .setDuration(CROSSFADE_DURATION)
                    .start();
        } else {
            // For pagination, just restore full opacity smoothly
            binding.searchResultsRecycler.animate()
                    .alpha(1f)
                    .setDuration(150)
                    .start();
        }
        
        searchAdapter.submitList(new ArrayList<>(songs));  // Use a copy
        
        // Then update in background with actual like status
        executor.execute(() -> {
            try {
                List<Song> updatedSongs = new ArrayList<>(songs.size());
                
                for (Song song : songs) {
                    Song existingSong = db.songDao().getSongByIdSync(song.getId());
                    if (existingSong != null) {
                        song.setLiked(existingSong.isLiked());
                    }
                    updatedSongs.add(song);
                }
                
                new Handler(Looper.getMainLooper()).post(() -> {
                    if (binding != null) {
                        searchAdapter.submitList(updatedSongs);
                    }
                });
            } catch (Exception e) {
                e.printStackTrace();
            }
        });
    }

    private void showEmptyState() {
        binding.progressBar.setVisibility(View.GONE);
        binding.searchResultsRecycler.setVisibility(View.GONE);
        
        binding.emptyStateContainer.setAlpha(0f);
        binding.emptyStateContainer.setVisibility(View.VISIBLE);
        binding.emptyStateContainer.animate().alpha(1f).setDuration(CROSSFADE_DURATION).start();
        
        binding.emptyState.setText("No results found");
        binding.emptyStateSubtitle.setText("Try searching for something else.");
        binding.emptyStateIcon.setImageResource(R.drawable.ic_search);
    }

    private void showError(String message) {
        binding.progressBar.setVisibility(View.GONE);
        binding.searchResultsRecycler.setVisibility(View.GONE);
        
        binding.emptyStateContainer.setAlpha(0f);
        binding.emptyStateContainer.setVisibility(View.VISIBLE);
        binding.emptyStateContainer.animate().alpha(1f).setDuration(CROSSFADE_DURATION).start();
        
        if (message.equals("Network error")) {
            binding.emptyState.setText("No internet connection");
            binding.emptyStateSubtitle.setText("Please check your network and try again.");
            binding.emptyStateIcon.setImageResource(R.drawable.ic_search);
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
        binding.emptyStateContainer.setAlpha(1f);
        binding.emptyState.setText("Find your next favorite song");
        binding.emptyStateSubtitle.setText("Search for songs, artists, or full albums to explore new music.");
        binding.emptyStateIcon.setImageResource(R.drawable.ic_search);
    }

    /**
     * Smooth crossfade back to the initial state when the user clears the search.
     */
    private void crossfadeToInitialState() {
        binding.progressBar.setVisibility(View.GONE);
        
        // If results are visible, fade them out first
        if (binding.searchResultsRecycler.getVisibility() == View.VISIBLE 
                && binding.searchResultsRecycler.getAlpha() > 0) {
            binding.searchResultsRecycler.animate()
                    .alpha(0f)
                    .setDuration(CROSSFADE_DURATION)
                    .setListener(new AnimatorListenerAdapter() {
                        @Override
                        public void onAnimationEnd(Animator animation) {
                            if (binding == null) return;
                            binding.searchResultsRecycler.setVisibility(View.GONE);
                            showInitialState();
                        }
                    })
                    .start();
        } else {
            binding.searchResultsRecycler.setVisibility(View.GONE);
            showInitialState();
        }
    }

    private void showPlaylistsDialog(Song song) {
        if (currentPlaylistDialog != null && currentPlaylistDialog.isShowing()) {
            currentPlaylistDialog.dismiss();
        }

        AppDatabase db = AppDatabase.getInstance(requireContext());
        
        LiveData<List<PlaylistWithSongs>> playlistsLiveData = db.playlistDao().getAllPlaylistsWithSongs();
        playlistsLiveData.removeObservers(getViewLifecycleOwner());
        
        final boolean[] observerCalled = {false};
        
        playlistsLiveData.observe(getViewLifecycleOwner(), playlists -> {
            if (observerCalled[0]) {
                return;
            }
            observerCalled[0] = true;
            
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
                
                if (playlists.get(i).songs != null) {
                    for (Song playlistSong : playlists.get(i).songs) {
                        if (playlistSong.getId().equals(song.getId())) {
                            checkedItems[i] = true;
                            break;
                        }
                    }
                }
            }
            
            AlertDialog.Builder builder = new AlertDialog.Builder(requireContext(), R.style.PlaylistDialogStyle)
                .setTitle(R.string.add_to_playlist)
                .setMultiChoiceItems(playlistNames, checkedItems, (dialog, which, isChecked) -> {
                })
                .setPositiveButton(R.string.save, (dialog, which) -> {
                    for (int i = 0; i < checkedItems.length; i++) {
                        if (checkedItems[i]) {
                            addSongToPlaylist(playlistIds[i], song);
                        } else {
                            removeSongFromPlaylist(playlistIds[i], song);
                        }
                    }
                    dialog.dismiss();
                })
                .setNegativeButton(R.string.cancel, (dialog, which) -> dialog.dismiss());
            
            currentPlaylistDialog = builder.create();
            currentPlaylistDialog.show();
        });
    }

    private void removeSongFromPlaylist(long playlistId, Song song) {
        if (song == null || song.getId() == null) {
            return;
        }
        
        com.midnight.music.data.repository.MusicRepository.getInstance(requireContext())
                .removeSongFromPlaylist(playlistId, song.getId(), null);
    }

    private void addSongToPlaylist(long playlistId, Song song) {
        if (song == null || song.getId() == null) {
            Toast.makeText(requireContext(), "Error: Invalid song data", Toast.LENGTH_SHORT).show();
            return;
        }
        
        com.midnight.music.data.repository.MusicRepository.getInstance(requireContext())
                .addSongToPlaylist(playlistId, song, () -> {
                    // Item added, no toast needed for quiet aesthetic
                });
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        if (currentPlaylistDialog != null && currentPlaylistDialog.isShowing()) {
            currentPlaylistDialog.dismiss();
        }
        
        // Clean up search resources
        if (searchRunnable != null) {
            searchHandler.removeCallbacks(searchRunnable);
        }
        cancelCurrentCall();
        
        binding = null;
    }
} 
