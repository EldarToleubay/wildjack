package com.quick.wildjack.wildjack;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/invites")
@CrossOrigin("*")
@RequiredArgsConstructor
public class InviteController {

    private final InviteService inviteService;

    @PostMapping
    public ResponseEntity<GameInvite> sendInvite(
            @RequestHeader("X-Telegram-Id") Long telegramId,
            @RequestBody InviteRequestPayload payload
    ) {
        return ResponseEntity.ok(inviteService.sendInvite(telegramId, payload.getToTelegramId(), payload.getGameId()));
    }

    @PostMapping("/{inviteId}/accept")
    public ResponseEntity<InviteAcceptResponse> acceptInvite(
            @RequestHeader("X-Telegram-Id") Long telegramId,
            @PathVariable Long inviteId
    ) {
        return ResponseEntity.ok(inviteService.acceptInvite(inviteId, telegramId));
    }

    @PostMapping("/{inviteId}/reject")
    public ResponseEntity<GameInvite> rejectInvite(
            @RequestHeader("X-Telegram-Id") Long telegramId,
            @PathVariable Long inviteId
    ) {
        return ResponseEntity.ok(inviteService.rejectInvite(inviteId, telegramId));
    }

    @GetMapping
    public ResponseEntity<List<GameInvite>> listInvites(
            @RequestHeader("X-Telegram-Id") Long telegramId,
            @RequestParam String type
    ) {
        if ("incoming".equalsIgnoreCase(type)) {
            return ResponseEntity.ok(inviteService.listIncoming(telegramId));
        }
        if ("outgoing".equalsIgnoreCase(type)) {
            return ResponseEntity.ok(inviteService.listOutgoing(telegramId));
        }
        throw new RuntimeException("Unknown invite type");
    }
}
