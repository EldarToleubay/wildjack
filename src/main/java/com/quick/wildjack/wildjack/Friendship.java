package com.quick.wildjack.wildjack;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Data;

import java.time.Instant;

@Entity
@Table(name = "friends")
@Data
public class Friendship {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_telegram_id", nullable = false)
    private Long userTelegramId;

    @Column(name = "friend_telegram_id", nullable = false)
    private Long friendTelegramId;

    @Column(name = "created_at")
    private Instant createdAt;
}
