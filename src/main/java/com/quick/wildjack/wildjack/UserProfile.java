package com.quick.wildjack.wildjack;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Data;

import java.time.Instant;

@Entity
@Table(name = "users")
@Data
public class UserProfile {
    @Id
    @Column(name = "telegram_id", nullable = false)
    private Long telegramId;

    private String username;

    @Column(name = "display_name")
    private String displayName;

    @Column(name = "first_name")
    private String firstName;

    @Column(name = "last_name")
    private String lastName;

    @Column(name = "avatar_url")
    private String avatarUrl;

    @Column(name = "language_code")
    private String languageCode;

    @Column(name = "start_param")
    private String startParam;

    @Column(name = "created_at")
    private Instant createdAt;

    @Column(name = "updated_at")
    private Instant updatedAt;

    @Column(name = "last_login_at")
    private Instant lastLoginAt;

    @Column(name = "rating")
    private Integer rating;

    @Column(name = "games_played")
    private Integer gamesPlayed;

    @Column(name = "wins")
    private Integer wins;

    @Column(name = "losses")
    private Integer losses;
}
