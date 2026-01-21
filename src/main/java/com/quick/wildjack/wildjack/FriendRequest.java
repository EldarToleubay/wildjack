package com.quick.wildjack.wildjack;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Data;

import java.time.Instant;

@Entity
@Table(name = "friend_requests")
@Data
public class FriendRequest {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "from_telegram_id", nullable = false)
    private Long fromTelegramId;

    @Column(name = "from_display_name")
    private String fromDisplayName;

    @Column(name = "from_avatar_url")
    private String fromAvatarUrl;

    @Column(name = "to_telegram_id", nullable = false)
    private Long toTelegramId;

    @Column(name = "to_display_name")
    private String toDisplayName;

    @Column(name = "to_avatar_url")
    private String toAvatarUrl;

    @Enumerated(EnumType.STRING)
    private FriendRequestStatus status;

    @Column(name = "created_at")
    private Instant createdAt;

    @Column(name = "responded_at")
    private Instant respondedAt;
}
