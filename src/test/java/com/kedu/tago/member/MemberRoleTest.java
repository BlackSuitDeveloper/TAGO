package com.kedu.tago.member;

import com.kedu.tago.common.utils.Bcrypt;
import com.kedu.tago.member.dto.MemberSignupRequestDTO;
import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.session.Configuration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.InputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** 회원 권한(ROLE) 조회와 매퍼 SQL 검증 (DB 없이) */
class MemberRoleTest {

    private final MemberMapper mapper = mock(MemberMapper.class);
    private final MemberService service = new MemberService(mapper, mock(Bcrypt.class));

    @Test
    @DisplayName("getRole: DB 값을 Role 로 변환, 없거나 모르는 값은 USER")
    void getRole() {
        when(mapper.getRoleById("admin")).thenReturn("ADMIN");
        when(mapper.getRoleById("user")).thenReturn("USER");
        when(mapper.getRoleById("ghost")).thenReturn(null);
        when(mapper.getRoleById("weird")).thenReturn("SUPER");

        assertEquals(Role.ADMIN, service.getRole("admin"));
        assertEquals(Role.USER, service.getRole("user"));
        assertEquals(Role.USER, service.getRole("ghost"));
        assertEquals(Role.USER, service.getRole("weird"));
    }

    private Configuration loadMapperXml() throws Exception {
        Configuration c = new Configuration();
        try (InputStream in = getClass().getClassLoader().getResourceAsStream("mapper/member_mapper.xml")) {
            new XMLMapperBuilder(in, c, "mapper/member_mapper.xml", c.getSqlFragments()).parse();
        }
        return c;
    }

    @Test
    @DisplayName("매퍼 XML: 권한 조회 쿼리가 있고, 가입 시 role 은 항상 USER 로 저장된다 (요청으로 ADMIN 지정 불가)")
    void mapperSql() throws Exception {
        Configuration c = loadMapperXml();
        assertTrue(c.hasStatement("com.kedu.tago.member.MemberMapper.getRoleById"));

        MappedStatement insert = c.getMappedStatement("com.kedu.tago.member.MemberMapper.insertMember");
        String sql = insert.getBoundSql(MemberSignupRequestDTO.builder().id("x").pw("p").build()).getSql().replaceAll("\\s+", " ");
        assertTrue(sql.contains("role"), sql);
        assertTrue(sql.contains("'USER'"), sql);

        MappedStatement profile = c.getMappedStatement("com.kedu.tago.member.MemberMapper.selectMember");
        assertTrue(profile.getBoundSql("x").getSql().contains("role"));
    }
}
