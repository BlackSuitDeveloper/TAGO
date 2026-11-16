package com.kedu.tago.member;

import org.mybatis.spring.SqlSessionTemplate;
import org.springframework.stereotype.Repository;

import java.util.HashMap;
import java.util.Map;

@Repository
public class MemberDAO {

    private SqlSessionTemplate mybatis;

    public MemberDAO(SqlSessionTemplate mybatis) {
        this.mybatis = mybatis;
    }

    // 로그인
    public boolean isMember(String id, String pw) {
        Map<String, String> params = new HashMap<>();
        params.put("id", id);
        params.put("pw", pw);

        Integer count = mybatis.selectOne(
                "member.MemberDAO.isMember", params
        );

        return count != null && count > 0;
    }

    // 아이디 중복 체크
    public int checkId(String id) {
        return mybatis.selectOne("member.MemberDAO.checkId", id);
    }

    // 회원가입
    public int insertMember(MemberDTO dto) {
        return mybatis.insert("member.MemberDAO.insertMember", dto);
    }

    // 회원 정보 조회
    public MemberDTO selectMember(String id) {
        return mybatis.selectOne("member.MemberDAO.selectMember",id);
    }

    // 회원 정보 수정
    public int updateMember(MemberDTO dto) {
        return mybatis.update("member.MemberDAO.updateMember", dto);
    }

    // 회원 탈퇴
    public int deleteId(String id) {
        return  mybatis.delete("member.MemberDAO.deleteId", id);
    }
}
