package com.quick.wildjack.wildjack;

import lombok.Data;

import java.util.List;
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
}
