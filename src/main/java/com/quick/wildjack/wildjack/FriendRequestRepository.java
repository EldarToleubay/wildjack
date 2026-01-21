package com.quick.wildjack.wildjack;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface FriendRequestRepository extends JpaRepository<FriendRequest, Long> {
    List<FriendRequest> findByToTelegramId(Long toTelegramId);

    List<FriendRequest> findByFromTelegramId(Long fromTelegramId);

    Optional<FriendRequest> findByFromTelegramIdAndToTelegramIdAndStatus(Long fromTelegramId,
                                                                          Long toTelegramId,
                                                                          FriendRequestStatus status);
}
