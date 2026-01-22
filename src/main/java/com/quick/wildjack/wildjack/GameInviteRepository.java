package com.quick.wildjack.wildjack;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface GameInviteRepository extends JpaRepository<GameInvite, Long> {
    List<GameInvite> findByToTelegramId(Long toTelegramId);

    List<GameInvite> findByFromTelegramId(Long fromTelegramId);

    void deleteByStatusAndCreatedAtBefore(GameInviteStatus status, java.time.Instant createdAt);
}
