package com.sentinel.decision_engine.controller;

import com.sentinel.decision_engine.service.OtpService;
import com.sentinel.decision_engine.service.OtpService.VerifyResult;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

// Make sure to add this static import if you run into switch-case compilation issues with the nested enum
import static com.sentinel.decision_engine.service.OtpService.VerifyResult.*;

@RestController
@RequestMapping("/v1")
public class OtpController {

    private final OtpService otpService;

    public OtpController(OtpService otpService) {
        this.otpService = otpService;
    }

    @PostMapping("/verify-otp/{txId}")
    public ResponseEntity<Map<String, Object>> verifyOtp(
            @PathVariable String txId,
            @RequestBody Map<String, String> body) {

        if (body == null || !body.containsKey("otp") || body.get("otp").isBlank()) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "otp field is required"));
        }

        String submittedOtp = body.get("otp");
        VerifyResult result = otpService.verifyOtp(txId, submittedOtp);

        return switch (result) {
            case CORRECT -> ResponseEntity.ok(Map.of(
                    "transaction_id", txId,
                    "decision", "ALLOW",
                    "message", "OTP verified - transaction approved"
            ));
            case WRONG -> ResponseEntity.ok(Map.of(
                    "transaction_id", txId,
                    "decision", "BLOCK",
                    "message", "Wrong OTP - transaction blocked"
            ));
            case EXPIRED -> ResponseEntity.ok(Map.of(
                    "transaction_id", txId,
                    "decision", "BLOCK",
                    "message", "OTP expired - transaction blocked"
            ));
        };
    }
}