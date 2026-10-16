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
import com.midnight.music.data.model.Artist;
import com.midnight.music.data.model.SearchItem;
import com.midnight.music.data.model.Song;
import com.midnight.music.data.network.JioSaavnService;
import com.midnight.music.data.network.SaavnApiService;
import com.midnight.music.data.network.SaavnSearchResponse;
import com.midnight.music.data.network.SaavnSongResult;
import com.midnight.music.data.network.SaavnArtistSearchResponse;
import com.midnight.music.data.network.SaavnArtistResult;
import com.midnight.music.data.network.NetworkClient;
import com.google.android.material.textfield.TextInputEditText;
import com.midnight.music.player.MusicPlayerManager;
import com.midnight.music.ui.player.PlayerActivity;
import com.midnight.music.ui.artist.ArtistDetailActivity;
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
    private SearchResultsAdapter searchAdapter;
    private ArtistAdapter artistAdapter;

    public enum SearchTab {
        ALL,
        SONGS,
        ARTISTS
    }
    private SearchTab currentTab = SearchTab.ALL;
    private ArtistAdapter fullArtistAdapter;

    // Audio quality preference
    private SharedPreferences sharedPreferences;

    // Animation duration for smooth transitions
    private static final int CROSSFADE_DURATION = 200;
    
    // API clients
    private SaavnApiService primaryApi;
    private JioSaavnService fallbackApi;
    
    private RecyclerView.LayoutManager searchLayoutManager;

    // Playlist dialog tracker
    private AlertDialog currentPlaylistDialog = null;

    // Search stream properties
    private final Handler searchHandler = new Handler(Looper.getMainLooper());
    private Runnable searchRunnable;
    private Call<?> currentSearchCall;
    private Call<?> currentSongQueryCall;
    private Call<?> currentArtistSearchCall;

    // Pagination state
    private static final int PAGE_LIMIT = 20;
    private int currentPage = 1;
    private boolean isLoadingMore = false;
    private boolean hasMorePages = true;
    private boolean usingFallback = false;
    private String currentQuery = "";
    private final List<Song> allSongs = new ArrayList<>();
    private final List<Artist> allArtists = new ArrayList<>();

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
                .client(NetworkClient.getInstance())
                .addConverterFactory(GsonConverterFactory.create())
                .build();
        primaryApi = primaryRetrofit.create(SaavnApiService.class);

        // Fallback API (old Vercel one)
        Retrofit fallbackRetrofit = new Retrofit.Builder()
                .baseUrl(JioSaavnService.BASE_URL)
                .client(NetworkClient.getInstance())
                .addConverterFactory(GsonConverterFactory.create())
                .build();
        fallbackApi = fallbackRetrofit.create(JioSaavnService.class);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        setupLayoutManagers();
        setupSearchInput();
        setupFilterChips();
        setupRecyclerView();
        
        binding.searchResultsRecycler.setLayoutManager(new LinearLayoutManager(requireContext()));
        binding.searchResultsRecycler.setAdapter(searchAdapter);
        // Apply staggered fade-in animation to search results
        android.view.animation.LayoutAnimationController animation = 
                android.view.animation.AnimationUtils.loadLayoutAnimation(requireContext(), R.anim.layout_anim_stagger);
        binding.searchResultsRecycler.setLayoutAnimation(animation);
        
        binding.artistsRecycler.setLayoutManager(new LinearLayoutManager(requireContext(), LinearLayoutManager.HORIZONTAL, false));
        binding.artistsRecycler.setAdapter(artistAdapter);
        
        // Setup pagination listener (has usingFallback guard)
        setupPaginationListener();
        
        showInitialState();
    }

    private void setupLayoutManagers() {
        searchLayoutManager = new LinearLayoutManager(requireContext());
    }

    private void setupFilterChips() {
        binding.chipAll.setOnClickListener(v -> selectTab(SearchTab.ALL));
        binding.chipSongs.setOnClickListener(v -> selectTab(SearchTab.SONGS));
        binding.chipArtists.setOnClickListener(v -> selectTab(SearchTab.ARTISTS));
    }

    private void selectTab(SearchTab tab) {
        if (currentTab == tab) return;
        currentTab = tab;
        updateTabStyles();
        updateCombinedResults();
    }

    private void updateTabStyles() {
        if (getContext() == null || binding == null) return;
        int activeBg = R.drawable.bg_filter_chip_active;
        int inactiveBg = R.drawable.bg_filter_chip_inactive;
        int activeText = requireContext().getColor(R.color.black);
        int inactiveText = requireContext().getColor(R.color.text_secondary);

        binding.chipAll.setBackgroundResource(currentTab == SearchTab.ALL ? activeBg : inactiveBg);
        binding.chipAll.setTextColor(currentTab == SearchTab.ALL ? activeText : inactiveText);

        binding.chipSongs.setBackgroundResource(currentTab == SearchTab.SONGS ? activeBg : inactiveBg);
        binding.chipSongs.setTextColor(currentTab == SearchTab.SONGS ? activeText : inactiveText);

        binding.chipArtists.setBackgroundResource(currentTab == SearchTab.ARTISTS ? activeBg : inactiveBg);
        binding.chipArtists.setTextColor(currentTab == SearchTab.ARTISTS ? activeText : inactiveText);
    }

    private void setupSearchInput() {
        android.widget.EditText searchInput = binding.searchInput;
        binding.btnClearSearch.setOnClickListener(v -> {
            searchInput.setText("");
            cancelCurrentCall();
            resetPagination();
            crossfadeToInitialState();
        });

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
                binding.btnClearSearch.setVisibility(query.isEmpty() ? View.GONE : View.VISIBLE);
                
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
        fullArtistAdapter = new ArtistAdapter(artist -> ArtistDetailActivity.start(requireContext(), artist));
        binding.artistsFullRecycler.setLayoutManager(new LinearLayoutManager(requireContext()));
        binding.artistsFullRecycler.setAdapter(fullArtistAdapter);

        searchAdapter = new SearchResultsAdapter(new SearchResultsAdapter.SearchAdapterListener() {
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

            @Override
            public void onDownload(Song song) {
                if (song == null || song.getId() == null) {
                    Toast.makeText(requireContext(), "Invalid song", Toast.LENGTH_SHORT).show();
                    return;
                }
                Toast.makeText(requireContext(), "Downloading " + song.getSong() + " in background...", Toast.LENGTH_SHORT).show();

                com.midnight.music.utils.DownloadManager.getInstance(requireContext())
                    .downloadSong(song, new com.midnight.music.utils.DownloadManager.DownloadListener() {
                        @Override
                        public void onProgress(int percent) { }

                        @Override
                        public void onComplete(String filePath) {
                            new Handler(Looper.getMainLooper()).post(() -> {
                                if (isAdded()) {
                                    Toast.makeText(requireContext(), "Downloaded " + song.getSong(), Toast.LENGTH_SHORT).show();
                                }
                            });
                        }

                        @Override
                        public void onError(Exception e) {
                            new Handler(Looper.getMainLooper()).post(() -> {
                                if (isAdded()) {
                                    Toast.makeText(requireContext(), "Failed to download", Toast.LENGTH_SHORT).show();
                                }
                            });
                        }
                    });
            }

            @Override
            public void onArtistClick(Artist artist) {
                ArtistDetailActivity.start(requireContext(), artist);
            }
        });

        artistAdapter = new ArtistAdapter(artist -> ArtistDetailActivity.start(requireContext(), artist));
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
        allArtists.clear();
    }

    /**
     * Cancel the currently running API call, if any.
     */
    private void cancelCurrentCall() {
        if (currentSearchCall != null && !currentSearchCall.isCanceled()) {
            currentSearchCall.cancel();
        }
        if (currentSongQueryCall != null && !currentSongQueryCall.isCanceled()) {
            currentSongQueryCall.cancel();
        }
        if (currentArtistSearchCall != null && !currentArtistSearchCall.isCanceled()) {
            currentArtistSearchCall.cancel();
        }
    }

    /**
     * Primary search entry point. Searches both songs and artists.
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
            // Search artists only on first page
            searchArtists(currentQuery, currentPage);
        }

        // Search songs
        searchWithPrimaryApi(currentQuery, currentPage);
    }

    /**
     * Search using the paginated API.
     * When page == 1, runs parallel queries for both the exact query and the "[query] song" variant
     * to prevent JioSaavn from exclusively returning artist discographies (e.g. boyband "Blue").
     */
    private void searchWithPrimaryApi(String query, int page) {
        android.util.Log.d("SearchFragment", "searchWithPrimaryApi called for query: " + query + " page: " + page);

        if (page == 1) {
            boolean shouldFetchSongVariant = !query.toLowerCase().trim().endsWith("song");
            int totalCalls = shouldFetchSongVariant ? 2 : 1;
            java.util.concurrent.atomic.AtomicInteger pendingCalls = new java.util.concurrent.atomic.AtomicInteger(totalCalls);
            java.util.concurrent.atomic.AtomicBoolean anySuccess = new java.util.concurrent.atomic.AtomicBoolean(false);
            List<Song> collectedSongs = java.util.Collections.synchronizedList(new ArrayList<>());
            String preferredQuality = sharedPreferences.getString(
                    SettingsActivity.AUDIO_QUALITY_KEY, SettingsActivity.DEFAULT_QUALITY);

            // Call 1: Direct query
            Call<SaavnSearchResponse> call1 = primaryApi.searchSongs(query, 1, PAGE_LIMIT);
            currentSearchCall = call1;

            call1.enqueue(new Callback<SaavnSearchResponse>() {
                @Override
                public void onResponse(@NonNull Call<SaavnSearchResponse> call, @NonNull Response<SaavnSearchResponse> response) {
                    if (call.isCanceled() || !isAdded()) return;
                    if (response.isSuccessful() && response.body() != null && response.body().isSuccess()
                            && response.body().getData() != null && response.body().getData().getResults() != null) {
                        anySuccess.set(true);
                        for (SaavnSongResult r : response.body().getData().getResults()) {
                            collectedSongs.add(r.toSong(preferredQuality));
                        }
                    }
                    if (pendingCalls.decrementAndGet() == 0) {
                        new Handler(Looper.getMainLooper()).post(() -> onDualSearchFinished(query, collectedSongs, anySuccess.get()));
                    }
                }

                @Override
                public void onFailure(@NonNull Call<SaavnSearchResponse> call, @NonNull Throwable t) {
                    if (call.isCanceled() || !isAdded()) return;
                    if (pendingCalls.decrementAndGet() == 0) {
                        new Handler(Looper.getMainLooper()).post(() -> onDualSearchFinished(query, collectedSongs, anySuccess.get()));
                    }
                }
            });

            // Call 2: Song query variant (forces JioSaavn to return title-matching tracks)
            if (shouldFetchSongVariant) {
                Call<SaavnSearchResponse> call2 = primaryApi.searchSongs(query.trim() + " song", 1, PAGE_LIMIT);
                currentSongQueryCall = call2;

                call2.enqueue(new Callback<SaavnSearchResponse>() {
                    @Override
                    public void onResponse(@NonNull Call<SaavnSearchResponse> call, @NonNull Response<SaavnSearchResponse> response) {
                        if (call.isCanceled() || !isAdded()) return;
                        if (response.isSuccessful() && response.body() != null && response.body().isSuccess()
                                && response.body().getData() != null && response.body().getData().getResults() != null) {
                            anySuccess.set(true);
                            for (SaavnSongResult r : response.body().getData().getResults()) {
                                collectedSongs.add(r.toSong(preferredQuality));
                            }
                        }
                        if (pendingCalls.decrementAndGet() == 0) {
                            new Handler(Looper.getMainLooper()).post(() -> onDualSearchFinished(query, collectedSongs, anySuccess.get()));
                        }
                    }

                    @Override
                    public void onFailure(@NonNull Call<SaavnSearchResponse> call, @NonNull Throwable t) {
                        if (call.isCanceled() || !isAdded()) return;
                        if (pendingCalls.decrementAndGet() == 0) {
                            new Handler(Looper.getMainLooper()).post(() -> onDualSearchFinished(query, collectedSongs, anySuccess.get()));
                        }
                    }
                });
            }
        } else {
            // Subsequent pages
            Call<SaavnSearchResponse> call = primaryApi.searchSongs(query, page, PAGE_LIMIT);
            currentSearchCall = call;

            call.enqueue(new Callback<SaavnSearchResponse>() {
                @Override
                public void onResponse(@NonNull Call<SaavnSearchResponse> call,
                                       @NonNull Response<SaavnSearchResponse> response) {
                    if (!isAdded() || call.isCanceled()) return;

                    if (response.isSuccessful() && response.body() != null
                            && response.body().isSuccess()
                            && response.body().getData() != null
                            && response.body().getData().getResults() != null) {

                        List<SaavnSongResult> results = response.body().getData().getResults();
                        if (results.size() < PAGE_LIMIT) {
                            hasMorePages = false;
                        }

                        String preferredQuality = sharedPreferences.getString(
                                SettingsActivity.AUDIO_QUALITY_KEY, SettingsActivity.DEFAULT_QUALITY);
                        List<Song> songs = new ArrayList<>();
                        for (SaavnSongResult result : results) {
                            songs.add(result.toSong(preferredQuality));
                        }

                        allSongs.addAll(songs);
                        isLoadingMore = false;
                        showSearchResults(new ArrayList<>(allSongs));
                    } else {
                        isLoadingMore = false;
                        hasMorePages = false;
                    }
                }

                @Override
                public void onFailure(@NonNull Call<SaavnSearchResponse> call, @NonNull Throwable t) {
                    if (!isAdded() || call.isCanceled()) return;
                    isLoadingMore = false;
                    hasMorePages = false;
                }
            });
        }
    }

    private void onDualSearchFinished(String query, List<Song> collectedSongs, boolean anySuccess) {
        if (!isAdded()) return;
        if (anySuccess) {
            allSongs.clear();
            allSongs.addAll(collectedSongs);
            isLoadingMore = false;
            if (collectedSongs.isEmpty()) {
                updateCombinedResults();
            } else {
                showSearchResults(new ArrayList<>(allSongs));
            }
        } else {
            // Both primary calls failed, fallback to fallbackApi
            searchWithFallbackApi(query);
        }
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
     * Search for artists.
     */
    private void searchArtists(String query, int page) {
        Call<SaavnArtistSearchResponse> call = primaryApi.searchArtists(query, page, PAGE_LIMIT);
        currentArtistSearchCall = call;
        
        call.enqueue(new Callback<SaavnArtistSearchResponse>() {
            @Override
            public void onResponse(@NonNull Call<SaavnArtistSearchResponse> call,
                                  @NonNull Response<SaavnArtistSearchResponse> response) {
                if (!isAdded()) return;
                
                if (response.isSuccessful() && response.body() != null
                        && response.body().isSuccess()
                        && response.body().getData() != null
                        && response.body().getData().getResults() != null) {
                    
                    List<SaavnArtistResult> results = response.body().getData().getResults();
                    
                    if (page == 1) {
                        allArtists.clear();
                    }
                    
                    for (SaavnArtistResult result : results) {
                        allArtists.add(result.toArtist());
                    }
                    
                    updateCombinedResults();
                }
            }

            @Override
            public void onFailure(@NonNull Call<SaavnArtistSearchResponse> call, @NonNull Throwable t) {
                // Silently fail - artists are optional
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
        
        // Show loading at bottom
        binding.progressBar.setVisibility(View.VISIBLE);
        binding.progressBar.setPadding(0, 0, 0, 100);
        
        searchWithPrimaryApi(currentQuery, currentPage);
    }

    private void showLoadingState() {
        binding.emptyStateContainer.setVisibility(View.GONE);
        binding.progressBar.setVisibility(View.VISIBLE);
    }

    /**
     * Intelligently scores, re-ranks, and deduplicates songs so that:
     * 1. Songs whose titles directly match the search query are ranked highest.
     * 2. Author/artist matches are secondary.
     * 3. Duplicate songs (same title and primary artist) are collapsed.
     */
    private List<Song> rankAndDeduplicateSongs(List<Song> songs, String query) {
        if (songs == null || songs.isEmpty()) return new java.util.ArrayList<>();

        final String cleanQ = (query != null) ? query.trim().toLowerCase() : "";

        // 1. Deduplicate by normalized (title + primary artist)
        java.util.Map<String, Song> uniqueMap = new java.util.LinkedHashMap<>();
        for (Song s : songs) {
            if (s == null || s.getSong() == null) continue;
            String normTitle = s.getSong().trim().toLowerCase()
                    .replaceAll("\\(.*?\\)", "")
                    .replaceAll("\\[.*?\\]", "")
                    .trim();
            String normArtist = s.getSingers() != null ? s.getSingers().trim().toLowerCase() : "";
            if (normArtist.contains(",")) {
                normArtist = normArtist.substring(0, normArtist.indexOf(",")).trim();
            }
            String key = normTitle + "::" + normArtist;
            if (!uniqueMap.containsKey(key)) {
                uniqueMap.put(key, s);
            }
        }

        List<Song> deduplicated = new java.util.ArrayList<>(uniqueMap.values());
        if (cleanQ.isEmpty()) return deduplicated;

        // 2. Score query relevance
        class ScoredSong {
            final Song song;
            final int score;
            final int index;

            ScoredSong(Song song, int score, int index) {
                this.song = song;
                this.score = score;
                this.index = index;
            }
        }

        List<ScoredSong> scoredList = new java.util.ArrayList<>();
        for (int i = 0; i < deduplicated.size(); i++) {
            Song s = deduplicated.get(i);
            String title = (s.getSong() != null) ? s.getSong().trim().toLowerCase() : "";
            String artists = (s.getSingers() != null) ? s.getSingers().trim().toLowerCase() : "";
            String album = (s.getAlbum() != null) ? s.getAlbum().trim().toLowerCase() : "";

            int score = 0;

            String baseTitle = title.replaceAll("\\(.*?\\)", "").replaceAll("\\[.*?\\]", "").trim();

            // Exact song title match: highest priority (+1000)
            if (title.equalsIgnoreCase(cleanQ)) {
                score += 1000;
            } else if (baseTitle.equalsIgnoreCase(cleanQ)) {
                score += 900;
            } else if (title.startsWith(cleanQ)) {
                score += 500;
            } else if (title.matches(".*\\b" + java.util.regex.Pattern.quote(cleanQ) + "\\b.*")) {
                score += 300;
            } else if (title.contains(cleanQ)) {
                score += 150;
            }

            // Artist match: secondary priority
            if (artists.equalsIgnoreCase(cleanQ)) {
                score += 80;
            } else if (artists.startsWith(cleanQ)) {
                score += 50;
            } else if (artists.contains(cleanQ)) {
                score += 30;
            }

            // Album match: tertiary
            if (album.contains(cleanQ)) {
                score += 10;
            }

            scoredList.add(new ScoredSong(s, score, i));
        }

        // Sort descending by score, tie-breaking by original API order
        java.util.Collections.sort(scoredList, (a, b) -> {
            if (b.score != a.score) {
                return Integer.compare(b.score, a.score);
            }
            return Integer.compare(a.index, b.index);
        });

        List<Song> result = new java.util.ArrayList<>();
        for (ScoredSong ss : scoredList) {
            result.add(ss.song);
        }
        return result;
    }

    private void updateCombinedResults() {
        if (binding == null) return;
        binding.progressBar.setVisibility(View.GONE);
        binding.emptyStateContainer.setVisibility(View.GONE);
        binding.searchResultsRecycler.setAlpha(1f);

        boolean hasSongs = !allSongs.isEmpty();
        boolean hasArtists = !allArtists.isEmpty();

        if (!hasSongs && !hasArtists) {
            showEmptyState();
            return;
        }

        switch (currentTab) {
            case ALL:
                binding.artistsFullRecycler.setVisibility(View.GONE);

                // Show top 3 artists if available
                List<Artist> top3Artists = allArtists.size() > 3 ? allArtists.subList(0, 3) : allArtists;
                if (!top3Artists.isEmpty()) {
                    binding.artistsSection.setVisibility(View.VISIBLE);
                    artistAdapter.submitList(new java.util.ArrayList<>(top3Artists));
                } else {
                    binding.artistsSection.setVisibility(View.GONE);
                }

                if (hasSongs) {
                    binding.songsSection.setVisibility(View.VISIBLE);
                    binding.searchResultsRecycler.setVisibility(View.VISIBLE);
                    List<SearchItem> items = SearchItem.buildSearchResults(new java.util.ArrayList<>(allSongs));
                    searchAdapter.submitList(items);
                } else {
                    binding.songsSection.setVisibility(View.GONE);
                }
                break;

            case SONGS:
                binding.artistsSection.setVisibility(View.GONE);
                binding.artistsFullRecycler.setVisibility(View.GONE);

                if (hasSongs) {
                    binding.songsSection.setVisibility(View.VISIBLE);
                    binding.searchResultsRecycler.setVisibility(View.VISIBLE);
                    List<SearchItem> items = SearchItem.buildSearchResults(new java.util.ArrayList<>(allSongs));
                    searchAdapter.submitList(items);
                } else {
                    binding.songsSection.setVisibility(View.GONE);
                    showEmptyState();
                }
                break;

            case ARTISTS:
                binding.artistsSection.setVisibility(View.GONE);
                binding.songsSection.setVisibility(View.GONE);

                if (hasArtists) {
                    binding.artistsFullRecycler.setVisibility(View.VISIBLE);
                    fullArtistAdapter.submitList(new java.util.ArrayList<>(allArtists));
                } else {
                    binding.artistsFullRecycler.setVisibility(View.GONE);
                    showEmptyState();
                }
                break;
        }
    }

    private void showSearchResults(List<Song> songs) {
        // Rank & deduplicate query results
        List<Song> processed = rankAndDeduplicateSongs(songs, currentQuery);
        this.allSongs.clear();
        this.allSongs.addAll(processed);
        
        // Update tab-aware UI
        updateCombinedResults();
        
        // Also check like status in background
        AppDatabase db = AppDatabase.getInstance(requireContext());
        // ponytail: use shared databaseWriteExecutor
        AppDatabase.databaseWriteExecutor.execute(() -> {
            try {
                for (Song song : songs) {
                    Song existingSong = db.songDao().getSongByIdSync(song.getId());
                    if (existingSong != null) {
                        song.setLiked(existingSong.isLiked());
                    }
                }
                
                new Handler(Looper.getMainLooper()).post(() -> {
                    if (binding != null) {
                        updateCombinedResults();
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
        binding.artistsSection.setVisibility(View.GONE);
        binding.songsSection.setVisibility(View.GONE);
        binding.artistsFullRecycler.setVisibility(View.GONE);
        
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
        binding.artistsSection.setVisibility(View.GONE);
        binding.songsSection.setVisibility(View.GONE);
        binding.artistsFullRecycler.setVisibility(View.GONE);
        
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
        binding.artistsSection.setVisibility(View.GONE);
        binding.songsSection.setVisibility(View.GONE);
        binding.artistsFullRecycler.setVisibility(View.GONE);
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
