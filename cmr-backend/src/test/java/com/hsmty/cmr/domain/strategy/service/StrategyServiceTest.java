package com.hsmty.cmr.domain.strategy.service;

import com.hsmty.cmr.common.exception.BizException;
import com.hsmty.cmr.common.id.BizNoGenerator;
import com.hsmty.cmr.common.id.SnowflakeIdWorker;
import com.hsmty.cmr.config.CmrProperties;
import com.hsmty.cmr.domain.dataset.mapper.IndexMapper;
import com.hsmty.cmr.domain.strategy.entity.CmrStrategy;
import com.hsmty.cmr.domain.strategy.mapper.StrategyMapper;
import com.hsmty.cmr.domain.strategy.mapper.StrategyVersionMapper;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 策略生命周期：下线策略不参与检索、系统预置策略不可下线
 *
 * @author cmr
 * @since 2026-11-10
 */
class StrategyServiceTest {

    private final StrategyMapper strategyMapper = mock(StrategyMapper.class);

    @Test
    void 已下线策略不可用于检索() {
        CmrStrategy offline = strategy(1L, CmrStrategy.STATUS_OFFLINE);
        when(strategyMapper.selectOneById(1L)).thenReturn(offline);

        assertThatThrownBy(() -> service().effectiveById(1L))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("策略未生效");
    }

    @Test
    void 系统预置策略不可下线() {
        CmrStrategy preset = strategy(2L, CmrStrategy.STATUS_ACTIVE);
        preset.setPreset(true);
        when(strategyMapper.selectOneById(2L)).thenReturn(preset);

        assertThatThrownBy(() -> service().offline(2L, "tuning"))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("系统预置策略不可下线");
    }

    @Test
    void 下线记录原因与时间() {
        CmrStrategy active = strategy(3L, CmrStrategy.STATUS_ACTIVE);
        when(strategyMapper.selectOneById(3L)).thenReturn(active);

        service().offline(3L, "poor");

        assertThat(active.getStatus()).isEqualTo(CmrStrategy.STATUS_OFFLINE);
        assertThat(active.getOfflineReason()).isEqualTo("poor");
        assertThat(active.getOfflineTime()).isNotNull();
        verify(strategyMapper).update(active);
    }

    private StrategyService service() {
        return new StrategyService(strategyMapper, mock(StrategyVersionMapper.class), mock(IndexMapper.class),
                new SnowflakeIdWorker(1, 1), new BizNoGenerator(), new CmrProperties());
    }

    private CmrStrategy strategy(Long id, String status) {
        CmrStrategy strategy = new CmrStrategy();
        strategy.setId(id);
        strategy.setBizNo("STR-" + id);
        strategy.setName("策略" + id);
        strategy.setStatus(status);
        strategy.setPreset(false);
        return strategy;
    }
}
