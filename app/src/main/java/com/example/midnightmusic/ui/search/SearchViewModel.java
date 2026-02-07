package com.example.midnightmusic.ui.search;

import android.app.Application;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.example.midnightmusic.data.model.Song;
import com.example.midnightmusic.data.repository.MusicRepository;

import java.util.List;

/**
 * ViewModel for SearchFragment.
 * Manages search state and results, survives configuration changes.
 */
public class SearchViewModel extends AndroidViewModel {
    private final MusicRepository repository;

    private final MutableLiveData<List<Song>> searchResults = new MutableLiveData<>();
    private final MutableLiveData<Boolean> isLoading = new MutableLiveData<>(false);
    private final MutableLiveData<String> errorMessage = new MutableLiveData<>();
    private final MutableLiveData<SearchState> searchState = new MutableLiveData<>(SearchState.IDLE);

    private String lastQuery = "";

    public enum SearchState {
        IDLE,
        LOADING,
        SUCCESS,
        EMPTY,
        ERROR
    }

    public SearchViewModel(@NonNull Application application) {
        super(application);
        repository = MusicRepository.getInstance(application);
    }

    public LiveData<List<Song>> getSearchResults() {
        return searchResults;
    }

    public LiveData<Boolean> isLoading() {
        return isLoading;
    }

    public LiveData<String> getErrorMessage() {
        return errorMessage;
    }

    public LiveData<SearchState> getSearchState() {
        return searchState;
    }

    public String getLastQuery() {
        return lastQuery;
    }

    public void search(String query) {
        if (query == null || query.trim().isEmpty()) {
            searchState.setValue(SearchState.IDLE);
            return;
        }

        lastQuery = query.trim();
        isLoading.setValue(true);
        searchState.setValue(SearchState.LOADING);

        repository.searchSongs(lastQuery, new MusicRepository.SearchCallback() {
            @Override
            public void onSuccess(List<Song> songs) {
                isLoading.postValue(false);
                searchResults.postValue(songs);

                if (songs == null || songs.isEmpty()) {
                    searchState.postValue(SearchState.EMPTY);
                } else {
                    searchState.postValue(SearchState.SUCCESS);
                }
            }

            @Override
            public void onError(Exception e) {
                isLoading.postValue(false);
                errorMessage.postValue(e.getMessage());
                searchState.postValue(SearchState.ERROR);
            }
        });
    }

    public void toggleLike(Song song) {
        repository.toggleLikeSong(song, new MusicRepository.LikeCallback() {
            @Override
            public void onComplete(boolean isLiked) {
                // The song's like status is updated directly
            }

            @Override
            public void onError(Exception e) {
                errorMessage.postValue("Failed to update like status");
            }
        });
    }

    public void addSongToPlaylist(long playlistId, Song song) {
        repository.addSongToPlaylist(playlistId, song, null);
    }

    public void clearSearch() {
        searchResults.setValue(null);
        searchState.setValue(SearchState.IDLE);
        lastQuery = "";
    }
}
