package com.kedu.tago.member;

import org.springframework.stereotype.Service;

@Service
public class MemberService {

    private MemberDAO mDao;

    MemberService(MemberDAO mDao) {
        this.mDao = mDao;
    }

    // 로그인
    public boolean isMember(String id, String pw) {
        return mDao.isMember(id, pw);
    }

    // 아이디 중복 체크
    public int checkId(String id) {
        return mDao.checkId(id);
    }

    // 회원가입
    public int insertMember(MemberDTO dto) {
        return mDao.insertMember(dto);
    }

    // 회원 정보 조회
    public MemberDTO selectMember(String id) {
        return mDao.selectMember(id);
    }

    // 회원 정보 수정
    public int updateMember(MemberDTO dto) {
        return mDao.updateMember(dto);
    }

    // 회원 탈퇴
    public int deleteId(String id) {
        return mDao.deleteId(id);
    }
}
