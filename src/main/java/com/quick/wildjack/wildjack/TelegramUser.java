package com.quick.wildjack.wildjack;

import lombok.Data;

@Data
public class TelegramUser {
    private Long id;
    private String username;
    private String firstName;
    private String lastName;
    private String photoUrl;
    private String languageCode;
}
