package com.quick.wildjack.wildjack;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import lombok.Data;

@Data
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
@JsonIgnoreProperties(ignoreUnknown = true)
public class TelegramUser {
    private Long id;
    private String username;
    private String firstName;
    private String lastName;
    private String photoUrl;
    private String languageCode;
}
