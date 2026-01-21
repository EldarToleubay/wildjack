package com.quick.wildjack.wildjack;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.Arrays;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;
import java.util.stream.Collectors;

@Service
public class TelegramAuthService {

    private static final Logger log = LoggerFactory.getLogger(TelegramAuthService.class);
    private final UserProfileRepository userProfileRepository;
    private final ObjectMapper objectMapper;
    private final String botToken;
    private final long maxAuthAgeSeconds;
    private final long maxFutureSkewSeconds;
    private final boolean authDebug;

    public TelegramAuthService(UserProfileRepository userProfileRepository,
                               ObjectMapper objectMapper,
                               @Value("${telegram.bot-token:}") String botToken,
                               @Value("${telegram.auth.max-age-seconds:3600}") long maxAuthAgeSeconds,
                               @Value("${telegram.auth.max-future-skew-seconds:300}") long maxFutureSkewSeconds,
                               @Value("${telegram.auth.debug:false}") boolean authDebug) {
        this.userProfileRepository = userProfileRepository;
        this.objectMapper = objectMapper;
        this.botToken = botToken;
        this.maxAuthAgeSeconds = maxAuthAgeSeconds;
        this.maxFutureSkewSeconds = maxFutureSkewSeconds;
        this.authDebug = authDebug;
    }

    public TelegramAuthResponse authenticate(TelegramAuthRequest request) {
        if (request == null || request.getInitData() == null || request.getInitData().isBlank()) {
            throw new RuntimeException("initData is required");
        }
        if (botToken == null || botToken.isBlank()) {
            throw new RuntimeException("Telegram bot token is not configured");
        }

        TelegramParsedInitData parsed = parseAndValidate(request.getInitData());

        Instant now = Instant.now();
        UserProfile profile = userProfileRepository.findById(parsed.telegramId())
                .orElseGet(UserProfile::new);

        if (profile.getCreatedAt() == null) {
            profile.setCreatedAt(now);
        }
        profile.setTelegramId(parsed.telegramId());
        profile.setUsername(parsed.username());
        profile.setFirstName(parsed.firstName());
        profile.setLastName(parsed.lastName());
        profile.setLanguageCode(parsed.languageCode());
        if (parsed.photoUrl() != null && !parsed.photoUrl().isBlank()) {
            profile.setAvatarUrl(parsed.photoUrl());
        }
        if (profile.getDisplayName() == null || profile.getDisplayName().isBlank()) {
            profile.setDisplayName(defaultDisplayName(parsed));
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

    public java.util.List<UserProfileResponse> getLeaderboard(int limit) {
        return userProfileRepository.findAll().stream()
                .sorted(java.util.Comparator.comparingInt(this::ratingSafe).reversed())
                .limit(Math.max(1, limit))
                .map(UserProfileResponse::from)
                .toList();
    }

    private int ratingSafe(UserProfile profile) {
        return profile.getRating() == null ? 0 : profile.getRating();
    }

    private boolean verifyInitData(String initData) {
        ValidationLogDetails details = validateInitData(parseInitData(initData));
        if (authDebug) {
            logValidation(details);
        }
        return details.ok();
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

    public ValidationLogDetails validateInitData(Map<String, String> rawParams) {
        Map<String, String> params = new TreeMap<>(rawParams);
        String keysBefore = String.join(",", params.keySet());
        String hash = params.remove("hash");
        String keysAfter = String.join(",", params.keySet());
        boolean hasSignature = params.containsKey("signature");
        int signatureLength = hasSignature ? params.get("signature").length() : 0;

        String dataCheckString = params.entrySet().stream()
                .map(entry -> entry.getKey() + "=" + entry.getValue())
                .collect(Collectors.joining("\n"));

        byte[] secretKey = deriveWebAppSecretKey(botToken);
        String calculatedHash = hmacSha256Hex(dataCheckString, secretKey);
        String incomingHashPrefix = hash == null ? "" : hash.substring(0, Math.min(10, hash.length()));
        String calculatedHashPrefix = calculatedHash.substring(0, Math.min(10, calculatedHash.length()));
        boolean matches = hash != null && MessageDigest.isEqual(
                calculatedHash.getBytes(StandardCharsets.UTF_8),
                hash.getBytes(StandardCharsets.UTF_8)
        );

        AuthDateDetails authDateDetails = getAuthDateDetails(params.get("auth_date"));
        boolean ok = matches && authDateDetails.fresh;

        return new ValidationLogDetails(
                keysBefore,
                keysAfter,
                hasSignature,
                signatureLength,
                incomingHashPrefix,
                calculatedHashPrefix,
                matches,
                dataCheckString,
                secretKey,
                authDateDetails,
                ok
        );
    }

    private byte[] sha256Bytes(String data) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return digest.digest(data.getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) {
            throw new RuntimeException("Failed to compute SHA-256", e);
        }
    }

    private byte[] deriveWebAppSecretKey(String token) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            SecretKeySpec keySpec = new SecretKeySpec("WebAppData".getBytes(StandardCharsets.UTF_8), "HmacSHA256");
            mac.init(keySpec);
            return mac.doFinal(token.getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) {
            throw new RuntimeException("Failed to derive WebApp secret key", e);
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

    private AuthDateDetails getAuthDateDetails(String authDate) {
        long now = Instant.now().getEpochSecond();
        if (authDate == null || authDate.isBlank()) {
            return new AuthDateDetails(0L, now, now, maxAuthAgeSeconds, false);
        }
        try {
            long timestamp = Long.parseLong(authDate);
            long skew = now - timestamp;
            boolean fresh = skew <= maxAuthAgeSeconds && skew >= -maxFutureSkewSeconds;
            return new AuthDateDetails(timestamp, now, skew, maxAuthAgeSeconds, fresh);
        } catch (NumberFormatException e) {
            return new AuthDateDetails(0L, now, now, maxAuthAgeSeconds, false);
        }
    }

    private void logValidation(ValidationLogDetails details) {
        log.debug("telegram-auth keys_before=[{}] keys_after=[{}] signature_present={} signature_length={}",
                details.keysBefore(), details.keysAfter(), details.hasSignature(), details.signatureLength());
        log.debug("telegram-auth incoming_hash_prefix={} calculated_hash_prefix={} equal={}",
                details.incomingHashPrefix(), details.calculatedHashPrefix(), details.hashEqual());
        log.debug("telegram-auth data_check_string_length={} data_check_string_preview={}",
                details.dataCheckString().length(), escapePreview(details.dataCheckString(), 180));
        log.debug("telegram-auth data_first_lines={} data_last_lines={}",
                details.firstLines(), details.lastLines());
        log.debug("telegram-auth bot_token_mask={} secret_key_hex_prefix={}",
                maskToken(botToken), bytesToHexPrefix(details.secretKey(), 8));
        log.debug("telegram-auth auth_date={} now_epoch_seconds={} skew_seconds={} maxAgeSeconds={}",
                details.authDateDetails().authDate(),
                details.authDateDetails().nowEpochSeconds(),
                details.authDateDetails().skewSeconds(),
                details.authDateDetails().maxAgeSeconds());
    }

    private String escapePreview(String value, int maxLen) {
        String trimmed = value.substring(0, Math.min(maxLen, value.length()));
        return trimmed.replace("\n", "\\n");
    }

    private String bytesToHexPrefix(byte[] bytes, int length) {
        StringBuilder hex = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            hex.append(String.format("%02x", b));
        }
        int end = Math.min(length * 2, hex.length());
        return hex.substring(0, end);
    }

    private String maskToken(String token) {
        if (token == null) {
            return "";
        }
        if (token.length() <= 10) {
            return token.charAt(0) + "***" + token.charAt(token.length() - 1);
        }
        return token.substring(0, 5) + "***" + token.substring(token.length() - 5);
    }

    private record AuthDateDetails(long authDate, long nowEpochSeconds, long skewSeconds, long maxAgeSeconds,
                                   boolean fresh) {
    }

    private record ValidationLogDetails(
            String keysBefore,
            String keysAfter,
            boolean hasSignature,
            int signatureLength,
            String incomingHashPrefix,
            String calculatedHashPrefix,
            boolean hashEqual,
            String dataCheckString,
            byte[] secretKey,
            AuthDateDetails authDateDetails,
            boolean ok
    ) {
        private String[] splitLines() {
            return dataCheckString.split("\n");
        }

        private String trimLine(String line) {
            return line.length() <= 120 ? line : line.substring(0, 120);
        }

        String firstLines() {
            String[] lines = splitLines();
            int count = Math.min(3, lines.length);
            return Arrays.stream(lines, 0, count)
                    .map(this::trimLine)
                    .collect(Collectors.joining(","));
        }

        String lastLines() {
            String[] lines = splitLines();
            int count = Math.min(3, lines.length);
            return Arrays.stream(lines, Math.max(0, lines.length - count), lines.length)
                    .map(this::trimLine)
                    .collect(Collectors.joining(","));
        }
    }

    private TelegramParsedInitData parseAndValidate(String initData) {
        Map<String, String> params = parseInitData(initData);
        ValidationLogDetails details = validateInitData(params);
        if (authDebug) {
            logValidation(details);
        }
        if (!details.ok()) {
            throw new RuntimeException("Invalid initData signature");
        }

        TelegramParsedInitData parsed = parseTelegramUser(params);
        if (parsed.telegramId() == null) {
            throw new RuntimeException("Telegram user id missing");
        }
        return parsed;
    }

    private TelegramParsedInitData parseTelegramUser(Map<String, String> params) {
        String userJson = params.get("user");
        if (userJson == null || userJson.isBlank()) {
            return new TelegramParsedInitData(null, null, null, null, null, null, null);
        }
        try {
            TelegramUser user = objectMapper.readValue(userJson, TelegramUser.class);
            return new TelegramParsedInitData(
                    user.getId(),
                    user.getUsername(),
                    user.getFirstName(),
                    user.getLastName(),
                    user.getPhotoUrl(),
                    user.getLanguageCode(),
                    params.get("auth_date")
            );
        } catch (Exception e) {
            throw new RuntimeException("Failed to parse Telegram user", e);
        }
    }

    private String defaultDisplayName(TelegramParsedInitData parsed) {
        if (parsed.username() != null && !parsed.username().isBlank()) {
            return "@" + parsed.username();
        }
        String fullName = String.join(" ",
                parsed.firstName() == null ? "" : parsed.firstName(),
                parsed.lastName() == null ? "" : parsed.lastName()
        ).trim();
        if (!fullName.isBlank()) {
            return fullName;
        }
        if (parsed.firstName() != null && !parsed.firstName().isBlank()) {
            return parsed.firstName();
        }
        return "Player";
    }

    private record TelegramParsedInitData(Long telegramId, String username, String firstName, String lastName,
                                          String photoUrl, String languageCode, String authDate) {
    }
}
