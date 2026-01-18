package com.quick.wildjack.wildjack;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/games")
@RequiredArgsConstructor
@CrossOrigin("*")
public class GameController {

    private final GameService gameService;
    private final SimpMessagingTemplate messagingTemplate;


    @PostMapping("/create")
    public ResponseEntity<Game> createGame(@RequestBody List<String> playerNames) {
        Game game = gameService.createGame(playerNames);

        // ✅ сразу отправим состояние в топик, чтобы создатель мог подписаться и получать апдейты
        messagingTemplate.convertAndSend("/topic/game/" + game.getId(), game);

        return ResponseEntity.ok(game);
    }

    @PostMapping("/{gameId}/join")
    public ResponseEntity<JoinGameResponse> joinGame(@PathVariable String gameId,
                                                     @RequestParam String playerName) {
        Game game = gameService.joinGame(gameId, playerName);

        String playerId = game.getPlayers().stream()
                .filter(p -> p.getName().equalsIgnoreCase(playerName))
                .findFirst()
                .orElseThrow()
                .getId();

        // ✅ вот это ключевое: после join рассылаем актуальный game всем
        messagingTemplate.convertAndSend("/topic/game/" + game.getId(), game);

        return ResponseEntity.ok(new JoinGameResponse(game, playerId));
    }

    @PostMapping("/{gameId}/rejoin")
    public ResponseEntity<JoinGameResponse> rejoinGame(@PathVariable String gameId,
                                                       @RequestBody RejoinRequest request) {
        return ResponseEntity.ok(gameService.rejoinGame(gameId, request.getSessionToken()));
    }


    @PostMapping("/{gameId}/move")
    public ResponseEntity<Game> makeMove(
            @PathVariable String gameId,
            @RequestParam String playerId,
            @RequestBody MoveRequest moveRequest
    ) {
        return ResponseEntity.ok(
                gameService.makeMove(
                        gameId,
                        playerId,
                        moveRequest.getCard(),
                        moveRequest.getCardIndex(),
                        moveRequest.getX(),
                        moveRequest.getY()
                )
        );
    }

}
