package com.kedu.tago.member;

import com.kedu.tago.member.dto.MemberProfileResponseDTO;
import com.kedu.tago.member.dto.MemberSignupRequestDTO;
import com.kedu.tago.member.dto.MemberUpdateRequestDTO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/** 회원 매퍼 */
@Mapper
public interface MemberMapper {

    /** 아이디·비밀번호 일치 건수 */
    int isMember(@Param("id") String id, @Param("pw") String pw);

    /** 아이디 중복 건수 */
    int checkId(@Param("id") String id);

    /** 이메일 중복 건수 */
    int checkEmail(@Param("email") String email);

    /** 회원 등록 */
    int insertMember(MemberSignupRequestDTO dto);

    /** 회원 정보 조회 */
    MemberProfileResponseDTO selectMember(@Param("id") String id);

    /** 회원 정보 수정 */
    int updateMember(MemberUpdateRequestDTO dto);

    /** 회원 삭제 */
    int deleteId(@Param("id") String id);

    /** 저장된 비밀번호 해시 조회 */
    String getPasswordById(String id);

    /** 회원 권한 조회 */
    String getRoleById(@Param("id") String id);

    /** 이름과 이메일로 아이디 조회 */
    String findIdByNameAndEmail(@Param("name") String name, @Param("email") String email);

    /** 아이디와 이메일 일치 건수 */
    int countByIdAndEmail(@Param("id") String id, @Param("email") String email);

    /** 비밀번호 변경 */
    int updatePassword(@Param("id") String id, @Param("pw") String pw);
}
