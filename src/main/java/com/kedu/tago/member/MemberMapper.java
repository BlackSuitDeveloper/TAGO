package com.kedu.tago.member;

import com.kedu.tago.member.dto.MemberProfileResponseDTO;
import com.kedu.tago.member.dto.MemberSignupRequestDTO;
import com.kedu.tago.member.dto.MemberUpdateRequestDTO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface MemberMapper {

    int isMember(@Param("id") String id, @Param("pw") String pw);

    int checkId(@Param("id") String id);

    int insertMember(MemberSignupRequestDTO dto);

    MemberProfileResponseDTO selectMember(@Param("id") String id);

    int updateMember(MemberUpdateRequestDTO dto);

    int deleteId(@Param("id") String id);
}
