package com.midnight.music.ui.discord;

import android.app.Dialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.view.Window;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.bumptech.glide.Glide;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputEditText;
import com.midnight.music.R;
import com.midnight.music.data.discord.DiscordPresenceData;
import com.midnight.music.data.discord.DiscordRpcManager;
import com.midnight.music.data.discord.DiscordUserProfile;
import com.midnight.music.databinding.ActivityDiscordRpcBinding;

import java.util.Locale;

public class DiscordRpcActivity extends AppCompatActivity {
    private ActivityDiscordRpcBinding binding;
    private DiscordRpcManager rpcManager;
    private final Handler progressTicker = new Handler(Looper.getMainLooper());
    private Runnable tickerRunnable;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityDiscordRpcBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        rpcManager = DiscordRpcManager.getInstance(this);

        setupToolbar();
        setupObservables();
        setupActions();
        startProgressTicker();
    }

    private void setupToolbar() {
        binding.toolbar.setNavigationOnClickListener(v -> finish());
    }

    private void setupObservables() {
        // User Profile
        rpcManager.getUserProfile().observe(this, this::updateProfileView);

        // Connection Status
        rpcManager.getConnectionStatus().observe(this, this::updateStatusPill);

        // Current Presence
        rpcManager.getCurrentPresence().observe(this, this::updatePresencePreview);

        // Switches & Settings
        rpcManager.getIsEnabled().observe(this, enabled -> {
            if (binding.switchEnableRpc.isChecked() != enabled) {
                binding.switchEnableRpc.setChecked(enabled);
            }
        });

        rpcManager.getShowTitle().observe(this, show -> {
            if (binding.switchShowTitle.isChecked() != show) {
                binding.switchShowTitle.setChecked(show);
            }
        });

        rpcManager.getShowArtist().observe(this, show -> {
            if (binding.switchShowArtist.isChecked() != show) {
                binding.switchShowArtist.setChecked(show);
            }
        });

        rpcManager.getShowTime().observe(this, show -> {
            if (binding.switchShowTime.isChecked() != show) {
                binding.switchShowTime.setChecked(show);
            }
        });

        rpcManager.getActivityType().observe(this, type -> {
            int targetId = type == 2 ? R.id.radio_listening : R.id.radio_playing;
            if (binding.radioGroupActivityType.getCheckedRadioButtonId() != targetId) {
                binding.radioGroupActivityType.check(targetId);
            }
            binding.previewHeader.setText(type == 2 ? "LISTENING TO MIDNIGHT MUSIC" : "PLAYING MIDNIGHT MUSIC");
        });
    }

    private void setupActions() {
        binding.switchEnableRpc.setOnCheckedChangeListener((btn, checked) -> {
            if (btn.isPressed()) {
                rpcManager.setEnabled(checked);
            }
        });

        binding.switchShowTitle.setOnCheckedChangeListener((btn, checked) -> {
            if (btn.isPressed()) {
                rpcManager.setShowTitle(checked);
            }
        });

        binding.switchShowArtist.setOnCheckedChangeListener((btn, checked) -> {
            if (btn.isPressed()) {
                rpcManager.setShowArtist(checked);
            }
        });

        binding.switchShowTime.setOnCheckedChangeListener((btn, checked) -> {
            if (btn.isPressed()) {
                rpcManager.setShowTime(checked);
            }
        });

        binding.radioGroupActivityType.setOnCheckedChangeListener((group, checkedId) -> {
            int type = checkedId == R.id.radio_listening ? 2 : 0;
            rpcManager.setActivityType(type);
        });

        binding.btnConnectDiscord.setOnClickListener(v -> startActivity(new Intent(this, DiscordLoginActivity.class)));
        binding.btnLogoutDiscord.setOnClickListener(v -> confirmLogout());
    }

    private void updateProfileView(@Nullable DiscordUserProfile profile) {
        if (profile != null) {
            binding.discordUsername.setText(profile.getUsername());
            String handle = profile.getGlobalName() != null && !profile.getGlobalName().isEmpty()
                    ? "@" + profile.getGlobalName()
                    : (profile.getDiscriminator() != null && !profile.getDiscriminator().equals("0") ? "#" + profile.getDiscriminator() : "");
            binding.discordGlobalName.setText(handle);
            binding.btnConnectDiscord.setText("Change Token");
            binding.btnLogoutDiscord.setVisibility(View.VISIBLE);

            if (profile.getAvatarUrl() != null && !profile.getAvatarUrl().isEmpty()) {
                Glide.with(this)
                        .load(profile.getAvatarUrl())
                        .circleCrop()
                        .placeholder(R.drawable.ic_discord)
                        .into(binding.discordAvatar);
            } else {
                binding.discordAvatar.setImageResource(R.drawable.ic_discord);
            }
        } else {
            binding.discordUsername.setText("Not Connected");
            binding.discordGlobalName.setText("Connect token to broadcast music");
            binding.btnConnectDiscord.setText("Connect Account");
            binding.btnLogoutDiscord.setVisibility(View.GONE);
            binding.discordAvatar.setImageResource(R.drawable.ic_discord);
        }
    }

    private void updateStatusPill(String status) {
        if (status == null) status = "Disconnected";
        switch (status) {
            case "Ready":
            case "Connected":
                binding.discordStatusPill.setText("ONLINE");
                binding.discordStatusPill.setBackgroundTintList(android.content.res.ColorStateList.valueOf(0xFF23A55A));
                binding.discordStatusPill.setTextColor(Color.WHITE);
                break;
            case "Connecting":
                binding.discordStatusPill.setText("CONNECTING");
                binding.discordStatusPill.setBackgroundTintList(android.content.res.ColorStateList.valueOf(0xFFFEE75C));
                binding.discordStatusPill.setTextColor(0xFF000000);
                break;
            case "Failed":
            case "Error":
                binding.discordStatusPill.setText("ERROR");
                binding.discordStatusPill.setBackgroundTintList(android.content.res.ColorStateList.valueOf(0xFFED4245));
                binding.discordStatusPill.setTextColor(Color.WHITE);
                break;
            case "Disconnected":
            default:
                binding.discordStatusPill.setText("OFFLINE");
                binding.discordStatusPill.setBackgroundTintList(android.content.res.ColorStateList.valueOf(0xFF4E5058));
                binding.discordStatusPill.setTextColor(0xFFDBDEE1);
                break;
        }
    }

    private void updatePresencePreview(@Nullable DiscordPresenceData presence) {
        if (presence != null && presence.isPlaying()) {
            boolean showTitle = Boolean.TRUE.equals(rpcManager.getShowTitle().getValue());
            boolean showArtist = Boolean.TRUE.equals(rpcManager.getShowArtist().getValue());
            boolean showTime = Boolean.TRUE.equals(rpcManager.getShowTime().getValue());

            binding.previewSongTitle.setText(showTitle && presence.getTitle() != null ? presence.getTitle() : "Midnight Music");
            binding.previewSongArtist.setText(showArtist && presence.getArtist() != null ? presence.getArtist() : "");
            binding.previewSongAlbum.setText(presence.getAlbum() != null && !presence.getAlbum().isEmpty() ? presence.getAlbum() : "Midnight Music");

            if (presence.getImageUrl() != null && !presence.getImageUrl().isEmpty()) {
                Glide.with(this)
                        .load(presence.getImageUrl())
                        .placeholder(R.drawable.placeholder_album)
                        .into(binding.previewImage);
            } else {
                binding.previewImage.setImageResource(R.drawable.placeholder_album);
            }

            binding.previewActionButton.setText(presence.getButtonLabel() != null ? presence.getButtonLabel() : "Listen on Midnight");

            if (showTime && presence.getEndTimestamp() > presence.getStartTimestamp()) {
                binding.previewTimeContainer.setVisibility(View.VISIBLE);
                updatePreviewProgressBar(presence);
            } else {
                binding.previewTimeContainer.setVisibility(View.GONE);
            }
        } else {
            binding.previewSongTitle.setText("No Song Playing");
            binding.previewSongArtist.setText("Play music to test RPC");
            binding.previewSongAlbum.setText("Midnight Music");
            binding.previewImage.setImageResource(R.drawable.placeholder_album);
            binding.previewTimeContainer.setVisibility(View.GONE);
            binding.previewActionButton.setText("Listen on Midnight");
        }
    }

    private void updatePreviewProgressBar(DiscordPresenceData presence) {
        long now = System.currentTimeMillis();
        long total = presence.getEndTimestamp() - presence.getStartTimestamp();
        if (total <= 0) return;

        long elapsed = Math.max(0, now - presence.getStartTimestamp());
        int progress = (int) Math.min(100, Math.max(0, (elapsed * 100) / total));
        long remaining = Math.max(0, presence.getEndTimestamp() - now);

        binding.previewProgressBar.setProgress(progress);
        binding.previewTimeElapsed.setText(formatDuration(elapsed) + " elapsed");
        binding.previewTimeRemaining.setText(formatDuration(remaining) + " left");
    }

    private void startProgressTicker() {
        tickerRunnable = new Runnable() {
            @Override
            public void run() {
                DiscordPresenceData presence = rpcManager.getCurrentPresence().getValue();
                if (presence != null && presence.isPlaying() && Boolean.TRUE.equals(rpcManager.getShowTime().getValue())) {
                    if (presence.getEndTimestamp() > presence.getStartTimestamp()) {
                        updatePreviewProgressBar(presence);
                    }
                }
                progressTicker.postDelayed(this, 1000);
            }
        };
        progressTicker.postDelayed(tickerRunnable, 1000);
    }

    private String formatDuration(long ms) {
        long totalSeconds = ms / 1000;
        long minutes = totalSeconds / 60;
        long seconds = totalSeconds % 60;
        return String.format(Locale.getDefault(), "%d:%02d", minutes, seconds);
    }

    private void showConnectTokenDialog() {
        Dialog dialog = new Dialog(this);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        dialog.setContentView(R.layout.dialog_discord_token);

        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        }

        TextInputEditText editToken = dialog.findViewById(R.id.edit_discord_token);
        MaterialButton btnPaste = dialog.findViewById(R.id.btn_paste_token);
        MaterialButton btnCancel = dialog.findViewById(R.id.btn_cancel);
        MaterialButton btnConnect = dialog.findViewById(R.id.btn_confirm_connect);
        ProgressBar progressBar = dialog.findViewById(R.id.progress_verifying);
        TextView tvError = dialog.findViewById(R.id.tv_verify_error);

        // Pre-fill existing token if available
        String currentToken = rpcManager.getToken().getValue();
        if (currentToken != null) {
            editToken.setText(currentToken);
        }

        // Paste from clipboard handler
        ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        btnPaste.setOnClickListener(v -> {
            if (clipboard != null && clipboard.hasPrimaryClip()) {
                ClipData clip = clipboard.getPrimaryClip();
                if (clip != null && clip.getItemCount() > 0) {
                    CharSequence text = clip.getItemAt(0).getText();
                    if (text != null) {
                        editToken.setText(text.toString().trim());
                        editToken.setSelection(editToken.getText().length());
                    }
                }
            }
        });

        btnCancel.setOnClickListener(v -> dialog.dismiss());

        btnConnect.setOnClickListener(v -> {
            String token = editToken.getText() != null ? editToken.getText().toString().trim() : "";
            if (token.isEmpty()) {
                tvError.setText("Token cannot be empty");
                tvError.setVisibility(View.VISIBLE);
                return;
            }

            progressBar.setVisibility(View.VISIBLE);
            tvError.setVisibility(View.GONE);
            btnConnect.setEnabled(false);
            btnCancel.setEnabled(false);

            rpcManager.verifyAndSaveToken(token, new DiscordRpcManager.VerifyCallback() {
                @Override
                public void onSuccess(DiscordUserProfile profile) {
                    runOnUiThread(() -> {
                        progressBar.setVisibility(View.GONE);
                        dialog.dismiss();
                        Toast.makeText(DiscordRpcActivity.this, "Connected as " + profile.getUsername(), Toast.LENGTH_SHORT).show();
                    });
                }

                @Override
                public void onError(String message) {
                    runOnUiThread(() -> {
                        progressBar.setVisibility(View.GONE);
                        btnConnect.setEnabled(true);
                        btnCancel.setEnabled(true);
                        tvError.setText(message);
                        tvError.setVisibility(View.VISIBLE);
                    });
                }
            });
        });

        dialog.show();
    }

    private void confirmLogout() {
        new AlertDialog.Builder(this)
                .setTitle("Disconnect Discord")
                .setMessage("Are you sure you want to log out of Discord? This will stop broadcasting your music presence.")
                .setPositiveButton("Disconnect", (dialog, which) -> {
                    rpcManager.logout();
                    Toast.makeText(this, "Disconnected from Discord", Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (tickerRunnable != null) {
            progressTicker.removeCallbacks(tickerRunnable);
        }
    }
}
