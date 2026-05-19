package com.sentinel.decision_engine.service;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

@Service
public class EmailService {

    private final JavaMailSender mailSender;
    private final String gmailUser;

    public EmailService(JavaMailSender mailSender,
                        @Value("${spring.mail.username:}") String gmailUser) {
        this.mailSender = mailSender;
        this.gmailUser = gmailUser;
    }

    public void sendOtp(String toEmail, String txId, String otp) {
        if (gmailUser.isBlank()) {
            System.out.println("EMAIL DISABLED - OTP for " + txId + " is: " + otp);
            return;
        }
        SimpleMailMessage message = new SimpleMailMessage();
        message.setTo(toEmail);
        message.setSubject("Sentinel-Auth: Transaction Verification Required");
        message.setText(
                "A transaction requires your verification.\n\n" +
                        "Transaction ID : " + txId + "\n" +
                        "Your OTP       : " + otp + "\n\n" +
                        "This OTP is valid for 60 seconds.\n" +
                        "If you did not initiate this transaction, ignore this email — it will be blocked automatically."
        );
        mailSender.send(message);
    }
}