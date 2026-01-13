package com.quick.wildjack.wildjack;

import lombok.Data;

@Data
public class MoveMessage {
    private String gameId;
    private String playerId;
    private Card card;
    private int x;
    private int y;
}
