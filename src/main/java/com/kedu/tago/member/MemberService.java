package com.kedu.tago.member;

import com.kedu.tago.common.security.Bcrypt;
import com.kedu.tago.member.dto.MemberProfileResponseDTO;
import com.kedu.tago.member.dto.MemberSignupRequestDTO;
import com.kedu.tago.member.dto.MemberUpdateRequestDTO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class MemberService {

    private final MemberMapper memberMapper;
    private final Bcrypt bcrypt;

    // 로그인
    public boolean isMember(String id, String rawPw) {
        String encodedPw = memberMapper.getPasswordById(id);
        if (encodedPw == null) {
            return false;
        }
        return bcrypt.checkPassword(rawPw, encodedPw);
    }

    // 회원 권한 조회 (없거나 모르는 값이면 USER)
    public Role getRole(String id) {
        return Role.from(memberMapper.getRoleById(id));
    }

    // 아이디 중복 체크
    public int checkId(String id) {
        return memberMapper.checkId(id);
    }

    // 회원가입
    public int insertMember(MemberSignupRequestDTO dto) {
        String encodedPw = bcrypt.hashPassword(dto.getPw());
        dto.setPw(encodedPw);
        return memberMapper.insertMember(dto);
    }

    // 회원 정보 조회
    public MemberProfileResponseDTO selectMember(String id) {
        return memberMapper.selectMember(id);
    }

    // 회원 정보 수정
    public int updateMember(MemberUpdateRequestDTO dto) {
        return memberMapper.updateMember(dto);
    }

    // 회원 탈퇴
    public int deleteId(String id) {
        return memberMapper.deleteId(id);
    }

    // 이름과 이메일로 아이디 찾기
    public String findIdByNameAndEmail(String name, String email) {
        return memberMapper.findIdByNameAndEmail(name, email);
    }

    // 아이디와 이메일 일치 검증
    public boolean validateMemberByIdAndEmail(String id, String email) {
        return memberMapper.countByIdAndEmail(id, email) > 0;
    }

    // 새 비밀번호 변경 (BCrypt 암호화 적용)
    public boolean updatePassword(String id, String newRawPw) {
        String encodedPw = bcrypt.hashPassword(newRawPw);
        return memberMapper.updatePassword(id, encodedPw) > 0;
    }
}
