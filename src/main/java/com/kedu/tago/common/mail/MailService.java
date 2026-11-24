package com.kedu.tago.common.mail;

import com.kedu.tago.common.exception.ApiException;
import com.kedu.tago.common.exception.ErrorCode;
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
import java.util.concurrent.TimeUnit;

/** 이메일 인증코드 발송·검증 및 재설정 토큰 관리 (Redis 저장) */
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

    /** 6자리 난수 인증코드 생성 */
    public String generateVerificationCode() {
        SecureRandom random = new SecureRandom();
        int code = 100000 + random.nextInt(900000);
        return String.valueOf(code);
    }

    /** 이메일 주소 마스킹 (개인정보 보호용 로그) */
    private String maskEmail(String email) {
        if (email == null || !email.contains("@")) {
            return "***";
        }
        int atIndex = email.indexOf('@');
        String name = email.substring(0, atIndex);
        String domain = email.substring(atIndex);
        if (name.length() <= 2) {
            return name.charAt(0) + "*" + domain;
        }
        return name.substring(0, 2) + "*".repeat(name.length() - 2) + domain;
    }

    /** 이메일 발송 (실패 시 예외 발생, 본문/인증코드는 로그에 남기지 않음) */
    public void sendEmail(String toEmail, String subject, String content) {
        if (mailSender == null) {
            log.error("❌ 메일 발송 불가: JavaMailSender 빈이 구성되지 않았습니다.");
            throw new ApiException(ErrorCode.MAIL_SEND_FAILED, "메일 발송 서비스가 준비되지 않았습니다. 관리자에게 문의해 주세요.");
        }
        if (senderEmail == null || senderEmail.trim().isEmpty()) {
            log.error("❌ 메일 발송 불가: 발신자 이메일(spring.mail.username)이 설정되지 않았습니다.");
            throw new ApiException(ErrorCode.MAIL_SEND_FAILED, "메일 발신자 설정이 올바르지 않습니다. 관리자에게 문의해 주세요.");
        }

        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setFrom(senderEmail);
            message.setTo(toEmail);
            message.setSubject(subject);
            message.setText(content);
            mailSender.send(message);
            log.info("✅ 이메일 발송 성공 (수신자: {})", maskEmail(toEmail));
        } catch (Exception e) {
            log.error("❌ 이메일 실제 발송 실패 (수신자: {}, 원인: {})", maskEmail(toEmail), e.getMessage());
            throw new ApiException(ErrorCode.MAIL_SEND_FAILED, "이메일 발송에 실패했습니다. 잠시 후 다시 시도해 주세요.");
        }
    }

    /** 아이디 찾기 인증번호 발송 */
    public void sendFindIdCode(String email) {
        String code = generateVerificationCode();
        String subject = "[TAGO] 아이디 찾기 인증번호 안내";
        String content = "안녕하세요, TAGO입니다.\n\n" +
                "아이디 찾기를 위한 인증번호는 다음과 같습니다.\n\n" +
                "인증번호: [" + code + "]\n\n" +
                "5분 이내에 인증번호를 입력해 주세요.\n감사합니다.";

        // 메일 실제 발송 먼저 수행 (실패 시 예외 발생)
        sendEmail(email, subject, content);

        // 발송 성공 시에만 Redis에 저장
        String redisKey = "VERIFY:FIND_ID:" + email;
        redisConfig.setValue(redisKey, code, CODE_EXPIRATION_MS);
    }

    /** 아이디 찾기 인증번호 검증 */
    public boolean verifyFindIdCode(String email, String inputCode) {
        String redisKey = "VERIFY:FIND_ID:" + email;
        String savedCode = redisConfig.getValue(redisKey);
        if (savedCode != null && savedCode.equals(inputCode)) {
            redisConfig.deleteValue(redisKey);
            return true;
        }
        return false;
    }

    /** 비밀번호 재설정 인증번호 발송 (아이디+이메일 결합 키, 60초 쿨다운, 5분 유효) */
    public void sendResetPasswordCode(String id, String email) {
        String cooldownKey = "VERIFY:RESET_PW:COOLDOWN:" + id.trim() + ":" + email.trim();
        if (redisConfig.hasKey(cooldownKey)) {
            throw new ApiException(ErrorCode.EMAIL_SEND_COOLDOWN);
        }

        String code = generateVerificationCode();
        String subject = "[TAGO] 비밀번호 재설정 인증번호 안내";
        String content = "안녕하세요, TAGO입니다.\n\n" +
                "회원님의 계정(" + id.trim() + ") 비밀번호 재설정을 위한 인증번호는 다음과 같습니다.\n\n" +
                "인증번호: [" + code + "]\n\n" +
                "5분 이내에 인증번호를 입력해 주세요.\n감사합니다.";

        // 메일 실제 발송 먼저 수행 (실패 시 예외 발생)
        sendEmail(email, subject, content);

        // 발송 성공 시에만 Redis에 저장 및 쿨다운 설정
        String redisKey = "VERIFY:RESET_PW:CODE:" + id.trim() + ":" + email.trim();
        String failKey = "VERIFY:RESET_PW:FAIL:" + id.trim() + ":" + email.trim();

        redisConfig.setValue(redisKey, code, CODE_EXPIRATION_MS);
        redisConfig.deleteValue(failKey);
        redisConfig.setValue(cooldownKey, "1", 60, TimeUnit.SECONDS);
    }

    /** 비밀번호 재설정 인증번호 검증 후 일회용 재설정 토큰 발급 (회원 아이디에 바인딩, 10분 유효) */
    public String verifyResetPasswordCode(String id, String email, String inputCode) {
        String redisKey = "VERIFY:RESET_PW:CODE:" + id.trim() + ":" + email.trim();
        String failKey = "VERIFY:RESET_PW:FAIL:" + id.trim() + ":" + email.trim();

        String savedCode = redisConfig.getValue(redisKey);
        if (savedCode == null) {
            throw new ApiException(ErrorCode.VERIFICATION_EXPIRED);
        }

        Long failCount = redisConfig.increment(failKey);
        if (failCount != null && failCount == 1) {
            redisConfig.expire(failKey, 5, TimeUnit.MINUTES);
        }

        if (failCount != null && failCount > 5) {
            redisConfig.deleteValue(redisKey);
            redisConfig.deleteValue(failKey);
            throw new ApiException(ErrorCode.VERIFICATION_ATTEMPTS_EXCEEDED);
        }

        if (!savedCode.equals(inputCode)) {
            int remaining = Math.max(0, 5 - (failCount != null ? failCount.intValue() : 0));
            throw new ApiException(ErrorCode.CODE_MISMATCH, "인증번호가 일치하지 않습니다. (남은 시도: " + remaining + "회)");
        }

        redisConfig.deleteValue(redisKey);
        redisConfig.deleteValue(failKey);

        String resetToken = UUID.randomUUID().toString();
        // 토큰에 대상 회원 아이디(id)를 바인딩하여 저장 (남의 비밀번호 변경 방지)
        redisConfig.setValue("TOKEN:RESET_PW:" + resetToken, id.trim(), TOKEN_EXPIRATION_MS);
        return resetToken;
    }

    /** 비밀번호 재설정 토큰 검증 및 소멸 (토큰에 바인딩된 회원 아이디 반환) */
    public String validateAndConsumeResetToken(String resetToken) {
        if (resetToken == null || resetToken.trim().isEmpty()) {
            return null;
        }
        String redisKey = "TOKEN:RESET_PW:" + resetToken.trim();
        String savedId = redisConfig.getValue(redisKey);
        if (savedId != null) {
            redisConfig.deleteValue(redisKey);
            return savedId;
        }
        return null;
    }

    /** 마이페이지 비밀번호 변경용 인증코드 발송 (계정 아이디 바인딩, 60초 쿨다운, 5분 유효) */
    public void sendMypagePasswordCode(String id, String email) {
        String cooldownKey = "VERIFY:MYPAGE_PW:COOLDOWN:" + id.trim();
        if (redisConfig.hasKey(cooldownKey)) {
            throw new ApiException(ErrorCode.EMAIL_SEND_COOLDOWN);
        }

        String code = generateVerificationCode();
        String subject = "[TAGO] 마이페이지 비밀번호 변경 인증번호 안내";
        String content = "안녕하세요, TAGO입니다.\n\n" +
                "회원님의 계정(" + id.trim() + ") 비밀번호 변경을 위한 인증번호는 다음과 같습니다.\n\n" +
                "인증번호: [" + code + "]\n\n" +
                "5분 이내에 인증번호를 입력해 주세요.\n감사합니다.";

        // 실제 메일 발송 먼저 수행 (실패 시 예외 발생)
        sendEmail(email, subject, content);

        // 발송 성공 시에만 Redis에 저장
        String redisKey = "VERIFY:MYPAGE_PW:CODE:" + id.trim();
        String failKey = "VERIFY:MYPAGE_PW:FAIL:" + id.trim();
        redisConfig.setValue(redisKey, code, CODE_EXPIRATION_MS);
        redisConfig.deleteValue(failKey);
        redisConfig.setValue(cooldownKey, "1", 60, TimeUnit.SECONDS);
    }

    /** 마이페이지 인증코드 검증 */
    public boolean verifyMypagePasswordCode(String id, String inputCode) {
        String redisKey = "VERIFY:MYPAGE_PW:CODE:" + id.trim();
        String failKey = "VERIFY:MYPAGE_PW:FAIL:" + id.trim();

        String savedCode = redisConfig.getValue(redisKey);
        if (savedCode == null) {
            throw new ApiException(ErrorCode.VERIFICATION_EXPIRED);
        }

        Long failCount = redisConfig.increment(failKey);
        if (failCount != null && failCount == 1) {
            redisConfig.expire(failKey, 5, TimeUnit.MINUTES);
        }

        if (failCount != null && failCount > 5) {
            redisConfig.deleteValue(redisKey);
            redisConfig.deleteValue(failKey);
            throw new ApiException(ErrorCode.VERIFICATION_ATTEMPTS_EXCEEDED);
        }

        if (!savedCode.equals(inputCode)) {
            int remaining = Math.max(0, 5 - (failCount != null ? failCount.intValue() : 0));
            throw new ApiException(ErrorCode.CODE_MISMATCH, "인증번호가 일치하지 않습니다. (남은 시도: " + remaining + "회)");
        }

        redisConfig.deleteValue(redisKey);
        redisConfig.deleteValue(failKey);

        // 인증 완료 플래그를 계정 ID에 저장 (10분 유효)
        redisConfig.setValue("VERIFIED:MYPAGE_PW:" + id.trim(), "true", TOKEN_EXPIRATION_MS);
        return true;
    }

    /** 마이페이지 비밀번호 변경 인증 여부 확인 및 소비 */
    public boolean consumeMypageVerification(String id) {
        String redisKey = "VERIFIED:MYPAGE_PW:" + id.trim();
        String verified = redisConfig.getValue(redisKey);
        if ("true".equals(verified)) {
            redisConfig.deleteValue(redisKey);
            return true;
        }
        return false;
    }

    /** 회원가입용 이메일 인증코드 발송 (60초 쿨다운, 5분 유효) */
    public void sendSignupVerificationCode(String email) {
        String cooldownKey = "VERIFY:SIGNUP:COOLDOWN:" + email;
        if (redisConfig.hasKey(cooldownKey)) {
            throw new ApiException(ErrorCode.EMAIL_SEND_COOLDOWN);
        }

        String code = generateVerificationCode();
        String subject = "[TAGO] 회원가입 이메일 인증번호 안내";
        String content = "안녕하세요, TAGO입니다.\n\n" +
                "회원가입을 위한 인증번호는 다음과 같습니다.\n\n" +
                "인증번호: [" + code + "]\n\n" +
                "5분 이내에 회원가입 화면에 인증번호를 입력해 주세요.\n감사합니다.";

        // 실제 메일 발송 먼저 수행 (실패 시 예외 발생)
        sendEmail(email, subject, content);

        // 발송 성공 시에만 Redis에 저장
        String codeKey = "VERIFY:SIGNUP:CODE:" + email;
        String failKey = "VERIFY:SIGNUP:FAIL:" + email;
        redisConfig.setValue(codeKey, code, CODE_EXPIRATION_MS);
        redisConfig.deleteValue(failKey);
        redisConfig.setValue(cooldownKey, "1", 60, TimeUnit.SECONDS);
    }

    /** 회원가입용 이메일 인증코드 검증 후 회원가입 토큰(15분 유효) 발급 */
    public String verifySignupVerificationCode(String email, String inputCode) {
        String codeKey = "VERIFY:SIGNUP:CODE:" + email;
        String failKey = "VERIFY:SIGNUP:FAIL:" + email;

        String savedCode = redisConfig.getValue(codeKey);
        if (savedCode == null) {
            throw new ApiException(ErrorCode.VERIFICATION_EXPIRED);
        }

        // 실패 횟수 체크 (5회 초과 시 코드 파기)
        Long failCount = redisConfig.increment(failKey);
        if (failCount != null && failCount == 1) {
            redisConfig.expire(failKey, 5, TimeUnit.MINUTES);
        }

        if (failCount != null && failCount > 5) {
            redisConfig.deleteValue(codeKey);
            redisConfig.deleteValue(failKey);
            throw new ApiException(ErrorCode.VERIFICATION_ATTEMPTS_EXCEEDED);
        }

        if (!savedCode.equals(inputCode)) {
            int remaining = Math.max(0, 5 - (failCount != null ? failCount.intValue() : 0));
            throw new ApiException(ErrorCode.CODE_MISMATCH, "인증번호가 일치하지 않습니다. (남은 시도: " + remaining + "회)");
        }

        // 인증 성공: 코드 및 실패 카운트 삭제, 가입 토큰 발급 (15분)
        redisConfig.deleteValue(codeKey);
        redisConfig.deleteValue(failKey);

        String signupToken = UUID.randomUUID().toString();
        redisConfig.setValue("TOKEN:SIGNUP:" + signupToken, email, 15 * 60 * 1000L);
        return signupToken;
    }

    /** 회원가입 토큰 검증 및 소멸 (회원가입 처리 시 1회 사용) */
    public boolean validateAndConsumeSignupToken(String signupToken, String email) {
        if (signupToken == null || signupToken.trim().isEmpty() || email == null) {
            return false;
        }
        String tokenKey = "TOKEN:SIGNUP:" + signupToken.trim();
        String savedEmail = redisConfig.getValue(tokenKey);
        if (savedEmail != null && savedEmail.equalsIgnoreCase(email.trim())) {
            redisConfig.deleteValue(tokenKey);
            return true;
        }
        return false;
    }
}
