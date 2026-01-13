package com.quick.wildjack.wildjack;

import lombok.Data;

import java.util.List;
import java.util.Map;

@Data
public class Game {
    private String id;
    private List<Player> players;
    private Cell[][] board;         // 10x10
    private List<Card> deck;        // оставшиеся карты
    private int currentPlayerIndex; // чей ход

    // NEW:
    private GameStatus status;          // WAITING / STARTED / FINISHED
    private int maxPlayers;             // 2..4
    private long turnDeadlineEpochMs;   // дедлайн хода (ms)
    private boolean isTeamGame;         // оставил как было
    private GameResult result;          // WIN / DRAW
    private String winnerKey;           // playerId or team key
    private Map<String, Integer> sequencesByKey;
    private int teamCount;
    private int sequencesToWin;
    private boolean exchangeUsedThisTurn;
}
