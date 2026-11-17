package com.kedu.tago.member;

import com.kedu.tago.member.dto.MemberProfileResponseDTO;
import com.kedu.tago.member.dto.MemberSignupRequestDTO;
import com.kedu.tago.member.dto.MemberUpdateRequestDTO;
import org.springframework.stereotype.Service;

@Service
public class MemberService {

    private final MemberMapper memberMapper;

    public MemberService(MemberMapper memberMapper) {
        this.memberMapper = memberMapper;
    }

    // 로그인
    public boolean isMember(String id, String pw) {
        return memberMapper.isMember(id, pw) > 0;
    }

    // 아이디 중복 체크
    public int checkId(String id) {
        return memberMapper.checkId(id);
    }

    // 회원가입
    public int insertMember(MemberSignupRequestDTO dto) {
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
