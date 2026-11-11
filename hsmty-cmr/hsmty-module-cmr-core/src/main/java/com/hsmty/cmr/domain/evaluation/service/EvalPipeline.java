package com.hsmty.cmr.domain.evaluation.service;

import tools.jackson.databind.JsonNode;
import com.hsmty.cmr.common.exception.BizException;
import com.hsmty.cmr.common.util.JsonUtils;
import com.hsmty.cmr.config.CmrProperties;
import com.hsmty.cmr.domain.evaluation.entity.CmrEvalRecord;
import com.hsmty.cmr.domain.evaluation.mapper.EvalRecordMapper;
import com.hsmty.cmr.domain.task.TaskService;
import com.hsmty.cmr.domain.task.entity.CmrTask;
import com.hsmty.cmr.domain.task.entity.CmrTaskLog;
import com.hsmty.cmr.operator.OpIds;
import com.hsmty.cmr.operator.OperatorClient;
import com.mybatisflex.core.query.QueryWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static com.hsmty.cmr.domain.evaluation.entity.table.CmrEvalRecordTableDef.CMR_EVAL_RECORD;

/**
 * 评测管线：写评测文件 → 409 执行 → 按配置验收线判定结论 → 指标/对比/逐条明细落库
 *
 * @author cmr
 * @since 2026-11-03
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EvalPipeline {

    private final OperatorClient operatorClient;
    private final TaskService taskService;
    private final EvalRecordMapper evalRecordMapper;
    private final com.hsmty.cmr.domain.strategy.mapper.StrategyVersionMapper strategyVersionMapper;
    private final CmrProperties properties;

    /** 执行评测链（由 TaskExecutor 调度） */
    public void run(CmrTask task, Long evalRecordId, String evalFile, String indexDir,
                    Long targetStrategyVersionId) {
        CmrEvalRecord record = evalRecordMapper.selectOneById(evalRecordId);
        BizException.assertNull(record, "评测记录不存在: " + evalRecordId);

        taskService.updateStep(task, "eval");
        taskService.log(task.getId(), "eval", CmrTaskLog.STATUS_START, null,
                "evalFile=" + evalFile + " indexDir=" + indexDir);

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("uniqueId", task.getBizNo());
        payload.put("indexDir", indexDir);
        payload.put("evalFile", evalFile);
        if (targetStrategyVersionId != null) {
            com.hsmty.cmr.domain.strategy.entity.CmrStrategyVersion v =
                    strategyVersionMapper.selectOneById(targetStrategyVersionId);
            BizException.assertNull(v, "评测对象策略版本不存在: " + targetStrategyVersionId);
            payload.put("weights", JsonUtils.read(v.getWeightsJson(),
                    new tools.jackson.core.type.TypeReference<Map<String, Integer>>() {
                    }));
            payload.put("rerank", Boolean.TRUE.equals(v.getRerank()));
        }

        JsonNode data;
        try {
            data = operatorClient.predict(OpIds.EVALUATE, payload);
        } catch (Exception e) {
            taskService.log(task.getId(), "eval", CmrTaskLog.STATUS_FAIL, null, e.getMessage());
            throw e instanceof RuntimeException re ? re : new BizException(e.getMessage(), e);
        }

        // 验收线判定（配置驱动，G3 兜底 recall 0.8）
        Map<String, Object> lines = new LinkedHashMap<>();
        List<String> failed = new ArrayList<>();
        JsonNode metrics = data.path("metrics");
        for (String key : List.of("recall@10", "precision@10", "ndcg@10")) {
            BigDecimal line = switch (key) {
                case "recall@10" -> properties.getEval().getLines().getRecall();
                case "precision@10" -> properties.getEval().getLines().getPrecision();
                default -> properties.getEval().getLines().getNdcg();
            };
            if (line == null) {
                continue;
            }
            lines.put(key, line);
            BigDecimal actual = metrics.path(key).isNumber() ? metrics.path(key).decimalValue() : BigDecimal.ZERO;
            if (actual.compareTo(line) < 0) {
                failed.add(key + " " + actual + " < " + line);
            }
        }

        record.setMetricsJson(JsonUtils.writeQuietly(metrics));
        record.setLinesJson(JsonUtils.writeQuietly(lines));
        record.setConclusion(failed.isEmpty() ? "pass" : "fail");
        record.setFailedLinesJson(failed.isEmpty() ? null : JsonUtils.writeQuietly(failed));
        record.setComparisonJson(JsonUtils.writeQuietly(data.path("comparison")));
        record.setPerQueryJson(JsonUtils.writeQuietly(data.path("perQuery")));
        evalRecordMapper.update(record);
        taskService.log(task.getId(), "eval", CmrTaskLog.STATUS_OK, data,
                "评测完成 conclusion=" + record.getConclusion());
        log.info("评测完成: {} conclusion={} metrics={}", record.getBizNo(), record.getConclusion(), metrics);
    }

    /** 上一次同索引评测（供前端对比展示；409 侧亦自动对比） */
    public CmrEvalRecord lastRecordOfIndex(Long indexId, Long excludeId) {
        return evalRecordMapper.selectOneByQuery(QueryWrapper.create()
                .where(CMR_EVAL_RECORD.INDEX_ID.eq(indexId))
                .and(CMR_EVAL_RECORD.ID.ne(excludeId))
                .orderBy(CMR_EVAL_RECORD.ID.desc())
                .limit(1));
    }
}
