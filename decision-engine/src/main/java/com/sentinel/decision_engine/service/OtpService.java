package com.sentinel.decision_engine.service;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.util.concurrent.TimeUnit;

@Service
public class OtpService {

    private static final String OTP_KEY    = "otp:";
    private static final String EMAIL_KEY  = "otp:email:";
    private static final int    OTP_TTL_S  = 60;

    private final StringRedisTemplate redis;
    private final SecureRandom        random = new SecureRandom();

    public OtpService(StringRedisTemplate redis) {
        this.redis = redis;
    }

    // generate and store OTP for txId
    public String generateOtp(String txId) {
        String otp = String.format("%06d", random.nextInt(1_000_000));
        redis.opsForValue().set(OTP_KEY + txId, otp, OTP_TTL_S, TimeUnit.SECONDS);
        return otp;
    }

    // store the email linked to this txId (needed for response)
    public void storeEmail(String txId, String email) {
        redis.opsForValue().set(EMAIL_KEY + txId, email, OTP_TTL_S + 10, TimeUnit.SECONDS);
    }

    public String getEmail(String txId) {
        return redis.opsForValue().get(EMAIL_KEY + txId);
    }

    public enum VerifyResult { CORRECT, WRONG, EXPIRED }

    public VerifyResult verifyOtp(String txId, String submittedOtp) {
        String stored = redis.opsForValue().get(OTP_KEY + txId);
        if (stored == null) return VerifyResult.EXPIRED;
        if (stored.equals(submittedOtp)) {
            redis.delete(OTP_KEY + txId); // single use
            return VerifyResult.CORRECT;
        }
        return VerifyResult.WRONG;
    }
}