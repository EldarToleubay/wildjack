package com.quick.wildjack.wildjack;

import lombok.Data;

@Data
public class TelegramAuthRequest {
    private String initData;
    private TelegramUser user;
    private String displayName;
}
