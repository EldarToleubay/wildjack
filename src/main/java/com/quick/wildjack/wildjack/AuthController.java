package com.quick.wildjack.wildjack;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/auth")
@CrossOrigin("*")
@RequiredArgsConstructor
@Slf4j
public class AuthController {

    private final TelegramAuthService telegramAuthService;

    @PostMapping("/telegram-lite")
    public ResponseEntity<TelegramAuthResponse> telegramLite(@RequestBody TelegramAuthRequest request) {
        log.info("Telegram lite request received");
        log.info("InitData:"+ request.getInitData());
        log.info("user.id:"+ request.getUser().getId());
        log.info("user.photourl:"+ request.getUser().getPhotoUrl());
        return ResponseEntity.ok(telegramAuthService.authenticate(request));
    }
}
