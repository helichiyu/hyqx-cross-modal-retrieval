package com.hsmty.cmr.domain.auth.vo;

import lombok.Builder;
import lombok.Data;

import java.util.List;

/**
 * 当前用户信息
 *
 * @author cmr
 * @since 2026-11-03
 */
@Data
@Builder
public class UserVO {

    private String username;

    private String nickname;

    private String role;

    private List<String> permissions;
}
