package com.midnight.music.ui.player;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.midnight.music.R;
import com.midnight.music.data.model.Song;

public class QueueAdapter extends ListAdapter<Song, QueueAdapter.QueueViewHolder> {
    private final QueueItemClickListener listener;
    private String currentPlayingSongId;

    public interface QueueItemClickListener {
        void onQueueItemClick(Song song, int position);
    }

    public QueueAdapter(QueueItemClickListener listener) {
        super(new QueueDiffCallback());
        this.listener = listener;
    }

    public void setCurrentPlayingSongId(String songId) {
        if ((songId == null && this.currentPlayingSongId == null) ||
            (songId != null && songId.equals(this.currentPlayingSongId))) {
            return;
        }
        this.currentPlayingSongId = songId;
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public QueueViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_queue_song, parent, false);
        return new QueueViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull QueueViewHolder holder, int position) {
        Song song = getItem(position);
        holder.bind(song, position);
    }

    class QueueViewHolder extends RecyclerView.ViewHolder {
        private final ImageView imgSong;
        private final TextView txtSongName;
        private final TextView txtArtist;
        private final TextView txtDuration;

        public QueueViewHolder(@NonNull View itemView) {
            super(itemView);
            imgSong = itemView.findViewById(R.id.imgSong);
            txtSongName = itemView.findViewById(R.id.txtSongName);
            txtArtist = itemView.findViewById(R.id.txtArtist);
            txtDuration = itemView.findViewById(R.id.txtDuration);

            itemView.setOnClickListener(v -> {
                int position = getAdapterPosition();
                if (position != RecyclerView.NO_POSITION && listener != null) {
                    listener.onQueueItemClick(getItem(position), position);
                }
            });
        }

        public void bind(Song song, int position) {
            if (song == null) return;

            txtSongName.setText(song.getSong());
            txtArtist.setText(song.getSingers());

            // ponytail: visually emphasize current playing track in queue
            boolean isPlaying = song.getId() != null && song.getId().equals(currentPlayingSongId);
            if (isPlaying) {
                int accent = com.midnight.music.utils.ThemeManager.getInstance(itemView.getContext()).getAccentColorValue();
                txtSongName.setTextColor(accent);
                txtArtist.setTextColor(accent);
                itemView.setAlpha(1.0f);
            } else {
                txtSongName.setTextColor(androidx.core.content.ContextCompat.getColor(itemView.getContext(), R.color.text_primary));
                txtArtist.setTextColor(androidx.core.content.ContextCompat.getColor(itemView.getContext(), R.color.text_secondary));
                itemView.setAlpha(0.8f);
            }
            
            // Format duration from seconds to MM:SS
            if (song.getDuration() != null) {
                try {
                    int durationSeconds = Integer.parseInt(song.getDuration());
                    String formattedDuration = formatDuration(durationSeconds);
                    txtDuration.setText(formattedDuration);
                } catch (NumberFormatException e) {
                    txtDuration.setText(song.getDuration());
                }
            } else {
                txtDuration.setText("--:--");
            }

            // Load song image
            Glide.with(imgSong.getContext())
                .load(song.getImageUrl())
                .placeholder(R.drawable.placeholder_song)
                .into(imgSong);
        }
    }

    // Format seconds to MM:SS
    private String formatDuration(int seconds) {
        int minutes = seconds / 60;
        int remainingSeconds = seconds % 60;
        return String.format("%d:%02d", minutes, remainingSeconds);
    }

    private static class QueueDiffCallback extends DiffUtil.ItemCallback<Song> {
        @Override
        public boolean areItemsTheSame(@NonNull Song oldItem, @NonNull Song newItem) {
            return oldItem.getId().equals(newItem.getId());
        }

        @Override
        public boolean areContentsTheSame(@NonNull Song oldItem, @NonNull Song newItem) {
            return oldItem.equals(newItem);
        }
    }
} 
