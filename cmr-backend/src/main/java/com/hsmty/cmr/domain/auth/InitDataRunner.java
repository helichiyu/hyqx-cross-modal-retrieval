package com.hsmty.cmr.domain.auth;

import com.hsmty.cmr.common.id.BizNoGenerator;
import com.hsmty.cmr.common.id.SnowflakeIdWorker;
import com.hsmty.cmr.domain.auth.entity.CmrUser;
import com.hsmty.cmr.domain.auth.mapper.UserMapper;
import com.hsmty.cmr.domain.strategy.entity.CmrStrategy;
import com.hsmty.cmr.domain.strategy.entity.CmrStrategyVersion;
import com.hsmty.cmr.domain.strategy.mapper.StrategyMapper;
import com.hsmty.cmr.domain.strategy.mapper.StrategyVersionMapper;
import com.mybatisflex.core.query.QueryWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.Map;

import static com.hsmty.cmr.domain.auth.entity.table.CmrUserTableDef.CMR_USER;
import static com.hsmty.cmr.domain.strategy.entity.table.CmrStrategyTableDef.CMR_STRATEGY;

/**
 * 初始化数据播种（幂等）：三演示账号 + 系统默认策略（02 库表设计初始化清单 3/4 项）
 *
 * @author cmr
 * @since 2026-11-03
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class InitDataRunner implements ApplicationRunner {

    /** 初始密码，首登必改（交付文档注明） */
    public static final String INITIAL_PASSWORD = "Cmr@2026";

    private final UserMapper userMapper;
    private final StrategyMapper strategyMapper;
    private final StrategyVersionMapper strategyVersionMapper;
    private final PasswordEncoder passwordEncoder;
    private final SnowflakeIdWorker idWorker;
    private final BizNoGenerator bizNoGenerator;

    @Override
    public void run(ApplicationArguments args) {
        seedUser("admin", "管理员", "admin");
        seedUser("operator", "运营人员", "operator");
        seedUser("user", "检索用户", "user");
        seedDefaultStrategy();
    }

    private void seedUser(String username, String nickname, String role) {
        Long exists = userMapper.selectCountByQuery(QueryWrapper.create()
                .where(CMR_USER.USERNAME.eq(username)));
        if (exists != null && exists > 0) {
            return;
        }
        CmrUser user = new CmrUser();
        user.setId(idWorker.next());
        user.setUsername(username);
        user.setPassword(passwordEncoder.encode(INITIAL_PASSWORD));
        user.setNickname(nickname);
        user.setRole(role);
        user.setStatus("1");
        user.setDelFlag("F");
        user.setCreateTime(LocalDateTime.now());
        userMapper.insert(user, true);
        log.info("内置账号已播种: {} ({})", username, role);
    }

    /** 系统默认策略：scene=all，weights 25/25/25/25，直接 published v1 生效 */
    private void seedDefaultStrategy() {
        Long exists = strategyMapper.selectCountByQuery(QueryWrapper.create()
                .where(CMR_STRATEGY.SCENE.eq("all")));
        if (exists != null && exists > 0) {
            return;
        }
        CmrStrategy strategy = new CmrStrategy();
        strategy.setId(idWorker.next());
        strategy.setBizNo(bizNoGenerator.next(BizNoGenerator.PREFIX_STRATEGY));
        strategy.setName("系统默认策略");
        strategy.setScene("all");
        strategy.setStatus(CmrStrategy.STATUS_ACTIVE);
        strategy.setCurrentVersion(1);
        strategy.setRemark("初始化默认策略：权重 25/25/25/25，关闭精排");
        strategy.setDelFlag("F");
        strategy.setCreateTime(LocalDateTime.now());
        strategyMapper.insert(strategy, true);

        CmrStrategyVersion version = new CmrStrategyVersion();
        version.setId(idWorker.next());
        version.setStrategyId(strategy.getId());
        version.setVersionNo(1);
        version.setWeightsJson(com.hsmty.cmr.common.util.JsonUtils.write(
                Map.of("text", 25, "image", 25, "audio", 25, "video", 25)));
        version.setRerank(false);
        version.setTopK(20);
        version.setStatus(CmrStrategyVersion.STATUS_PUBLISHED);
        version.setSource("new");
        version.setPublishedTime(LocalDateTime.now());
        version.setDelFlag("F");
        version.setCreateTime(LocalDateTime.now());
        strategyVersionMapper.insert(version, true);
        log.info("系统默认策略已播种: {}", strategy.getBizNo());
    }
}
