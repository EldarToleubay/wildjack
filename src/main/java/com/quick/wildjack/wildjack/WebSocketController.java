package com.quick.wildjack.wildjack;

import lombok.RequiredArgsConstructor;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.CrossOrigin;

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
            // Выполняем ход
            Game game = gameService.makeMove(
                    message.getGameId(),
                    message.getPlayerId(),
                    message.getCard(),
                    message.getX(),
                    message.getY()
            );

            // Отправляем обновление доски всем игрокам игры
            messagingTemplate.convertAndSend("/topic/game/" + game.getId(), game);

        } catch (RuntimeException e) {
            // В случае ошибки можно отправить её игроку
            messagingTemplate.convertAndSend("/topic/game/" + message.getPlayerId() + "/error", e.getMessage());
        }
    }
}
