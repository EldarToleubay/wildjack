package com.quick.wildjack.wildjack;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/users")
@RequiredArgsConstructor
public class UserController {

    private final TelegramAuthService telegramAuthService;

    @GetMapping("/me")
    public ResponseEntity<UserProfileResponse> getMe(@RequestHeader("X-Telegram-Id") Long telegramId) {
        return ResponseEntity.ok(telegramAuthService.getProfile(telegramId));
    }

    @PatchMapping("/me")
    public ResponseEntity<UserProfileResponse> updateMe(
            @RequestHeader("X-Telegram-Id") Long telegramId,
            @RequestBody UserProfileUpdateRequest request
    ) {
        return ResponseEntity.ok(telegramAuthService.updateProfile(telegramId, request));
    }
}
