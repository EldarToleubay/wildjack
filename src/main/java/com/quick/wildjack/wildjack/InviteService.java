package com.quick.wildjack.wildjack;

import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;

@Service
public class InviteService {

    private final GameInviteRepository gameInviteRepository;
    private final UserProfileRepository userProfileRepository;
    private final GameService gameService;
    private final SimpMessagingTemplate messagingTemplate;

    public InviteService(GameInviteRepository gameInviteRepository,
                         UserProfileRepository userProfileRepository,
                         GameService gameService,
                         SimpMessagingTemplate messagingTemplate) {
        this.gameInviteRepository = gameInviteRepository;
        this.userProfileRepository = userProfileRepository;
        this.gameService = gameService;
        this.messagingTemplate = messagingTemplate;
    }

    public GameInvite sendInvite(Long fromId, Long toId, String gameId) {
        if (fromId.equals(toId)) {
            throw new RuntimeException("Cannot invite yourself");
        }
        UserProfile fromProfile = ensureUserExists(fromId);
        UserProfile toProfile = ensureUserExists(toId);
        if (gameId == null || gameId.isBlank()) {
            throw new RuntimeException("Game ID is required");
        }
        GameInvite invite = new GameInvite();
        invite.setFromTelegramId(fromId);
        invite.setToTelegramId(toId);
        invite.setGameId(gameId);
        invite.setStatus(GameInviteStatus.PENDING);
        invite.setCreatedAt(Instant.now());
        GameInvite saved = gameInviteRepository.save(invite);
        UserEventPayload payload = buildInviteEvent("game_invite_created", saved, fromProfile, fromProfile, toProfile);
        sendUserEvent(fromId, payload);
        sendUserEvent(toId, payload);
        return saved;
    }

    public InviteAcceptResponse acceptInvite(Long inviteId, Long userId) {
        GameInvite invite = gameInviteRepository.findById(inviteId)
                .orElseThrow(() -> new RuntimeException("Invite not found"));
        if (!invite.getToTelegramId().equals(userId)) {
            throw new RuntimeException("Not your invite");
        }
        invite.setStatus(GameInviteStatus.ACCEPTED);
        invite.setRespondedAt(Instant.now());
        gameInviteRepository.save(invite);

        UserProfile profile = ensureUserExists(userId);
        UserProfile fromProfile = ensureUserExists(invite.getFromTelegramId());
        Game game = gameService.joinGame(invite.getGameId(), profile.getDisplayName());

        String playerId = game.getPlayers().stream()
                .filter(player -> player.getName().equalsIgnoreCase(profile.getDisplayName()))
                .findFirst()
                .map(Player::getId)
                .orElse(null);

        messagingTemplate.convertAndSend("/topic/game/" + game.getId(), game);

        UserEventPayload payload = buildInviteEvent("game_invite_accepted", invite, profile, fromProfile, profile);
        payload.setGameId(game.getId());
        payload.setPlayerId(playerId);
        sendUserEvent(invite.getFromTelegramId(), payload);
        sendUserEvent(invite.getToTelegramId(), payload);

        InviteAcceptResponse response = new InviteAcceptResponse();
        response.setInvite(invite);
        response.setGame(game);
        response.setPlayerId(playerId);
        return response;
    }

    public GameInvite rejectInvite(Long inviteId, Long userId) {
        GameInvite invite = gameInviteRepository.findById(inviteId)
                .orElseThrow(() -> new RuntimeException("Invite not found"));
        if (!invite.getToTelegramId().equals(userId)) {
            throw new RuntimeException("Not your invite");
        }
        UserProfile fromProfile = ensureUserExists(invite.getFromTelegramId());
        UserProfile toProfile = ensureUserExists(invite.getToTelegramId());
        invite.setStatus(GameInviteStatus.REJECTED);
        invite.setRespondedAt(Instant.now());
        GameInvite saved = gameInviteRepository.save(invite);
        UserEventPayload payload = buildInviteEvent("game_invite_rejected", saved, toProfile, fromProfile, toProfile);
        sendUserEvent(invite.getFromTelegramId(), payload);
        sendUserEvent(invite.getToTelegramId(), payload);
        return saved;
    }

    public List<GameInvite> listIncoming(Long userId) {
        return gameInviteRepository.findByToTelegramId(userId);
    }

    public List<GameInvite> listOutgoing(Long userId) {
        return gameInviteRepository.findByFromTelegramId(userId);
    }

    private UserProfile ensureUserExists(Long telegramId) {
        return userProfileRepository.findById(telegramId)
                .orElseThrow(() -> new RuntimeException("User not found"));
    }

    private void sendUserEvent(Long telegramId, UserEventPayload payload) {
        messagingTemplate.convertAndSend("/topic/user/" + telegramId + "/events", payload);
    }

    private UserEventPayload buildInviteEvent(String type,
                                              GameInvite invite,
                                              UserProfile actorProfile,
                                              UserProfile fromProfile,
                                              UserProfile toProfile) {
        UserEventPayload payload = new UserEventPayload();
        payload.setType(type);
        payload.setInviteId(invite.getId());
        payload.setFromTelegramId(invite.getFromTelegramId());
        payload.setToTelegramId(invite.getToTelegramId());
        payload.setDisplayName(actorProfile != null ? actorProfile.getDisplayName() : null);
        payload.setAvatarUrl(actorProfile != null ? actorProfile.getAvatarUrl() : null);
        payload.setFromDisplayName(fromProfile != null ? fromProfile.getDisplayName() : null);
        payload.setFromAvatarUrl(fromProfile != null ? fromProfile.getAvatarUrl() : null);
        payload.setToDisplayName(toProfile != null ? toProfile.getDisplayName() : null);
        payload.setToAvatarUrl(toProfile != null ? toProfile.getAvatarUrl() : null);
        return payload;
    }
}
