package com.midnight.music.ui.library;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.RecyclerView;

import com.midnight.music.R;
import com.midnight.music.models.Album;
import com.midnight.music.ui.adapters.AlbumCardAdapter;

import java.util.ArrayList;
import java.util.List;

public class ArtistsFragment extends Fragment implements AlbumCardAdapter.OnAlbumClickListener {
    private RecyclerView recyclerView;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                           @Nullable ViewGroup container,
                           @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_library_list, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        recyclerView = view.findViewById(R.id.recycler_view);
        setupRecyclerView();
    }

    private void setupRecyclerView() {
        List<Album> artists = getArtists();
        AlbumCardAdapter adapter = new AlbumCardAdapter(artists, this);
        recyclerView.setAdapter(adapter);
    }

    private List<Album> getArtists() {
        List<Album> artists = new ArrayList<>();
        
        // Add sample artists
        artists.add(new Album("1", "Daft Punk", "Electronic â€¢ French duo", null, "artist"));
        artists.add(new Album("2", "The Weeknd", "R&B â€¢ Pop", null, "artist"));
        artists.add(new Album("3", "Tame Impala", "Psychedelic Rock", null, "artist"));
        artists.add(new Album("4", "Arctic Monkeys", "Rock â€¢ Indie", null, "artist"));
        artists.add(new Album("5", "Kendrick Lamar", "Hip-Hop â€¢ Rap", null, "artist"));
        artists.add(new Album("6", "Taylor Swift", "Pop â€¢ Country", null, "artist"));
        
        return artists;
    }

    @Override
    public void onAlbumClick(Album album) {
        // Handle artist click
        Toast.makeText(requireContext(), 
                      "Selected artist: " + album.getTitle(), 
                      Toast.LENGTH_SHORT).show();
    }
} 
