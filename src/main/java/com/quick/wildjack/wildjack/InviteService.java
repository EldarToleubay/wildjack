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
        ensureUserExists(fromId);
        ensureUserExists(toId);
        if (gameId == null || gameId.isBlank()) {
            throw new RuntimeException("Game ID is required");
        }
        GameInvite invite = new GameInvite();
        invite.setFromTelegramId(fromId);
        invite.setToTelegramId(toId);
        invite.setGameId(gameId);
        invite.setStatus(GameInviteStatus.PENDING);
        invite.setCreatedAt(Instant.now());
        return gameInviteRepository.save(invite);
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

        UserProfile profile = userProfileRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found"));
        Game game = gameService.joinGame(invite.getGameId(), profile.getDisplayName());

        String playerId = game.getPlayers().stream()
                .filter(player -> player.getName().equalsIgnoreCase(profile.getDisplayName()))
                .findFirst()
                .map(Player::getId)
                .orElse(null);

        messagingTemplate.convertAndSend("/topic/game/" + game.getId(), game);

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
        invite.setStatus(GameInviteStatus.REJECTED);
        invite.setRespondedAt(Instant.now());
        return gameInviteRepository.save(invite);
    }

    public List<GameInvite> listIncoming(Long userId) {
        return gameInviteRepository.findByToTelegramId(userId);
    }

    public List<GameInvite> listOutgoing(Long userId) {
        return gameInviteRepository.findByFromTelegramId(userId);
    }

    private void ensureUserExists(Long telegramId) {
        userProfileRepository.findById(telegramId)
                .orElseThrow(() -> new RuntimeException("User not found"));
    }
}
