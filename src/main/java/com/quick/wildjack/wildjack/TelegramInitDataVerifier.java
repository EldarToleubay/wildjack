package com.quick.wildjack.wildjack;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.Arrays;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collectors;

public class TelegramInitDataVerifier {
    public static void main(String[] args) {
        String initData = args.length > 0 ? args[0] : System.getenv("TELEGRAM_INIT_DATA");
        String botToken = args.length > 1 ? args[1] : System.getenv("TELEGRAM_BOT_TOKEN");
        long maxAgeSeconds = args.length > 2 ? Long.parseLong(args[2]) : 3600;

        if (initData == null || initData.isBlank() || botToken == null || botToken.isBlank()) {
            System.err.println("Usage: TelegramInitDataVerifier <initData> <botToken> [maxAgeSeconds]");
            System.err.println("Or set TELEGRAM_INIT_DATA and TELEGRAM_BOT_TOKEN env vars.");
            System.exit(2);
        }

        boolean ok = verify(initData, botToken, maxAgeSeconds);
        System.out.println(ok ? "true" : "false");
    }

    public static boolean verify(String initData, String botToken, long maxAgeSeconds) {
        Map<String, String> params = Arrays.stream(initData.split("&"))
                .map(part -> part.split("=", 2))
                .filter(pair -> pair.length == 2)
                .collect(Collectors.toMap(
                        pair -> pair[0],
                        pair -> URLDecoder.decode(pair[1], StandardCharsets.UTF_8)
                ));

        String hash = params.remove("hash");
        if (hash == null) {
            return false;
        }

        String authDate = params.get("auth_date");
        if (authDate == null || authDate.isBlank()) {
            return false;
        }

        try {
            long timestamp = Long.parseLong(authDate);
            long now = Instant.now().getEpochSecond();
            if (now - timestamp > maxAgeSeconds) {
                return false;
            }
        } catch (NumberFormatException e) {
            return false;
        }

        String dataCheckString = new TreeMap<>(params).entrySet().stream()
                .map(entry -> entry.getKey() + "=" + entry.getValue())
                .collect(Collectors.joining("\n"));

        byte[] secretKey = sha256Bytes(botToken);
        String calculatedHash = hmacSha256Hex(dataCheckString, secretKey);

        return MessageDigest.isEqual(
                calculatedHash.getBytes(StandardCharsets.UTF_8),
                hash.getBytes(StandardCharsets.UTF_8)
        );
    }

    private static byte[] sha256Bytes(String data) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return digest.digest(data.getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) {
            throw new RuntimeException("Failed to compute SHA-256", e);
        }
    }

    private static String hmacSha256Hex(String data, byte[] key) {
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
