package com.hsmty.cmr.domain.auth;

import com.hsmty.cmr.common.auth.AuthContext;
import com.hsmty.cmr.common.auth.LoginUser;
import com.hsmty.cmr.common.auth.Permissions;
import com.hsmty.cmr.domain.auth.vo.UserVO;
import com.hsmty.core.common.entity.ApiResult;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 当前用户接口。
 *
 * <p>登录与登出由平台 UAP 统一负责，子应用不再提供账号密码登录；
 * 这里只回显当前用户与权限码，供前端渲染菜单与按钮。
 *
 * @author cmr
 * @since 2026-11-03
 */
@Tag(name = "认证")
@RestController
@RequestMapping("auth")
@RequiredArgsConstructor
public class AuthController {

    /**
     * 当前用户（含权限码）。
     *
     * @return 当前用户信息
     */
    @Operation(summary = "当前用户（含权限码）")
    @GetMapping("me")
    public ApiResult<UserVO> me() {
        LoginUser user = AuthContext.get();
        if (user == null) {
            return ApiResult.success(UserVO.builder()
                    .username("anonymous")
                    .nickname("anonymous")
                    .role(Permissions.ROLE_USER)
                    .permissions(List.of())
                    .build());
        }
        List<String> permissions = AuthContext.permissions();
        return ApiResult.success(UserVO.builder()
                .username(user.username())
                .nickname(user.nickname())
                .role(user.role())
                .permissions(permissions.isEmpty() ? Permissions.of(user.role()) : permissions)
                .build());
    }
}
