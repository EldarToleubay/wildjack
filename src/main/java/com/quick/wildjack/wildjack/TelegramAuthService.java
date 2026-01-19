package com.quick.wildjack.wildjack;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Arrays;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

@Service
public class TelegramAuthService {

    private final UserProfileRepository userProfileRepository;
    private final String botToken;

    public TelegramAuthService(UserProfileRepository userProfileRepository,
                               @Value("${telegram.bot-token:}") String botToken) {
        this.userProfileRepository = userProfileRepository;
        this.botToken = botToken;
    }

    public TelegramAuthResponse authenticate(TelegramAuthRequest request) {
        if (request == null || request.getInitData() == null || request.getInitData().isBlank()) {
            throw new RuntimeException("initData is required");
        }
        if (request.getUser() == null || request.getUser().getId() == null) {
            throw new RuntimeException("user.id is required");
        }
        if (botToken == null || botToken.isBlank()) {
            throw new RuntimeException("Telegram bot token is not configured");
        }

        if (!verifyInitData(request.getInitData())) {
            throw new RuntimeException("Invalid initData signature");
        }

        TelegramUser tgUser = request.getUser();
        Instant now = Instant.now();
        UserProfile profile = userProfileRepository.findById(tgUser.getId())
                .orElseGet(UserProfile::new);

        if (profile.getCreatedAt() == null) {
            profile.setCreatedAt(now);
        }
        profile.setTelegramId(tgUser.getId());
        profile.setUsername(tgUser.getUsername());
        profile.setFirstName(tgUser.getFirstName());
        profile.setLastName(tgUser.getLastName());
        if (request.getDisplayName() != null && !request.getDisplayName().isBlank()) {
            profile.setDisplayName(request.getDisplayName());
        }
        if (tgUser.getPhotoUrl() != null && !tgUser.getPhotoUrl().isBlank()) {
            profile.setAvatarUrl(tgUser.getPhotoUrl());
        }
        profile.setUpdatedAt(now);
        profile.setLastLoginAt(now);

        UserProfile saved = userProfileRepository.save(profile);

        TelegramAuthResponse response = new TelegramAuthResponse();
        response.setUser(UserProfileResponse.from(saved));
        response.setSessionToken(saved.getTelegramId().toString());
        return response;
    }

    public UserProfileResponse getProfile(Long telegramId) {
        UserProfile profile = userProfileRepository.findById(telegramId)
                .orElseThrow(() -> new RuntimeException("User not found"));
        return UserProfileResponse.from(profile);
    }

    public UserProfileResponse updateProfile(Long telegramId, UserProfileUpdateRequest request) {
        UserProfile profile = userProfileRepository.findById(telegramId)
                .orElseThrow(() -> new RuntimeException("User not found"));
        if (request.getDisplayName() != null && !request.getDisplayName().isBlank()) {
            profile.setDisplayName(request.getDisplayName());
        }
        if (request.getAvatarUrl() != null && !request.getAvatarUrl().isBlank()) {
            profile.setAvatarUrl(request.getAvatarUrl());
        }
        profile.setUpdatedAt(Instant.now());
        UserProfile saved = userProfileRepository.save(profile);
        return UserProfileResponse.from(saved);
    }

    private boolean verifyInitData(String initData) {
        Map<String, String> params = Arrays.stream(initData.split("&"))
                .map(part -> part.split("=", 2))
                .filter(pair -> pair.length == 2)
                .collect(Collectors.toMap(pair -> pair[0], pair -> pair[1]));

        String hash = params.remove("hash");
        if (hash == null) {
            return false;
        }

        String dataCheckString = params.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .map(entry -> entry.getKey() + "=" + entry.getValue())
                .collect(Collectors.joining("\n"));

        String secretKey = hmacSha256(botToken, "WebAppData");
        String calculatedHash = hmacSha256(dataCheckString, secretKey);
        return Objects.equals(calculatedHash, hash);
    }

    private String hmacSha256(String data, String key) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            SecretKeySpec secretKeySpec = new SecretKeySpec(key.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
            mac.init(secretKeySpec);
            byte[] hash = mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder(hash.length * 2);
            for (byte b : hash) {
                hex.append(String.format("%02x", b));
            }
            return hex.toString();
        } catch (Exception e) {
            throw new RuntimeException("Failed to compute Telegram hash", e);
        }
    }
}
