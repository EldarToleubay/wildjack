package com.quick.wildjack.wildjack;

import lombok.Data;

@Data
public class InviteAcceptResponse {
    private GameInvite invite;
    private Game game;
    private String playerId;
}
