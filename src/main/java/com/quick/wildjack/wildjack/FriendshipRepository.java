package com.quick.wildjack.wildjack;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface FriendshipRepository extends JpaRepository<Friendship, Long> {
    List<Friendship> findByUserTelegramId(Long userTelegramId);

    Optional<Friendship> findByUserTelegramIdAndFriendTelegramId(Long userTelegramId, Long friendTelegramId);
}
