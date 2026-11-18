package com.kedu.tago.member;

import com.kedu.tago.common.utils.Bcrypt;
import com.kedu.tago.member.dto.MemberProfileResponseDTO;
import com.kedu.tago.member.dto.MemberSignupRequestDTO;
import com.kedu.tago.member.dto.MemberUpdateRequestDTO;
import org.springframework.stereotype.Service;

@Service
public class MemberService {

    private final MemberMapper memberMapper;
    private final Bcrypt bcrypt;

    public MemberService(MemberMapper memberMapper, Bcrypt bcrypt) {
        this.memberMapper = memberMapper;
        this.bcrypt = bcrypt;
    }

    // 로그인
    public boolean isMember(String id, String rawPw) {
        String encodedPw = memberMapper.getPasswordById(id);
        if (encodedPw == null) {
            return false;
        }
        return bcrypt.checkPassword(rawPw, encodedPw);
    }

    // 아이디 중복 체크
    public int checkId(String id) {
        return memberMapper.checkId(id);
    }

    // 회원가입
    public int insertMember(MemberSignupRequestDTO dto) {
        String encoedPw = bcrypt.hashPassword(dto.getPw());
        dto.setPw(encoedPw);
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
}
