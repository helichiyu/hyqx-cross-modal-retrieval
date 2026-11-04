package com.hsmty.cmr.domain.auth;

import com.hsmty.cmr.common.auth.AuthContext;
import com.hsmty.cmr.common.auth.JwtService;
import com.hsmty.cmr.common.auth.LoginUser;
import com.hsmty.cmr.common.auth.Permissions;
import com.hsmty.cmr.common.exception.BizException;
import com.hsmty.cmr.domain.auth.dto.LoginDTO;
import com.hsmty.cmr.domain.auth.entity.CmrUser;
import com.hsmty.cmr.domain.auth.mapper.UserMapper;
import com.hsmty.cmr.domain.auth.vo.LoginVO;
import com.hsmty.cmr.domain.auth.vo.UserVO;
import com.mybatisflex.core.query.QueryWrapper;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import static com.hsmty.cmr.domain.auth.entity.table.CmrUserTableDef.CMR_USER;

/**
 * 认证服务：账号密码登录（BCrypt）、当前用户、登出黑名单
 *
 * @author cmr
 * @since 2026-11-03
 */
@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserMapper userMapper;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    public LoginVO login(LoginDTO dto) {
        CmrUser user = userMapper.selectOneByQuery(QueryWrapper.create()
                .where(CMR_USER.USERNAME.eq(dto.getUsername())));
        BizException.assertNull(user, "用户名或密码错误");
        BizException.assertTrue(!passwordEncoder.matches(dto.getPassword(), user.getPassword()), "用户名或密码错误");
        BizException.assertTrue(!"1".equals(user.getStatus()), "账号已停用");

        LoginUser loginUser = new LoginUser(user.getId(), user.getUsername(), user.getNickname(), user.getRole());
        return LoginVO.builder()
                .token(jwtService.issue(loginUser).token())
                .user(toUserVO(loginUser))
                .build();
    }

    public UserVO me() {
        LoginUser user = AuthContext.get();
        BizException.assertNull(user, "未登录");
        return toUserVO(user);
    }

    /** 登出：令牌剩余生命周期内黑名单失效 */
    public void logout(HttpServletRequest request) {
        String header = request.getHeader("Authorization");
        if (header != null && header.startsWith("Bearer ")) {
            jwtService.revokeByToken(header.substring(7));
        }
    }

    private UserVO toUserVO(LoginUser user) {
        return UserVO.builder()
                .username(user.username())
                .nickname(user.nickname())
                .role(user.role())
                .permissions(Permissions.of(user.role()))
                .build();
    }
}
