package com.quick.wildjack.wildjack;

import lombok.RequiredArgsConstructor;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Controller;

@Controller
@RequiredArgsConstructor
public class WebSocketController {

    private final GameService gameService;
    private final SimpMessagingTemplate messagingTemplate;

    /**
     * Игрок делает ход через WebSocket
     * Клиент шлёт MoveMessage на /app/move
     */
    @MessageMapping("/move")
    public void handleMove(MoveMessage message) {
        try {
            String action = message.getAction();
            if (action == null || action.isBlank()) {
                action = "MOVE";
            }

            Game game;
            if ("EXCHANGE".equalsIgnoreCase(action)) {
                game = gameService.exchangeDeadCard(
                        message.getGameId(),
                        message.getPlayerId(),
                        message.getCard()
                );
            } else if ("SKIP".equalsIgnoreCase(action)) {
                game = gameService.skipTurnIfStuck(
                        message.getGameId(),
                        message.getPlayerId()
                );
            } else if ("MOVE".equalsIgnoreCase(action)) {
                game = gameService.makeMove(
                        message.getGameId(),
                        message.getPlayerId(),
                        message.getCard(),
                        message.getX(),
                        message.getY()
                );
            } else {
                throw new RuntimeException("Unknown action: " + action);
            }

            // Отправляем обновление доски всем игрокам игры
            messagingTemplate.convertAndSend("/topic/game/" + game.getId(), game);

        } catch (RuntimeException e) {
            // В случае ошибки можно отправить её игроку
            messagingTemplate.convertAndSend("/topic/game/" + message.getPlayerId() + "/error", e.getMessage());
        }
    }
}
