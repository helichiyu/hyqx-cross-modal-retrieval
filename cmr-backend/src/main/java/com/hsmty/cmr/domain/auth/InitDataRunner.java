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

    /** 测试环境初始密码（统一 123456，不做首登强制改密） */
    public static final String INITIAL_PASSWORD = "123456";

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
        seedDemoStrategies();
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

    /**
     * P5 业务演示策略（对齐原型“4 条业务演示策略 + 系统默认兜底”）：按名称幂等，
     * 覆盖生效中 / 草稿 / 已下线三种状态，保证策略列表、变更评估、版本回滚有数据可点。
     */
    private void seedDemoStrategies() {
        seedPublishedStrategy("通用场景检索策略", "general",
                weights(40, 30, 15, 15), true, 20, "0.60", null);
        seedPublishedStrategy("安防场景检索策略", "security",
                weights(20, 40, 20, 20), true, 30, "0.65", null);
        seedDraftStrategy("巡检场景检索策略", "inspection",
                weights(30, 30, 20, 20), true, 20, "0.55");
        seedPublishedStrategy("长尾实验策略", "general",
                weights(50, 30, 10, 10), false, 10, "0.70", "poor");
    }

    private void seedPublishedStrategy(String name, String scene, Map<String, Integer> weights,
                                       boolean rerank, int topK, String threshold, String offlineReason) {
        if (strategyExists(name)) {
            return;
        }
        boolean offline = offlineReason != null;
        CmrStrategy strategy = insertStrategy(name, scene,
                offline ? CmrStrategy.STATUS_OFFLINE : CmrStrategy.STATUS_ACTIVE, 1,
                offline ? "演示策略：已下线（效果不佳）" : "演示策略：" + scene + " 场景参数集");
        if (offline) {
            strategy.setOfflineReason(offlineReason);
            strategy.setOfflineTime(LocalDateTime.now());
            strategyMapper.update(strategy);
        }
        insertVersion(strategy, 1, CmrStrategyVersion.STATUS_PUBLISHED,
                weights, rerank, topK, threshold, LocalDateTime.now());
        log.info("演示策略已播种: {} scene={} status={}", strategy.getBizNo(), scene, strategy.getStatus());
    }

    private void seedDraftStrategy(String name, String scene, Map<String, Integer> weights,
                                   boolean rerank, int topK, String threshold) {
        if (strategyExists(name)) {
            return;
        }
        CmrStrategy strategy = insertStrategy(name, scene, CmrStrategy.STATUS_DRAFT, 0,
                "演示策略：草稿待发布");
        insertVersion(strategy, 0, CmrStrategyVersion.STATUS_DRAFT,
                weights, rerank, topK, threshold, null);
        log.info("演示草稿策略已播种: {} scene={}", strategy.getBizNo(), scene);
    }

    private boolean strategyExists(String name) {
        Long exists = strategyMapper.selectCountByQuery(QueryWrapper.create()
                .where(CMR_STRATEGY.NAME.eq(name)));
        return exists != null && exists > 0;
    }

    private CmrStrategy insertStrategy(String name, String scene, String status,
                                       int currentVersion, String remark) {
        CmrStrategy strategy = new CmrStrategy();
        strategy.setId(idWorker.next());
        strategy.setBizNo(bizNoGenerator.next(BizNoGenerator.PREFIX_STRATEGY));
        strategy.setName(name);
        strategy.setScene(scene);
        strategy.setStatus(status);
        strategy.setPreset(false);
        strategy.setCurrentVersion(currentVersion);
        strategy.setRemark(remark);
        strategy.setDelFlag("F");
        strategy.setCreateTime(LocalDateTime.now());
        strategyMapper.insert(strategy, true);
        return strategy;
    }

    private void insertVersion(CmrStrategy strategy, int versionNo, String status,
                               Map<String, Integer> weights, boolean rerank, int topK,
                               String threshold, LocalDateTime publishedTime) {
        CmrStrategyVersion version = new CmrStrategyVersion();
        version.setId(idWorker.next());
        version.setStrategyId(strategy.getId());
        version.setVersionNo(versionNo);
        version.setWeightsJson(com.hsmty.cmr.common.util.JsonUtils.write(weights));
        version.setRerank(rerank);
        version.setTopK(topK);
        version.setThreshold(new java.math.BigDecimal(threshold));
        version.setStatus(status);
        version.setSource("new");
        version.setPublishedTime(publishedTime);
        version.setDelFlag("F");
        version.setCreateTime(LocalDateTime.now());
        strategyVersionMapper.insert(version, true);
    }

    /** 四模态权重，按原型固定顺序 text/image/audio/video */
    private Map<String, Integer> weights(int text, int image, int audio, int video) {
        Map<String, Integer> weights = new java.util.LinkedHashMap<>();
        weights.put("text", text);
        weights.put("image", image);
        weights.put("audio", audio);
        weights.put("video", video);
        return weights;
    }
}
