package com.quick.wildjack.wildjack;

import lombok.Data;

@Data
public class UserProfileUpdateRequest {
    private String displayName;
    private String avatarUrl;
}
