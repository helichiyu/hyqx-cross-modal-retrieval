package com.hsmty.cmr.domain.task;

import com.hsmty.cmr.common.util.JsonUtils;
import com.hsmty.cmr.domain.dataset.entity.CmrDataset;
import com.hsmty.cmr.domain.dataset.mapper.DatasetMapper;
import com.hsmty.cmr.domain.dataset.service.BuildPipeline;
import com.hsmty.cmr.domain.evaluation.entity.CmrEvalRecord;
import com.hsmty.cmr.domain.evaluation.mapper.EvalRecordMapper;
import com.hsmty.cmr.domain.evaluation.service.EvalPipeline;
import com.hsmty.cmr.domain.task.entity.CmrTask;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * 开机恢复：崩溃/重启时仍在 running/ready 的 build/eval 任务重新入队（算子幂等，全链重跑）
 *
 * @author cmr
 * @since 2026-11-03
 */
@Slf4j
@Order(10)
@Component
@RequiredArgsConstructor
public class BootResumeRunner implements ApplicationRunner {

    private final TaskService taskService;
    private final TaskExecutor taskExecutor;
    private final DatasetMapper datasetMapper;
    private final EvalRecordMapper evalRecordMapper;
    private final BuildPipeline buildPipeline;
    private final EvalPipeline evalPipeline;

    @Override
    public void run(ApplicationArguments args) {
        List<CmrTask> resumable = taskService.findResumable();
        for (CmrTask task : resumable) {
            Map<String, Object> payload = JsonUtils.read(
                    task.getPayloadJson() == null ? "{}" : task.getPayloadJson(),
                    new tools.jackson.core.type.TypeReference<Map<String, Object>>() {
                    });
            Long refId = payload.get("datasetId") instanceof Number n ? n.longValue() : task.getRefId();
            if (CmrTask.TYPE_BUILD.equals(task.getType())) {
                CmrDataset dataset = datasetMapper.selectOneById(refId);
                if (dataset == null) {
                    log.warn("恢复跳过：数据集不存在 task={}", task.getBizNo());
                    continue;
                }
                task.setRetryCount((task.getRetryCount() == null ? 0 : task.getRetryCount()) + 1);
                taskExecutor.submit(task, t -> buildPipeline.run(t, dataset.getId()));
                log.info("建库任务已恢复: {}", task.getBizNo());
            } else if (CmrTask.TYPE_EVAL.equals(task.getType())) {
                Long recordId = payload.get("evalRecordId") instanceof Number n ? n.longValue() : task.getRefId();
                CmrEvalRecord record = evalRecordMapper.selectOneById(recordId);
                if (record == null) {
                    log.warn("恢复跳过：评测记录不存在 task={}", task.getBizNo());
                    continue;
                }
                String evalFile = payload.get("evalFile") instanceof String s ? s : null;
                String indexDir = payload.get("indexDir") instanceof String s ? s : null;
                Long targetVersionId = record.getStrategyVersionId();
                task.setRetryCount((task.getRetryCount() == null ? 0 : task.getRetryCount()) + 1);
                taskExecutor.submit(task, t -> evalPipeline.run(t, recordId, evalFile, indexDir, targetVersionId));
                log.info("评测任务已恢复: {}", task.getBizNo());
            }
        }
    }
}
