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
import com.midnight.music.data.model.Song;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class SongCardAdapter extends RecyclerView.Adapter<SongCardAdapter.SongViewHolder> {
    
    private List<Song> songs;
    private final OnSongClickListener listener;
    
    public interface OnSongClickListener {
        void onSongClick(Song song);
    }
    
    public SongCardAdapter(List<Song> songs, OnSongClickListener listener) {
        this.songs = new ArrayList<>(songs);
        this.listener = listener;
    }
    
    public void updateData(List<Song> newSongs) {
        List<Song> oldSongs = this.songs;
        this.songs = new ArrayList<>(newSongs);
        DiffUtil.DiffResult result = DiffUtil.calculateDiff(new DiffUtil.Callback() {
            @Override public int getOldListSize() { return oldSongs.size(); }
            @Override public int getNewListSize() { return newSongs.size(); }
            @Override public boolean areItemsTheSame(int oldPos, int newPos) {
                return oldSongs.get(oldPos).getId().equals(newSongs.get(newPos).getId());
            }
            @Override public boolean areContentsTheSame(int oldPos, int newPos) {
                Song o = oldSongs.get(oldPos), n = newSongs.get(newPos);
                return Objects.equals(o.getSong(), n.getSong())
                        && Objects.equals(o.getImageUrl(), n.getImageUrl())
                        && Objects.equals(o.getSingers(), n.getSingers());
            }
        });
        result.dispatchUpdatesTo(this);
    }
    
    public Song getSongAt(int position) {
        if (position >= 0 && position < songs.size()) {
            return songs.get(position);
        }
        return null;
    }
    
    @NonNull
    @Override
    public SongViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_song_card, parent, false);
        return new SongViewHolder(view);
    }
    
    @Override
    public void onBindViewHolder(@NonNull SongViewHolder holder, int position) {
        Song song = songs.get(position);
        holder.bind(song);
    }
    
    @Override
    public int getItemCount() {
        return songs.size();
    }
    
    class SongViewHolder extends RecyclerView.ViewHolder {
        private final ImageView songImage;
        private final TextView songTitle;
        private final TextView songArtist;
        private final ImageView btnPlay;
        
        public SongViewHolder(@NonNull View itemView) {
            super(itemView);
            songImage = itemView.findViewById(R.id.song_image);
            songTitle = itemView.findViewById(R.id.song_title);
            songArtist = itemView.findViewById(R.id.song_artist);
            btnPlay = itemView.findViewById(R.id.btn_play);
            
            itemView.setOnClickListener(v -> {
                int position = getAdapterPosition();
                if (position != RecyclerView.NO_POSITION) {
                    listener.onSongClick(songs.get(position));
                }
            });
        }
        
        public void bind(Song song) {
            songTitle.setText(song.getSong());
            songArtist.setText(song.getSingers());
            
            // Load song image with optimized caching and crossfade
            String imageUrl = song.getImageUrl();
            if (imageUrl != null && !imageUrl.isEmpty()) {
                Glide.with(itemView.getContext())
                        .load(imageUrl)
                        .diskCacheStrategy(DiskCacheStrategy.ALL)
                        .transition(DrawableTransitionOptions.withCrossFade(200))
                        .thumbnail(0.25f)
                        .placeholder(R.drawable.placeholder_art)
                        .into(songImage);
            } else {
                songImage.setImageResource(R.drawable.placeholder_art);
            }
        }
    }
} 
