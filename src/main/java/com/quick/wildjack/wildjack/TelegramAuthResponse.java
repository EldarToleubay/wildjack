package com.quick.wildjack.wildjack;

import lombok.Data;

@Data
public class TelegramAuthResponse {
    private UserProfileResponse user;
    private String sessionToken;
}
