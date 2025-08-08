package com.example.midnightmusic.ui.adapters;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.example.midnightmusic.R;
import com.example.midnightmusic.data.model.PlaylistWithSongs;

import java.util.List;

public class PlaylistTileAdapter extends RecyclerView.Adapter<PlaylistTileAdapter.PlaylistViewHolder> {
    
    private List<PlaylistWithSongs> playlists;
    private final OnPlaylistClickListener listener;
    
    public interface OnPlaylistClickListener {
        void onPlaylistClick(PlaylistWithSongs playlist);
    }
    
    public PlaylistTileAdapter(List<PlaylistWithSongs> playlists, OnPlaylistClickListener listener) {
        this.playlists = playlists;
        this.listener = listener;
    }
    
    public void updateData(List<PlaylistWithSongs> newPlaylists) {
        this.playlists = newPlaylists;
        notifyDataSetChanged();
    }
    
    @NonNull
    @Override
    public PlaylistViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_playlist_tile, parent, false);
        return new PlaylistViewHolder(view);
    }
    
    @Override
    public void onBindViewHolder(@NonNull PlaylistViewHolder holder, int position) {
        PlaylistWithSongs playlistWithSongs = playlists.get(position);
        holder.bind(playlistWithSongs);
    }
    
    @Override
    public int getItemCount() {
        return playlists.size();
    }
    
    class PlaylistViewHolder extends RecyclerView.ViewHolder {
        private final ImageView playlistImage;
        private final TextView playlistName;
        private final TextView playlistDetails;
        
        public PlaylistViewHolder(@NonNull View itemView) {
            super(itemView);
            playlistImage = itemView.findViewById(R.id.playlist_image);
            playlistName = itemView.findViewById(R.id.playlist_name);
            playlistDetails = itemView.findViewById(R.id.playlist_details);
            
            itemView.setOnClickListener(v -> {
                int position = getAdapterPosition();
                if (position != RecyclerView.NO_POSITION) {
                    listener.onPlaylistClick(playlists.get(position));
                }
            });
        }
        
        public void bind(PlaylistWithSongs playlistWithSongs) {
            playlistName.setText(playlistWithSongs.playlist.getName());
            
            // Set playlist details (number of songs)
            String songCount = playlistWithSongs.songs.size() + " songs";
            playlistDetails.setText(songCount);
            
            // Load playlist image
            if (playlistWithSongs.songs != null && !playlistWithSongs.songs.isEmpty()) {
                // Use the first song's image as playlist cover
                String imageUrl = playlistWithSongs.songs.get(0).getImageUrl();
                if (imageUrl != null && !imageUrl.isEmpty()) {
                    Glide.with(itemView.getContext())
                            .load(imageUrl)
                            .placeholder(R.drawable.placeholder_art)
                            .into(playlistImage);
                } else {
                    playlistImage.setImageResource(R.drawable.placeholder_art);
                }
            } else {
                playlistImage.setImageResource(R.drawable.placeholder_art);
            }
        }
    }
} 