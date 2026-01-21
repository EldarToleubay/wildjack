package com.quick.wildjack.wildjack;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/friends")
@CrossOrigin("*")
@RequiredArgsConstructor
public class FriendController {

    private final FriendService friendService;

    @PostMapping("/request")
    public ResponseEntity<FriendRequest> requestFriend(
            @RequestHeader("X-Telegram-Id") Long telegramId,
            @RequestBody FriendRequestPayload payload
    ) {
        return ResponseEntity.ok(friendService.sendRequest(telegramId, payload.getDisplayName()));
    }

    @PostMapping("/accept")
    public ResponseEntity<FriendRequest> acceptFriend(
            @RequestHeader("X-Telegram-Id") Long telegramId,
            @RequestParam Long requestId
    ) {
        return ResponseEntity.ok(friendService.acceptRequest(requestId, telegramId));
    }

    @PostMapping("/reject")
    public ResponseEntity<FriendRequest> rejectFriend(
            @RequestHeader("X-Telegram-Id") Long telegramId,
            @RequestParam Long requestId
    ) {
        return ResponseEntity.ok(friendService.rejectRequest(requestId, telegramId));
    }

    @GetMapping
    public ResponseEntity<List<Friendship>> listFriends(@RequestHeader("X-Telegram-Id") Long telegramId) {
        return ResponseEntity.ok(friendService.listFriends(telegramId));
    }

    @GetMapping("/requests")
    public ResponseEntity<List<FriendRequest>> listRequests(
            @RequestHeader("X-Telegram-Id") Long telegramId,
            @RequestParam String type
    ) {
        if ("incoming".equalsIgnoreCase(type)) {
            return ResponseEntity.ok(friendService.listIncoming(telegramId));
        }
        if ("outgoing".equalsIgnoreCase(type)) {
            return ResponseEntity.ok(friendService.listOutgoing(telegramId));
        }
        throw new RuntimeException("Unknown request type");
    }
}
