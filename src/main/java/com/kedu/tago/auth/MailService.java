package com.kedu.tago.auth;

import com.kedu.tago.config.RedisConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.util.UUID;

@Service
public class MailService {

    private static final Logger log = LoggerFactory.getLogger(MailService.class);
    private static final long CODE_EXPIRATION_MS = 5 * 60 * 1000L; // 5분
    private static final long TOKEN_EXPIRATION_MS = 10 * 60 * 1000L; // 10분

    private final RedisConfig redisConfig;
    private final JavaMailSender mailSender;

    @Value("${spring.mail.username:}")
    private String senderEmail;

    public MailService(RedisConfig redisConfig, @Autowired(required = false) JavaMailSender mailSender) {
        this.redisConfig = redisConfig;
        this.mailSender = mailSender;
    }

    // 6자리 난수 인증코드 생성
    public String generateVerificationCode() {
        SecureRandom random = new SecureRandom();
        int code = 100000 + random.nextInt(900000);
        return String.valueOf(code);
    }

    // 공통 이메일 발송 메서드 (실제 발송 + 실패 시 로깅 지원)
    public void sendEmail(String toEmail, String subject, String content) {
        boolean sent = false;

        if (mailSender == null) {
            log.warn("⚠️ JavaMailSender 빈이 없습니다. IntelliJ에서 'Reload All Gradle Projects'를 눌러 의존성을 갱신해 주세요.");
        } else if (senderEmail == null || senderEmail.trim().isEmpty()) {
            log.warn("⚠️ spring.mail.username이 비어있습니다. .env 파일 및 application.properties 설정을 확인해 주세요.");
        } else {
            try {
                SimpleMailMessage message = new SimpleMailMessage();
                message.setFrom(senderEmail);
                message.setTo(toEmail);
                message.setSubject(subject);
                message.setText(content);
                mailSender.send(message);
                sent = true;
                log.info("✅ 이메일 실제 발송 성공! to: {}", toEmail);
            } catch (Exception e) {
                log.error("❌ 이메일 실제 발송 실패 (원인: {})", e.getMessage(), e);
            }
        }

        if (!sent) {
            log.info("\n======================================================\n" +
                    "[TAGO 이메일 인증코드 (콘솔 확인용)]\n" +
                    "수신자: {}\n" +
                    "제목: {}\n" +
                    "내용:\n{}\n" +
                    "======================================================", toEmail, subject, content);
        }
    }

    // 1. 아이디 찾기 인증번호 발송
    public void sendFindIdCode(String email) {
        String code = generateVerificationCode();
        String redisKey = "VERIFY:FIND_ID:" + email;
        redisConfig.setValue(redisKey, code, CODE_EXPIRATION_MS);

        String subject = "[TAGO] 아이디 찾기 인증번호 안내";
        String content = "안녕하세요, TAGO입니다.\n\n" +
                "아이디 찾기를 위한 인증번호는 다음과 같습니다.\n\n" +
                "인증번호: [" + code + "]\n\n" +
                "5분 이내에 인증번호를 입력해 주세요.\n감사합니다.";
        sendEmail(email, subject, content);
    }

    // 아이디 찾기 인증번호 검증
    public boolean verifyFindIdCode(String email, String inputCode) {
        String redisKey = "VERIFY:FIND_ID:" + email;
        String savedCode = redisConfig.getValue(redisKey);
        if (savedCode != null && savedCode.equals(inputCode)) {
            redisConfig.deleteValue(redisKey);
            return true;
        }
        return false;
    }

    // 2. 비밀번호 재설정 인증번호 발송 (로그인 화면용)
    public void sendResetPasswordCode(String email) {
        String code = generateVerificationCode();
        String redisKey = "VERIFY:RESET_PW:" + email;
        redisConfig.setValue(redisKey, code, CODE_EXPIRATION_MS);

        String subject = "[TAGO] 비밀번호 재설정 인증번호 안내";
        String content = "안녕하세요, TAGO입니다.\n\n" +
                "비밀번호 재설정을 위한 인증번호는 다음과 같습니다.\n\n" +
                "인증번호: [" + code + "]\n\n" +
                "5분 이내에 인증번호를 입력해 주세요.\n감사합니다.";
        sendEmail(email, subject, content);
    }

    // 비밀번호 재설정 인증번호 검증 -> 성공 시 일회용 재설정 토큰 발급
    public String verifyResetPasswordCode(String email, String inputCode) {
        String redisKey = "VERIFY:RESET_PW:" + email;
        String savedCode = redisConfig.getValue(redisKey);
        if (savedCode != null && savedCode.equals(inputCode)) {
            redisConfig.deleteValue(redisKey);
            // 1회용 재설정 토큰 발급 (UUID)
            String resetToken = UUID.randomUUID().toString();
            redisConfig.setValue("TOKEN:RESET_PW:" + resetToken, email, TOKEN_EXPIRATION_MS);
            return resetToken;
        }
        return null;
    }

    // 비밀번호 재설정 토큰 검증 및 소멸
    public boolean validateAndConsumeResetToken(String resetToken, String email) {
        String redisKey = "TOKEN:RESET_PW:" + resetToken;
        String savedEmail = redisConfig.getValue(redisKey);
        if (savedEmail != null && savedEmail.equals(email)) {
            redisConfig.deleteValue(redisKey);
            return true;
        }
        return false;
    }

    // 3. 마이페이지 비밀번호 변경용 인증코드 발송
    public void sendMypagePasswordCode(String email) {
        String code = generateVerificationCode();
        String redisKey = "VERIFY:MYPAGE_PW:" + email;
        redisConfig.setValue(redisKey, code, CODE_EXPIRATION_MS);

        String subject = "[TAGO] 마이페이지 비밀번호 변경 인증번호 안내";
        String content = "안녕하세요, TAGO입니다.\n\n" +
                "마이페이지 비밀번호 변경을 위한 인증번호는 다음과 같습니다.\n\n" +
                "인증번호: [" + code + "]\n\n" +
                "5분 이내에 인증번호를 입력해 주세요.\n감사합니다.";
        sendEmail(email, subject, content);
    }

    // 마이페이지 인증코드 검증
    public boolean verifyMypagePasswordCode(String email, String inputCode) {
        String redisKey = "VERIFY:MYPAGE_PW:" + email;
        String savedCode = redisConfig.getValue(redisKey);
        if (savedCode != null && savedCode.equals(inputCode)) {
            redisConfig.deleteValue(redisKey);
            // 인증 완료 플래그 10분간 저장
            redisConfig.setValue("VERIFIED:MYPAGE_PW:" + email, "true", TOKEN_EXPIRATION_MS);
            return true;
        }
        return false;
    }

    // 마이페이지 비밀번호 변경 권한 확인 및 소비
    public boolean consumeMypageVerification(String email) {
        String redisKey = "VERIFIED:MYPAGE_PW:" + email;
        String verified = redisConfig.getValue(redisKey);
        if ("true".equals(verified)) {
            redisConfig.deleteValue(redisKey);
            return true;
        }
        return false;
    }
}
