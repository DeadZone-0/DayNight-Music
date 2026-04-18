package com.midnight.music.ui.adapters;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.engine.DiskCacheStrategy;
import com.bumptech.glide.load.resource.drawable.DrawableTransitionOptions;
import com.midnight.music.R;
import com.midnight.music.data.model.PlaylistWithSongs;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class PlaylistTileAdapter extends RecyclerView.Adapter<PlaylistTileAdapter.PlaylistViewHolder> {
    
    private List<PlaylistWithSongs> playlists;
    private final OnPlaylistClickListener listener;
    
    public interface OnPlaylistClickListener {
        void onPlaylistClick(PlaylistWithSongs playlist);
    }
    
    public PlaylistTileAdapter(List<PlaylistWithSongs> playlists, OnPlaylistClickListener listener) {
        this.playlists = new ArrayList<>(playlists);
        this.listener = listener;
    }
    
    public void updateData(List<PlaylistWithSongs> newPlaylists) {
        List<PlaylistWithSongs> oldPlaylists = this.playlists;
        this.playlists = new ArrayList<>(newPlaylists);
        DiffUtil.DiffResult result = DiffUtil.calculateDiff(new DiffUtil.Callback() {
            @Override public int getOldListSize() { return oldPlaylists.size(); }
            @Override public int getNewListSize() { return newPlaylists.size(); }
            @Override public boolean areItemsTheSame(int oldPos, int newPos) {
                return oldPlaylists.get(oldPos).playlist.getId() == newPlaylists.get(newPos).playlist.getId();
            }
            @Override public boolean areContentsTheSame(int oldPos, int newPos) {
                PlaylistWithSongs o = oldPlaylists.get(oldPos), n = newPlaylists.get(newPos);
                boolean sameName = String.valueOf(o.playlist.getName()).equals(String.valueOf(n.playlist.getName()));
                boolean sameSize = o.songs.size() == n.songs.size();
                boolean sameCover;
                if (o.songs.isEmpty() && n.songs.isEmpty()) {
                    sameCover = true;
                } else {
                    String oUrl = o.songs.isEmpty() ? null : o.songs.get(0).getImageUrl();
                    String nUrl = n.songs.isEmpty() ? null : n.songs.get(0).getImageUrl();
                    sameCover = Objects.equals(oUrl, nUrl);
                }
                return o.playlist.getId() == n.playlist.getId() && sameName && sameSize && sameCover;
            }
        });
        result.dispatchUpdatesTo(this);
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
            
            // Load playlist image with optimized caching
            if (playlistWithSongs.songs != null && !playlistWithSongs.songs.isEmpty()) {
                // Use the first song's image as playlist cover
                String imageUrl = playlistWithSongs.songs.get(0).getImageUrl();
                if (imageUrl != null && !imageUrl.isEmpty()) {
                    Glide.with(itemView.getContext())
                            .load(imageUrl)
                            .diskCacheStrategy(DiskCacheStrategy.ALL)
                            .transition(DrawableTransitionOptions.withCrossFade(200))
                            .thumbnail(0.25f)
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
 
