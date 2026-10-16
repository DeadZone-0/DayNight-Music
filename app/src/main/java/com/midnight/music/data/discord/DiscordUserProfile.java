package com.midnight.music.data.discord;

/**
 * Cached Discord User Profile information.
 */
public class DiscordUserProfile {
    private final String id;
    private final String username;
    private final String globalName;
    private final String discriminator;
    private final String avatarUrl;

    public DiscordUserProfile(String id, String username, String globalName, String discriminator, String avatarUrl) {
        this.id = id;
        this.username = username;
        this.globalName = globalName;
        this.discriminator = discriminator;
        this.avatarUrl = avatarUrl;
    }

    public String getId() { return id; }
    public String getUsername() { return username; }
    public String getGlobalName() { return globalName; }
    public String getDiscriminator() { return discriminator; }
    public String getAvatarUrl() { return avatarUrl; }

    public String getDisplayName() {
        if (globalName != null && !globalName.trim().isEmpty()) {
            return globalName;
        }
        return username;
    }
}
