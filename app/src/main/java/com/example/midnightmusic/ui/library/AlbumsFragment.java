package com.example.midnightmusic.ui.library;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.RecyclerView;

import com.example.midnightmusic.R;
import com.example.midnightmusic.models.Album;
import com.example.midnightmusic.ui.adapters.AlbumCardAdapter;

import java.util.ArrayList;
import java.util.List;

public class AlbumsFragment extends Fragment implements AlbumCardAdapter.OnAlbumClickListener {
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
        List<Album> albums = getAlbums();
        AlbumCardAdapter adapter = new AlbumCardAdapter(albums, this);
        recyclerView.setAdapter(adapter);
    }

    private List<Album> getAlbums() {
        List<Album> albums = new ArrayList<>();
        
        // Add sample albums
        albums.add(new Album("1", "Random Access Memories", "Daft Punk", null, "album"));
        albums.add(new Album("2", "Discovery", "Daft Punk", null, "album"));
        albums.add(new Album("3", "Homework", "Daft Punk", null, "album"));
        albums.add(new Album("4", "Human After All", "Daft Punk", null, "album"));
        albums.add(new Album("5", "TRON: Legacy", "Daft Punk", null, "album"));
        albums.add(new Album("6", "Alive 2007", "Daft Punk", null, "album"));
        
        return albums;
    }

    @Override
    public void onAlbumClick(Album album) {
        // Handle album click
        Toast.makeText(requireContext(), 
                      "Selected album: " + album.getTitle(), 
                      Toast.LENGTH_SHORT).show();
    }
} 