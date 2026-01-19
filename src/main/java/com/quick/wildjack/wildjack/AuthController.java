package com.quick.wildjack.wildjack;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
public class AuthController {

    private final TelegramAuthService telegramAuthService;

    @PostMapping("/telegram-lite")
    public ResponseEntity<TelegramAuthResponse> telegramLite(@RequestBody TelegramAuthRequest request) {
        return ResponseEntity.ok(telegramAuthService.authenticate(request));
    }
}
