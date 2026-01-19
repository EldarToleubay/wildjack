package com.quick.wildjack.wildjack;

import lombok.RequiredArgsConstructor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class GameTimeoutScheduler {

    private final GameService gameService;
    private final SimpMessagingTemplate messagingTemplate;

    @Scheduled(fixedDelayString = "${wildjack.timeout.check-ms:1000}")
    public void checkTimeouts() {
        for (Game game : gameService.finishExpiredGames()) {
            messagingTemplate.convertAndSend("/topic/game/" + game.getId(), game);
        }
    }
}
