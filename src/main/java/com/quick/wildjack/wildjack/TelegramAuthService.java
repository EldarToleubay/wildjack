package com.quick.wildjack.wildjack;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.net.URLDecoder;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;
import java.util.stream.Collectors;

@Service
public class TelegramAuthService {

    private static final Logger log = LoggerFactory.getLogger(TelegramAuthService.class);
    private final UserProfileRepository userProfileRepository;
    private final String botToken;
    private final long maxAuthAgeSeconds;
    private final boolean authDebug;

    public TelegramAuthService(UserProfileRepository userProfileRepository,
                               @Value("${telegram.bot-token:}") String botToken,
                               @Value("${telegram.auth.max-age-seconds:3600}") long maxAuthAgeSeconds,
                               @Value("${telegram.auth.debug:false}") boolean authDebug) {
        this.userProfileRepository = userProfileRepository;
        this.botToken = botToken;
        this.maxAuthAgeSeconds = maxAuthAgeSeconds;
        this.authDebug = authDebug;
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
        Map<String, String> params = parseInitData(initData);
        String hash = params.remove("hash");
        if (hash == null) {
            return false;
        }

        if (!isAuthDateFresh(params.get("auth_date"))) {
            return false;
        }

        String dataCheckString = new TreeMap<>(params).entrySet().stream()
                .map(entry -> entry.getKey() + "=" + entry.getValue())
                .collect(Collectors.joining("\n"));

        byte[] secretKey = sha256Bytes(botToken);
        String calculatedHash = hmacSha256Hex(dataCheckString, secretKey);
        boolean matches = MessageDigest.isEqual(
                calculatedHash.getBytes(StandardCharsets.UTF_8),
                hash.getBytes(StandardCharsets.UTF_8)
        );

        if (authDebug) {
            log.debug(
                    "telegram-auth keys={} data_check_prefix={} calculated_hash_prefix={}",
                    String.join(",", new TreeMap<>(params).keySet()),
                    dataCheckString.substring(0, Math.min(120, dataCheckString.length())),
                    calculatedHash.substring(0, Math.min(12, calculatedHash.length()))
            );
        }

        return matches;
    }

    private Map<String, String> parseInitData(String initData) {
        return Arrays.stream(initData.split("&"))
                .map(part -> part.split("=", 2))
                .filter(pair -> pair.length == 2)
                .collect(Collectors.toMap(
                        pair -> pair[0],
                        pair -> URLDecoder.decode(pair[1], StandardCharsets.UTF_8)
                ));
    }

    private boolean isAuthDateFresh(String authDate) {
        if (authDate == null || authDate.isBlank()) {
            return false;
        }
        try {
            long timestamp = Long.parseLong(authDate);
            long now = Instant.now().getEpochSecond();
            return now - timestamp <= maxAuthAgeSeconds;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    private byte[] sha256Bytes(String data) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return digest.digest(data.getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) {
            throw new RuntimeException("Failed to compute SHA-256", e);
        }
    }

    private String hmacSha256Hex(String data, byte[] key) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            SecretKeySpec secretKeySpec = new SecretKeySpec(key, "HmacSHA256");
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
