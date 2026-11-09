package com.hsmty.cmr.domain.dataset.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.hsmty.cmr.common.exception.BizException;
import com.hsmty.cmr.common.util.JsonUtils;
import com.hsmty.cmr.config.CmrProperties;
import com.hsmty.cmr.domain.dataset.entity.CmrDataset;
import com.hsmty.cmr.domain.dataset.entity.CmrIndex;
import com.hsmty.cmr.domain.dataset.mapper.DatasetMapper;
import com.hsmty.cmr.domain.dataset.mapper.IndexMapper;
import com.hsmty.cmr.domain.task.TaskService;
import com.hsmty.cmr.domain.task.entity.CmrTask;
import com.hsmty.cmr.domain.task.entity.CmrTaskLog;
import com.hsmty.cmr.file.FileService;
import com.hsmty.cmr.operator.OpIds;
import com.hsmty.cmr.operator.OperatorClient;
import com.mybatisflex.core.query.QueryWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static com.hsmty.cmr.domain.dataset.entity.table.CmrDatasetTableDef.CMR_DATASET;
import static com.hsmty.cmr.domain.dataset.entity.table.CmrIndexTableDef.CMR_INDEX;

/**
 * 建库管线：401 接入校验 → 402 评定（挂起/剔除分支）→ 403 解析 → 404 向量化 → 405 建索引 → 406 投产校验（激活/卡住分支）
 *
 * <p>数据集状态机随环节推进（02 库表设计第 3 节）；uniqueId = 任务编号，算子输出幂等可重跑。
 *
 * @author cmr
 * @since 2026-11-03
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BuildPipeline {

    private static final DateTimeFormatter DIR_TS = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss");

    private final OperatorClient operatorClient;
    private final TaskService taskService;
    private final DatasetMapper datasetMapper;
    private final IndexMapper indexMapper;
    private final FileService fileService;
    private final CmrProperties properties;

    /** 执行建库链（由 TaskExecutor 调度，异常上抛交由重试/隔离处置） */
    public void run(CmrTask task, Long datasetId) {
        CmrDataset dataset = datasetMapper.selectOneById(datasetId);
        BizException.assertNull(dataset, "数据集不存在: " + datasetId);
        String uniqueId = task.getBizNo();

        // 401 数据接入校验
        taskService.updateStep(task, "401");
        taskService.log(task.getId(), "401", CmrTaskLog.STATUS_START, null, "inputFile=" + dataset.getFileRoot());
        JsonNode r401 = step(task, "401", () -> operatorClient.predict(OpIds.INGEST,
                Map.of("uniqueId", uniqueId, "inputFile", dataset.getFileRoot())));
        dataset.setStatus(CmrDataset.STATUS_SCORING);
        dataset.setMetaJson(JsonUtils.writeQuietly(Map.of(
                "report", r401.path("report"), "outputFile", r401.path("outputFile").asText())));
        datasetMapper.update(dataset);

        // 402 数据集评定
        taskService.updateStep(task, "402");
        JsonNode r402 = step(task, "402", () -> operatorClient.predict(OpIds.SCORE,
                Map.of("uniqueId", uniqueId, "inputFile", r401.path("outputFile").asText())));
        String decision = r402.path("decision").asText("");
        dataset.setScore(r402.path("score").isNumber() ? r402.path("score").decimalValue() : null);
        dataset.setDecision(decision);
        switch (decision) {
            case "suspend" -> {
                dataset.setStatus(CmrDataset.STATUS_STUCK);
                dataset.setStuckReason("数据集评定挂起（评分 " + r402.path("score").asText() + "），待人工裁决");
                datasetMapper.update(dataset);
                taskService.log(task.getId(), "402", CmrTaskLog.STATUS_OK, r402, "评定挂起，等待人工裁决");
                return;
            }
            case "reject" -> {
                dataset.setStatus(CmrDataset.STATUS_REJECTED);
                dataset.setStuckReason("数据集评定不通过（评分 " + r402.path("score").asText() + "）");
                datasetMapper.update(dataset);
                taskService.log(task.getId(), "402", CmrTaskLog.STATUS_OK, r402, "评定不通过，数据集已剔除");
                return;
            }
            default -> {
                dataset.setStatus(CmrDataset.STATUS_STORED);
                dataset.setStuckReason(null);
                datasetMapper.update(dataset);
            }
        }

        // 403 模态内容解析（输入回到原始数据目录）
        taskService.updateStep(task, "403");
        JsonNode r403 = step(task, "403", () -> operatorClient.predict(OpIds.PARSE,
                Map.of("uniqueId", uniqueId, "inputFile", dataset.getFileRoot())));
        int itemCount = sumCounts(r403.path("counts"));
        dataset.setItemCount(itemCount);
        dataset.setModalityJson(JsonUtils.writeQuietly(r403.path("counts")));
        dataset.setStatus(CmrDataset.STATUS_INDEXING);
        datasetMapper.update(dataset);

        // 404 多模态向量化
        taskService.updateStep(task, "404");
        JsonNode r404 = step(task, "404", () -> operatorClient.predict(OpIds.EMBED,
                Map.of("uniqueId", uniqueId, "inputFile", r403.path("outputFile").asText())));
        String modelVersion = r404.path("modelVersion").asText(null);
        List<String> modelWhitelist = properties.getModel().getWhitelist();
        BizException.assertTrue(modelVersion != null && modelVersion.isBlank(), "404 未返回向量模型版本");
        BizException.assertTrue(!modelWhitelist.isEmpty() && !modelWhitelist.contains(modelVersion),
                "向量模型不在白名单内: " + modelVersion);

        // 405 向量索引构建
        String indexDir = "runtime/index/" + dataset.getBizNo() + "_" + LocalDateTime.now().format(DIR_TS);
        taskService.updateStep(task, "405");
        JsonNode r405 = step(task, "405", () -> {
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("uniqueId", uniqueId);
            payload.put("inputFile", r404.path("outputFile").asText());
            payload.put("itemsFile", r403.path("outputFile").asText());
            payload.put("indexDir", indexDir);
            return operatorClient.predict(OpIds.INDEX, payload);
        });
        CmrIndex index = new CmrIndex();
        index.setDatasetId(dataset.getId());
        index.setIndexDir(r405.path("indexDir").asText(indexDir));
        index.setModelVersion(modelVersion);
        index.setStatus(CmrIndex.STATUS_CANDIDATE);
        index.setCountsJson(JsonUtils.writeQuietly(r403.path("counts")));
        indexMapper.insert(index, true);

        // 406 投产召回率校验
        String evalFile = ensureDefaultEvalFile(r403.path("outputFile").asText());
        taskService.updateStep(task, "406");
        JsonNode r406 = step(task, "406", () -> operatorClient.predict(OpIds.RECALL,
                Map.of("uniqueId", uniqueId, "indexDir", index.getIndexDir(), "evalFile", evalFile)));
        index.setCheckResultJson(JsonUtils.writeQuietly(r406));
        indexMapper.update(index);

        if ("pass".equalsIgnoreCase(r406.path("decision").asText(""))) {
            activate(index, dataset);
            taskService.log(task.getId(), "406", CmrTaskLog.STATUS_OK, r406,
                    "投产校验通过，索引已激活，数据集可检索");
        } else {
            dataset.setStatus(CmrDataset.STATUS_STUCK);
            dataset.setStuckReason("召回率校验未达标（recall=" + r406.path("recall").asText() + "），待人工确认投产");
            datasetMapper.update(dataset);
            taskService.log(task.getId(), "406", CmrTaskLog.STATUS_OK, r406, "召回率未达标，等待人工确认投产");
        }
    }

    /** 激活索引：同场景旧 active 置 retired，本索引 active，数据集转可检索 */
    public void activate(CmrIndex index, CmrDataset dataset) {
        List<CmrIndex> actives = indexMapper.selectListByQuery(QueryWrapper.create()
                .where(CMR_INDEX.STATUS.eq(CmrIndex.STATUS_ACTIVE)));
        List<Long> sameScene = datasetMapper.selectListByQuery(QueryWrapper.create()
                        .where(CMR_DATASET.SCENE.eq(dataset.getScene())))
                .stream().map(CmrDataset::getId).toList();
        for (CmrIndex active : actives) {
            if (!active.getId().equals(index.getId()) && sameScene.contains(active.getDatasetId())) {
                active.setStatus(CmrIndex.STATUS_RETIRED);
                indexMapper.update(active);
            }
        }
        index.setStatus(CmrIndex.STATUS_ACTIVE);
        index.setActivatedTime(LocalDateTime.now());
        indexMapper.update(index);

        dataset.setStatus(CmrDataset.STATUS_SEARCHABLE);
        dataset.setCurrentIndexId(index.getId());
        dataset.setStuckReason(null);
        datasetMapper.update(dataset);
    }

    /** 步骤统一包裹：start 日志在调用方、ok/fail 日志与异常上抛在此 */
    private JsonNode step(CmrTask task, String stepName, StepCall call) {
        try {
            JsonNode data = call.get();
            taskService.log(task.getId(), stepName, CmrTaskLog.STATUS_OK, data, null);
            return data;
        } catch (Exception e) {
            taskService.log(task.getId(), stepName, CmrTaskLog.STATUS_FAIL, null, e.getMessage());
            throw e instanceof RuntimeException re ? re : new BizException(e.getMessage(), e);
        }
    }

    /** 默认评测文件不存在时，从解析条目自动合成（文本条目 query→itemId 自评） */
    private String ensureDefaultEvalFile(String itemsFile) {
        String defaultFile = properties.getEval().getDefaultEvalFile();
        if (fileService.exists(defaultFile)) {
            return defaultFile;
        }
        List<String> lines;
        try {
            lines = fileService.readLines(itemsFile);
        } catch (BizException e) {
            lines = List.of();
        }
        List<String> evalLines = new ArrayList<>();
        for (String line : lines) {
            if (evalLines.size() >= 50) {
                break;
            }
            JsonNode item = JsonUtils.readTree(line);
            String text = item.path("text").asText("");
            String itemId = item.path("itemId").asText("");
            if (!text.isBlank() && !itemId.isBlank()) {
                String query = text.length() > 80 ? text.substring(0, 80) : text;
                evalLines.add(JsonUtils.write(Map.of("query", query, "answers", List.of(itemId))));
            }
        }
        BizException.assertTrue(evalLines.isEmpty(), "无可用评测条目，且默认评测文件缺失: " + defaultFile);
        fileService.writeDatasetText(defaultFile.substring("dataset/".length()), String.join("\n", evalLines) + "\n");
        log.info("已自动合成默认评测文件: {}（{} 条）", defaultFile, evalLines.size());
        return defaultFile;
    }

    private int sumCounts(JsonNode counts) {
        int sum = 0;
        if (counts.isObject()) {
            for (JsonNode v : counts) {
                sum += v.asInt(0);
            }
        }
        return sum;
    }

    @FunctionalInterface
    private interface StepCall {
        JsonNode get();
    }
}
