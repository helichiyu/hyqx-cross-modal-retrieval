package com.hsmty.cmr.domain.evaluation.service;

import com.hsmty.cmr.common.id.BizNoGenerator;
import com.hsmty.cmr.common.id.SnowflakeIdWorker;
import com.hsmty.cmr.domain.evaluation.entity.CmrEvaluation;
import com.hsmty.cmr.domain.evaluation.mapper.EvaluationMapper;
import com.hsmty.cmr.domain.search.entity.CmrSearchHistory;
import com.hsmty.cmr.domain.search.mapper.ItemVoteMapper;
import com.hsmty.cmr.domain.search.mapper.SearchHistoryMapper;
import com.hsmty.cmr.domain.search.service.SearchService;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 评测退化判定：踩数上升 / 踩率 +5% / 结果数 −10% 任一命中即退化
 *
 * @author cmr
 * @since 2026-11-10
 */
class EvaluationServiceTest {

    private final EvaluationMapper evaluationMapper = mock(EvaluationMapper.class);
    private final SearchHistoryMapper historyMapper = mock(SearchHistoryMapper.class);
    private final ItemVoteMapper itemVoteMapper = mock(ItemVoteMapper.class);

    @Test
    void 踩数上升判退化() {
        stub(10, 1, 10, 3);

        Map<String, Object> detail = service().detail(1L);

        assertThat(detail.get("conclusion")).isEqualTo("degraded");
        assertThat((List<?>) detail.get("reasons")).anyMatch(r -> String.valueOf(r).contains("踩数上升"));
    }

    @Test
    void 结果数下降超一成判退化() {
        stub(10, 0, 8, 0);

        Map<String, Object> detail = service().detail(1L);

        assertThat(detail.get("conclusion")).isEqualTo("degraded");
        assertThat((List<?>) detail.get("reasons")).anyMatch(r -> String.valueOf(r).contains("结果数下降"));
        assertThat(detail.get("resultDropRatio")).isEqualTo(20.0);
    }

    @Test
    void 指标平稳判正常() {
        stub(10, 2, 10, 2);

        Map<String, Object> detail = service().detail(1L);

        assertThat(detail.get("conclusion")).isEqualTo("normal");
        assertThat((List<?>) detail.get("reasons")).isEmpty();
    }

    private void stub(int baseN, int baseDown, int replayN, int replayDown) {
        CmrEvaluation evaluation = new CmrEvaluation();
        evaluation.setId(1L);
        evaluation.setBaseSearchId(10L);
        evaluation.setBaseN(baseN);
        evaluation.setBaseDown(baseDown);
        when(evaluationMapper.selectOneById(1L)).thenReturn(evaluation);

        CmrSearchHistory base = new CmrSearchHistory();
        base.setId(10L);
        base.setItemCount(baseN);
        when(historyMapper.selectOneById(10L)).thenReturn(base);

        CmrSearchHistory replay = new CmrSearchHistory();
        replay.setId(20L);
        replay.setItemCount(replayN);
        when(historyMapper.selectListByQuery(any())).thenReturn(List.of(replay));
        when(itemVoteMapper.selectCountByQuery(any())).thenReturn((long) replayDown);
    }

    private EvaluationService service() {
        return new EvaluationService(evaluationMapper, historyMapper, itemVoteMapper, mock(SearchService.class),
                new SnowflakeIdWorker(1, 1), new BizNoGenerator());
    }
}
