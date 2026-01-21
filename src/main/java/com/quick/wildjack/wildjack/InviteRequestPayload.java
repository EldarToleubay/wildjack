package com.quick.wildjack.wildjack;

import lombok.Data;

@Data
public class InviteRequestPayload {
    private Long toTelegramId;
    private String gameId;
}
