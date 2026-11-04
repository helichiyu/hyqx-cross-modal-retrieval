package com.hsmty.cmr.domain.auth;

import com.hsmty.cmr.common.api.ApiResult;
import com.hsmty.cmr.domain.auth.dto.LoginDTO;
import com.hsmty.cmr.domain.auth.vo.LoginVO;
import com.hsmty.cmr.domain.auth.vo.UserVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 认证接口：登录/当前用户/登出
 *
 * @author cmr
 * @since 2026-11-03
 */
@Tag(name = "认证")
@RestController
@RequestMapping("auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @Operation(summary = "登录")
    @PostMapping("login")
    public ApiResult<LoginVO> login(@Valid @RequestBody LoginDTO dto) {
        return ApiResult.success(authService.login(dto));
    }

    @Operation(summary = "当前用户（含权限码）")
    @GetMapping("me")
    public ApiResult<UserVO> me() {
        return ApiResult.success(authService.me());
    }

    @Operation(summary = "登出")
    @PostMapping("logout")
    public ApiResult<Void> logout(HttpServletRequest request) {
        authService.logout(request);
        return ApiResult.success();
    }
}
