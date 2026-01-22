package com.quick.wildjack.wildjack;

import lombok.Data;

@Data
public class UserEventPayload {
    private String type;
    private Long requestId;
    private Long inviteId;
    private Long fromTelegramId;
    private Long toTelegramId;
    private String displayName;
    private String avatarUrl;
    private String fromDisplayName;
    private String fromAvatarUrl;
    private String toDisplayName;
    private String toAvatarUrl;
    private String gameId;
    private String playerId;
}
