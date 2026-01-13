package com.quick.wildjack.wildjack;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class JoinGameResponse {
    private Game game;
    private String playerId;
}
