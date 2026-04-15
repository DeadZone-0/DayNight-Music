package com.midnight.music.ui.search;

import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.PopupMenu;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;

import com.midnight.music.R;
import com.midnight.music.data.model.Artist;
import com.midnight.music.data.model.SearchItem;
import com.midnight.music.data.model.Song;
import com.midnight.music.utils.ImageLoader;
import com.google.android.material.imageview.ShapeableImageView;

public class SearchResultsAdapter extends ListAdapter<SearchItem, RecyclerView.ViewHolder> {

    private static final int VIEW_TYPE_SONG = 0;
    private static final int VIEW_TYPE_ARTIST = 1;
    private static final int VIEW_TYPE_SECTION_HEADER = 2;

    private final SearchAdapterListener listener;

    public interface SearchAdapterListener {
        void onSongClick(Song song);
        void onPlayNow(Song song);
        void onAddToPlaylist(Song song);
        void onQueueNext(Song song);
        void onToggleLike(Song song);
        void onArtistClick(Artist artist);
    }

    public SearchResultsAdapter(SearchAdapterListener listener) {
        super(new DiffUtil.ItemCallback<SearchItem>() {
            @Override
            public boolean areItemsTheSame(@NonNull SearchItem oldItem, @NonNull SearchItem newItem) {
                if (oldItem.getType() != newItem.getType()) return false;
                switch (oldItem.getType()) {
                    case SONG:
                        return oldItem.getSong().getId().equals(newItem.getSong().getId());
                    case ARTIST:
                        return oldItem.getArtist().getId().equals(newItem.getArtist().getId());
                    case SECTION_HEADER:
                        return oldItem.getSectionTitle().equals(newItem.getSectionTitle());
                }
                return false;
            }

            @Override
            public boolean areContentsTheSame(@NonNull SearchItem oldItem, @NonNull SearchItem newItem) {
                switch (oldItem.getType()) {
                    case SONG:
                        Song oldSong = oldItem.getSong();
                        Song newSong = newItem.getSong();
                        return oldSong.getSong().equals(newSong.getSong())
                                && oldSong.getSingers().equals(newSong.getSingers())
                                && oldSong.getImageUrl().equals(newSong.getImageUrl())
                                && oldSong.isLiked() == newSong.isLiked();
                    case ARTIST:
                        return oldItem.getArtist().equals(newItem.getArtist());
                    case SECTION_HEADER:
                        return true;
                }
                return false;
            }
        });
        this.listener = listener;
    }

    @Override
    public int getItemViewType(int position) {
        switch (getItem(position).getType()) {
            case SONG:
                return VIEW_TYPE_SONG;
            case ARTIST:
                return VIEW_TYPE_ARTIST;
            case SECTION_HEADER:
                return VIEW_TYPE_SECTION_HEADER;
        }
        return VIEW_TYPE_SONG;
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        LayoutInflater inflater = LayoutInflater.from(parent.getContext());
        switch (viewType) {
            case VIEW_TYPE_ARTIST:
                View artistView = inflater.inflate(R.layout.item_search_artist, parent, false);
                return new ArtistViewHolder(artistView);
            case VIEW_TYPE_SECTION_HEADER:
                View headerView = inflater.inflate(R.layout.item_search_section_header, parent, false);
                return new SectionHeaderViewHolder(headerView);
            case VIEW_TYPE_SONG:
            default:
                View songView = inflater.inflate(R.layout.item_search_result, parent, false);
                return new SongViewHolder(songView);
        }
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        SearchItem item = getItem(position);
        switch (item.getType()) {
            case ARTIST:
                ((ArtistViewHolder) holder).bind(item.getArtist());
                break;
            case SECTION_HEADER:
                ((SectionHeaderViewHolder) holder).bind(item.getSectionTitle());
                break;
            case SONG:
            default:
                ((SongViewHolder) holder).bind(item.getSong());
                break;
        }
    }

    class SongViewHolder extends RecyclerView.ViewHolder {
        private final TextView trackNumber;
        private final ShapeableImageView songImage;
        private final TextView songName;
        private final TextView songInfo;
        private final ImageButton moreOptions;

        SongViewHolder(@NonNull View itemView) {
            super(itemView);
            trackNumber = itemView.findViewById(R.id.track_number);
            songImage = itemView.findViewById(R.id.song_image);
            songName = itemView.findViewById(R.id.song_name);
            songInfo = itemView.findViewById(R.id.song_info);
            moreOptions = itemView.findViewById(R.id.more_options);

            itemView.setOnClickListener(v -> {
                int pos = getAdapterPosition();
                if (pos != RecyclerView.NO_POSITION && listener != null) {
                    listener.onSongClick(getItem(pos).getSong());
                }
            });

            moreOptions.setOnClickListener(this::showPopupMenu);
        }

        void bind(Song song) {
            trackNumber.setVisibility(View.GONE);
            songName.setText(song.getSong());
            songName.setTextColor(itemView.getContext().getColor(R.color.text_primary));
            
            String info = song.getSingers();
            if (song.getAlbum() != null && !song.getAlbum().isEmpty()) {
                info += " \u2022 " + song.getAlbum();
            }
            info += " \u2022 " + formatDuration(song.getDuration());
            songInfo.setText(info);
            songInfo.setTextColor(itemView.getContext().getColor(R.color.text_secondary));

            String imageUrl = song.getImageUrl();
            if (imageUrl != null && !imageUrl.isEmpty()) {
                ImageLoader.loadImage(songImage, imageUrl);
            } else {
                songImage.setImageResource(R.drawable.placeholder_song);
            }
        }

        private void showPopupMenu(View view) {
            int position = getAdapterPosition();
            if (position == RecyclerView.NO_POSITION || listener == null) return;
            
            SearchItem item = getItem(position);
            if (item.getType() != SearchItem.Type.SONG) return;
            
            PopupMenu popup = new PopupMenu(view.getContext(), view);
            popup.inflate(R.menu.menu_song_options);
            
            Song song = item.getSong();
            MenuItem likeItem = popup.getMenu().findItem(R.id.action_like);
            updateLikeMenuItem(likeItem, song.isLiked());
            
            popup.setOnMenuItemClickListener(item1 -> {
                int itemId = item1.getItemId();
                if (itemId == R.id.action_play_now) {
                    listener.onPlayNow(song);
                    return true;
                } else if (itemId == R.id.action_add_to_playlist) {
                    listener.onAddToPlaylist(song);
                    return true;
                } else if (itemId == R.id.action_queue_next) {
                    listener.onQueueNext(song);
                    return true;
                } else if (itemId == R.id.action_like) {
                    listener.onToggleLike(song);
                    return true;
                }
                return false;
            });
            
            popup.show();
        }
        
        private void updateLikeMenuItem(MenuItem item, boolean isLiked) {
            if (isLiked) {
                item.setTitle(R.string.unlike);
                item.setIcon(R.drawable.ic_heart_filled);
            } else {
                item.setTitle(R.string.like);
                item.setIcon(R.drawable.ic_heart);
            }
        }

        private String formatDuration(String durationStr) {
            try {
                int duration = Integer.parseInt(durationStr);
                int minutes = duration / 60;
                int seconds = duration % 60;
                return String.format("%d:%02d", minutes, seconds);
            } catch (NumberFormatException e) {
                return durationStr;
            }
        }
    }

    class ArtistViewHolder extends RecyclerView.ViewHolder {
        private final ShapeableImageView artistImage;
        private final TextView artistName;
        private final TextView artistInfo;
        private final View verifiedBadge;

        ArtistViewHolder(@NonNull View itemView) {
            super(itemView);
            artistImage = itemView.findViewById(R.id.artist_image);
            artistName = itemView.findViewById(R.id.artist_name);
            artistInfo = itemView.findViewById(R.id.artist_info);
            verifiedBadge = itemView.findViewById(R.id.verified_badge);

            itemView.setOnClickListener(v -> {
                int pos = getAdapterPosition();
                if (pos != RecyclerView.NO_POSITION && listener != null) {
                    listener.onArtistClick(getItem(pos).getArtist());
                }
            });
        }

        void bind(Artist artist) {
            artistName.setText(artist.getName());
            
            int followers = artist.getFollowerCount();
            String followerText;
            if (followers >= 1_000_000) {
                followerText = String.format("%.1fM followers", followers / 1_000_000.0);
            } else if (followers >= 1_000) {
                followerText = String.format("%.1fK followers", followers / 1_000.0);
            } else {
                followerText = "Artist";
            }
            artistInfo.setText(followerText);

            if (artist.isVerified()) {
                verifiedBadge.setVisibility(View.VISIBLE);
            } else {
                verifiedBadge.setVisibility(View.GONE);
            }

            String imageUrl = artist.getImageUrl();
            if (imageUrl != null && !imageUrl.isEmpty()) {
                ImageLoader.loadImage(artistImage, imageUrl);
            } else {
                artistImage.setImageResource(R.drawable.ic_artist_placeholder);
            }
        }
    }

    class SectionHeaderViewHolder extends RecyclerView.ViewHolder {
        private final TextView sectionTitle;

        SectionHeaderViewHolder(@NonNull View itemView) {
            super(itemView);
            sectionTitle = itemView.findViewById(R.id.section_title);
        }

        void bind(String title) {
            sectionTitle.setText(title);
        }
    }
}