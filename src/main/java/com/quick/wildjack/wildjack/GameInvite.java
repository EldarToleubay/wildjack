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
@Table(name = "game_invites")
@Data
public class GameInvite {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "from_telegram_id", nullable = false)
    private Long fromTelegramId;

    @Column(name = "to_telegram_id", nullable = false)
    private Long toTelegramId;

    @Column(name = "game_id", nullable = false)
    private String gameId;

    @Enumerated(EnumType.STRING)
    private GameInviteStatus status;

    @Column(name = "created_at")
    private Instant createdAt;

    @Column(name = "responded_at")
    private Instant respondedAt;
}
