package com.quick.wildjack.wildjack;

import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Service
public class FriendService {

    private final FriendRequestRepository friendRequestRepository;
    private final FriendshipRepository friendshipRepository;
    private final UserProfileRepository userProfileRepository;

    public FriendService(FriendRequestRepository friendRequestRepository,
                         FriendshipRepository friendshipRepository,
                         UserProfileRepository userProfileRepository) {
        this.friendRequestRepository = friendRequestRepository;
        this.friendshipRepository = friendshipRepository;
        this.userProfileRepository = userProfileRepository;
    }

    public FriendRequest sendRequest(Long fromId, Long toId) {
        if (fromId.equals(toId)) {
            throw new RuntimeException("Cannot friend yourself");
        }
        ensureUserExists(fromId);
        ensureUserExists(toId);

        Optional<Friendship> existingFriendship = friendshipRepository
                .findByUserTelegramIdAndFriendTelegramId(fromId, toId);
        if (existingFriendship.isPresent()) {
            throw new RuntimeException("Already friends");
        }

        Optional<FriendRequest> existingRequest = friendRequestRepository
                .findByFromTelegramIdAndToTelegramIdAndStatus(fromId, toId, FriendRequestStatus.PENDING);
        if (existingRequest.isPresent()) {
            return existingRequest.get();
        }

        FriendRequest request = new FriendRequest();
        request.setFromTelegramId(fromId);
        request.setToTelegramId(toId);
        request.setStatus(FriendRequestStatus.PENDING);
        request.setCreatedAt(Instant.now());
        return friendRequestRepository.save(request);
    }

    public FriendRequest acceptRequest(Long requestId, Long userId) {
        FriendRequest request = friendRequestRepository.findById(requestId)
                .orElseThrow(() -> new RuntimeException("Request not found"));
        if (!request.getToTelegramId().equals(userId)) {
            throw new RuntimeException("Not your request");
        }
        request.setStatus(FriendRequestStatus.ACCEPTED);
        request.setRespondedAt(Instant.now());
        friendRequestRepository.save(request);

        createFriendship(request.getFromTelegramId(), request.getToTelegramId());
        createFriendship(request.getToTelegramId(), request.getFromTelegramId());
        return request;
    }

    public FriendRequest rejectRequest(Long requestId, Long userId) {
        FriendRequest request = friendRequestRepository.findById(requestId)
                .orElseThrow(() -> new RuntimeException("Request not found"));
        if (!request.getToTelegramId().equals(userId)) {
            throw new RuntimeException("Not your request");
        }
        request.setStatus(FriendRequestStatus.REJECTED);
        request.setRespondedAt(Instant.now());
        return friendRequestRepository.save(request);
    }

    public List<Friendship> listFriends(Long userId) {
        return friendshipRepository.findByUserTelegramId(userId);
    }

    public List<FriendRequest> listIncoming(Long userId) {
        return friendRequestRepository.findByToTelegramId(userId);
    }

    public List<FriendRequest> listOutgoing(Long userId) {
        return friendRequestRepository.findByFromTelegramId(userId);
    }

    private void createFriendship(Long userId, Long friendId) {
        friendshipRepository.findByUserTelegramIdAndFriendTelegramId(userId, friendId)
                .orElseGet(() -> {
                    Friendship friendship = new Friendship();
                    friendship.setUserTelegramId(userId);
                    friendship.setFriendTelegramId(friendId);
                    friendship.setCreatedAt(Instant.now());
                    return friendshipRepository.save(friendship);
                });
    }

    private void ensureUserExists(Long telegramId) {
        userProfileRepository.findById(telegramId)
                .orElseThrow(() -> new RuntimeException("User not found"));
    }
}
