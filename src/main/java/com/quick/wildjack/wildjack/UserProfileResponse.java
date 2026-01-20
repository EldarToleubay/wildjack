package com.quick.wildjack.wildjack;

import lombok.Data;

import java.time.Instant;

@Data
public class UserProfileResponse {
    private Long telegramId;
    private String username;
    private String displayName;
    private String firstName;
    private String lastName;
    private String avatarUrl;
    private String startParam;
    private Instant createdAt;
    private Instant updatedAt;
    private Instant lastLoginAt;

    public static UserProfileResponse from(UserProfile profile) {
        UserProfileResponse response = new UserProfileResponse();
        response.setTelegramId(profile.getTelegramId());
        response.setUsername(profile.getUsername());
        response.setDisplayName(profile.getDisplayName());
        response.setFirstName(profile.getFirstName());
        response.setLastName(profile.getLastName());
        response.setAvatarUrl(profile.getAvatarUrl());
        response.setStartParam(profile.getStartParam());
        response.setCreatedAt(profile.getCreatedAt());
        response.setUpdatedAt(profile.getUpdatedAt());
        response.setLastLoginAt(profile.getLastLoginAt());
        return response;
    }
}
