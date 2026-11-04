package com.hsmty.cmr.domain.auth.vo;

import lombok.Builder;
import lombok.Data;

/**
 * 登录返回
 *
 * @author cmr
 * @since 2026-11-03
 */
@Data
@Builder
public class LoginVO {

    private String token;

    private UserVO user;
}
