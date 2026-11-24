package com.kedu.tago.member;

import com.kedu.tago.common.exception.ApiException;
import com.kedu.tago.common.exception.ErrorCode;
import com.kedu.tago.member.dto.MemberSignupRequestDTO;
import org.springframework.stereotype.Component;

import java.util.regex.Pattern;

/** 회원 가입 및 정보 수정 시 입력값 정밀 검증기 (EXC-1) */
@Component
public class MemberValidator {

    // 아이디: 영문/숫자 4~20자
    private static final Pattern ID_PATTERN = Pattern.compile("^[a-zA-Z0-9]{4,20}$");

    // 비밀번호: 8~64자, 영문, 숫자, 특수문자 각각 최소 1자 이상 포함
    private static final Pattern PW_COMPLEXITY_PATTERN = Pattern.compile(
            "^(?=.*[A-Za-z])(?=.*\\d)(?=.*[@$!%*#?&~^()_+=-])[A-Za-z\\d@$!%*#?&~^()_+=-]{8,64}$"
    );

    // 이메일: 표준 이메일 형식
    private static final Pattern EMAIL_PATTERN = Pattern.compile(
            "^[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,6}$"
    );

    // 휴대전화 번호 (숫자만 10~11자리, 01로 시작)
    private static final Pattern PHONE_PATTERN = Pattern.compile("^01[016789]\\d{7,8}$");

    // 이름: 한글/영문 2~30자
    private static final Pattern NAME_PATTERN = Pattern.compile("^[a-zA-Z가-힣\\s]{2,30}$");

    /** 회원가입 입력값 검증 */
    public void validateSignup(MemberSignupRequestDTO dto) {
        if (dto == null) {
            throw new ApiException(ErrorCode.BAD_REQUEST, "회원가입 요청 정보가 없습니다.");
        }

        // 1. 아이디 검증
        String id = dto.getId();
        if (id == null || id.trim().isEmpty()) {
            throw new ApiException(ErrorCode.BAD_REQUEST, "아이디를 입력해 주세요.");
        }
        if (!ID_PATTERN.matcher(id.trim()).matches()) {
            throw new ApiException(ErrorCode.BAD_REQUEST, "아이디는 4~20자의 영문 및 숫자만 사용 가능합니다.");
        }

        // 2. 비밀번호 검증
        String pw = dto.getPw();
        if (pw == null || pw.isEmpty()) {
            throw new ApiException(ErrorCode.BAD_REQUEST, "비밀번호를 입력해 주세요.");
        }
        if (pw.length() < 8 || pw.length() > 64) {
            throw new ApiException(ErrorCode.PASSWORD_TOO_SHORT, "비밀번호는 8자 이상 64자 이하로 입력해 주세요.");
        }
        if (!PW_COMPLEXITY_PATTERN.matcher(pw).matches()) {
            throw new ApiException(ErrorCode.BAD_REQUEST, "비밀번호는 영문, 숫자, 특수문자(@$!%*#?& 등)를 각각 최소 1자 이상 포함해야 합니다.");
        }

        // 3. 이름 검증
        String name = dto.getName();
        if (name == null || name.trim().isEmpty()) {
            throw new ApiException(ErrorCode.BAD_REQUEST, "이름을 입력해 주세요.");
        }
        if (!NAME_PATTERN.matcher(name.trim()).matches()) {
            throw new ApiException(ErrorCode.BAD_REQUEST, "이름은 2~30자의 한글 또는 영문이어야 합니다.");
        }

        // 4. 이메일 검증
        String email = dto.getEmail();
        if (email == null || email.trim().isEmpty()) {
            throw new ApiException(ErrorCode.BAD_REQUEST, "이메일을 입력해 주세요.");
        }
        if (!EMAIL_PATTERN.matcher(email.trim()).matches()) {
            throw new ApiException(ErrorCode.BAD_REQUEST, "올바른 이메일 형식을 입력해 주세요.");
        }

        // 5. 이메일 인증 토큰 검증
        String token = dto.getSignupToken();
        if (token == null || token.trim().isEmpty()) {
            throw new ApiException(ErrorCode.EMAIL_VERIFICATION_REQUIRED, "이메일 인증이 완료되지 않았습니다.");
        }

        // 6. 휴대전화 번호 검증 (숫자만 추출 후 검사)
        String phone = dto.getPhone();
        if (phone == null || phone.trim().isEmpty()) {
            throw new ApiException(ErrorCode.BAD_REQUEST, "휴대전화 번호를 입력해 주세요.");
        }
        String purePhone = phone.replaceAll("[^0-9]", "");
        if (!PHONE_PATTERN.matcher(purePhone).matches()) {
            throw new ApiException(ErrorCode.BAD_REQUEST, "올바른 휴대전화 번호 형식이 아닙니다. (예: 010-1234-5678)");
        }
        // 정제된 번호로 갱신
        dto.setPhone(purePhone);

        // 7. 주소 검증
        if (dto.getZipcode() == null || dto.getZipcode().trim().isEmpty() ||
            dto.getAddress1() == null || dto.getAddress1().trim().isEmpty()) {
            throw new ApiException(ErrorCode.BAD_REQUEST, "우편번호와 기본 주소를 모두 입력해 주세요.");
        }
    }
}
