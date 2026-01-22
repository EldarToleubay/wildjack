package com.quick.wildjack.wildjack;

import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Service
public class FriendService {

    private final FriendRequestRepository friendRequestRepository;
    private final FriendshipRepository friendshipRepository;
    private final UserProfileRepository userProfileRepository;
    private final SimpMessagingTemplate messagingTemplate;

    public FriendService(FriendRequestRepository friendRequestRepository,
                         FriendshipRepository friendshipRepository,
                         UserProfileRepository userProfileRepository,
                         SimpMessagingTemplate messagingTemplate) {
        this.friendRequestRepository = friendRequestRepository;
        this.friendshipRepository = friendshipRepository;
        this.userProfileRepository = userProfileRepository;
        this.messagingTemplate = messagingTemplate;
    }

    public FriendRequest sendRequest(Long fromId, String displayName) {
        UserProfile targetProfile = userProfileRepository.findByDisplayNameIgnoreCase(displayName)
                .orElseThrow(() -> new RuntimeException("User not found"));
        UserProfile fromProfile = userProfileRepository.findById(fromId)
                .orElseThrow(() -> new RuntimeException("User not found"));
        Long toId = targetProfile.getTelegramId();
        if (fromId.equals(toId)) {
            throw new RuntimeException("Cannot friend yourself");
        }
        ensureUserExists(fromId);

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
        request.setFromDisplayName(fromProfile.getDisplayName());
        request.setFromAvatarUrl(fromProfile.getAvatarUrl());
        request.setToTelegramId(toId);
        request.setToDisplayName(targetProfile.getDisplayName());
        request.setToAvatarUrl(targetProfile.getAvatarUrl());
        request.setStatus(FriendRequestStatus.PENDING);
        request.setCreatedAt(Instant.now());
        FriendRequest saved = friendRequestRepository.save(request);
        UserEventPayload payload = buildFriendRequestEvent("friend_request_created", saved,
                fromProfile.getDisplayName(), fromProfile.getAvatarUrl());
        sendUserEvent(fromId, payload);
        sendUserEvent(toId, payload);
        return saved;
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
        friendRequestRepository.delete(request);
        UserEventPayload payload = buildFriendRequestEvent("friend_request_accepted", request,
                request.getToDisplayName(), request.getToAvatarUrl());
        sendUserEvent(request.getFromTelegramId(), payload);
        sendUserEvent(request.getToTelegramId(), payload);
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
        friendRequestRepository.save(request);
        friendRequestRepository.delete(request);
        UserEventPayload payload = buildFriendRequestEvent("friend_request_rejected", request,
                request.getToDisplayName(), request.getToAvatarUrl());
        sendUserEvent(request.getFromTelegramId(), payload);
        sendUserEvent(request.getToTelegramId(), payload);
        return request;
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
                    UserProfile friendProfile = userProfileRepository.findById(friendId)
                            .orElseThrow(() -> new RuntimeException("User not found"));
                    Friendship friendship = new Friendship();
                    friendship.setUserTelegramId(userId);
                    friendship.setFriendTelegramId(friendId);
                    friendship.setFriendDisplayName(friendProfile.getDisplayName());
                    friendship.setFriendAvatarUrl(friendProfile.getAvatarUrl());
                    friendship.setCreatedAt(Instant.now());
                    return friendshipRepository.save(friendship);
                });
    }

    private void ensureUserExists(Long telegramId) {
        userProfileRepository.findById(telegramId)
                .orElseThrow(() -> new RuntimeException("User not found"));
    }

    private void sendUserEvent(Long telegramId, UserEventPayload payload) {
        messagingTemplate.convertAndSend("/topic/user/" + telegramId + "/events", payload);
    }

    private UserEventPayload buildFriendRequestEvent(String type, FriendRequest request, String actorName, String actorAvatar) {
        UserEventPayload payload = new UserEventPayload();
        payload.setType(type);
        payload.setRequestId(request.getId());
        payload.setFromTelegramId(request.getFromTelegramId());
        payload.setToTelegramId(request.getToTelegramId());
        payload.setDisplayName(actorName);
        payload.setAvatarUrl(actorAvatar);
        payload.setFromDisplayName(request.getFromDisplayName());
        payload.setFromAvatarUrl(request.getFromAvatarUrl());
        payload.setToDisplayName(request.getToDisplayName());
        payload.setToAvatarUrl(request.getToAvatarUrl());
        return payload;
    }
}
