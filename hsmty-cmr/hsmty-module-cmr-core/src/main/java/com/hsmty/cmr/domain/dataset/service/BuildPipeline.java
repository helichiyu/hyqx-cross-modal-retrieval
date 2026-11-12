package com.hsmty.cmr.domain.dataset.service;

import tools.jackson.databind.JsonNode;
import com.hsmty.cmr.common.exception.BizException;
import com.hsmty.cmr.common.id.SnowflakeIdWorker;
import com.hsmty.cmr.common.util.JsonUtils;
import com.hsmty.cmr.config.CmrProperties;
import com.hsmty.cmr.domain.dataset.entity.CmrDataset;
import com.hsmty.cmr.domain.dataset.entity.CmrIndex;
import com.hsmty.cmr.domain.dataset.mapper.DatasetMapper;
import com.hsmty.cmr.domain.dataset.mapper.IndexMapper;
import com.hsmty.cmr.domain.task.TaskService;
import com.hsmty.cmr.domain.task.entity.CmrTask;
import com.hsmty.cmr.domain.task.entity.CmrTaskLog;
import com.hsmty.cmr.operator.OpIds;
import com.hsmty.cmr.operator.OperatorClient;
import com.mybatisflex.core.query.QueryWrapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import static com.hsmty.cmr.domain.dataset.entity.table.CmrIndexTableDef.CMR_INDEX;

/**
 * 建库管线：401 接入校验 → 403 解析 → 404 向量化 → 405 建索引并直接激活。
 *
 * <p>按数据流 3.1「只做数据源与黑名单，不按数据集逐条评审，也不设人工卡点」，
 * 402 评定与 406 投产校验不再参与编排；405 建完即置 active，数据集直接可检索。
 * uniqueId = 任务编号，算子输出幂等可重跑。
 *
 * @author cmr
 * @since 2026-11-03
 */
@Service
@RequiredArgsConstructor
public class BuildPipeline {

    private static final DateTimeFormatter DIR_TS = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss");

    private final OperatorClient operatorClient;
    private final TaskService taskService;
    private final DatasetMapper datasetMapper;
    private final IndexMapper indexMapper;
    private final CmrProperties properties;
    private final SnowflakeIdWorker idWorker;

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
        // 接入校验必须全量通过：失败/条数/模态不一致都会让后续索引静默缺数据，直接中止
        int ingestFailures = r401.path("failureCount").asInt(0);
        BizException.assertTrue(ingestFailures > 0,
                "401 接入校验存在 " + ingestFailures + " 条失败，已中止建库");
        JsonNode reconcile = r401.path("reconcile");
        BizException.assertTrue(reconcile.path("countMatch").isBoolean()
                        && !reconcile.path("countMatch").asBoolean(true),
                "401 条目数与 manifest 声明不一致，已中止建库");
        BizException.assertTrue(reconcile.path("modalityMatch").isBoolean()
                        && !reconcile.path("modalityMatch").asBoolean(true),
                "401 模态覆盖与 manifest 声明不一致，已中止建库");
        dataset.setStatus(CmrDataset.STATUS_STORED);
        dataset.setMetaJson(JsonUtils.writeQuietly(Map.of(
                "report", r401.path("report"), "outputFile", r401.path("outputFile").asText())));
        // ignoreNulls=false：把历史卡点遗留的 stuckReason 清空
        datasetMapper.update(dataset, false);

        // 403 模态内容解析（输入回到原始数据目录）
        taskService.updateStep(task, "403");
        JsonNode r403 = step(task, "403", () -> operatorClient.predict(OpIds.PARSE,
                Map.of("uniqueId", uniqueId, "inputFile", dataset.getFileRoot())));
        // 403 输出为 byModality（各模态条目数）与 itemCount，兼容旧版 counts 键
        int skippedCount = r403.path("skipped").isArray() ? r403.path("skipped").size() : 0;
        BizException.assertTrue(skippedCount > 0,
                "403 解析存在 " + skippedCount + " 条跳过，已中止建库");
        int checkedItems = r401.path("totalItems").asInt(0);
        int parsedItems = r403.path("manifestItemCount").asInt(0);
        BizException.assertTrue(checkedItems > 0 && parsedItems != checkedItems,
                "403 解析条目数 " + parsedItems + " 与 401 校验条目数 " + checkedItems + " 不一致，已中止建库");
        JsonNode modalityCounts = r403.has("byModality") ? r403.path("byModality") : r403.path("counts");
        int itemCount = r403.path("itemCount").asInt(sumCounts(modalityCounts));
        dataset.setItemCount(itemCount);
        dataset.setModalityJson(JsonUtils.writeQuietly(modalityCounts));
        dataset.setStatus(CmrDataset.STATUS_INDEXING);
        datasetMapper.update(dataset);

        // 404 多模态向量化
        taskService.updateStep(task, "404");
        JsonNode r404 = step(task, "404", () -> operatorClient.predict(OpIds.EMBED,
                Map.of("uniqueId", uniqueId, "inputFile", r403.path("outputFile").asText())));
        String modelVersion = r404.path("modelVersion").asText(null);
        List<String> modelWhitelist = properties.getModel().getWhitelist();
        BizException.assertNull(modelVersion, "404 未返回向量模型版本");
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
        index.setId(idWorker.next());
        index.setDatasetId(dataset.getId());
        index.setIndexDir(r405.path("indexDir").asText(indexDir));
        index.setModelVersion(modelVersion);
        index.setStatus(CmrIndex.STATUS_CANDIDATE);
        index.setCountsJson(JsonUtils.writeQuietly(modalityCounts));
        indexMapper.insert(index, true);

        // 405 建完即激活：无人工卡点，数据集直接进入可检索
        activate(index, dataset);
        taskService.log(task.getId(), "405", CmrTaskLog.STATUS_OK, r405,
                "索引已激活，数据集可检索");
    }

    /** 激活索引：同场景旧 active 置 retired，本索引 active，数据集转可检索 */
    public void activate(CmrIndex index, CmrDataset dataset) {
        List<CmrIndex> actives = indexMapper.selectListByQuery(QueryWrapper.create()
                .where(CMR_INDEX.STATUS.eq(CmrIndex.STATUS_ACTIVE)));
        // 内存比较 scene：SQL 的 scene = null 永远不成立，历史数据场景为空时旧索引不会被 retired
        Map<Long, String> sceneById = new LinkedHashMap<>();
        for (CmrDataset ds : datasetMapper.selectAll()) {
            sceneById.put(ds.getId(), ds.getScene());
        }
        for (CmrIndex active : actives) {
            if (!active.getId().equals(index.getId())
                    && Objects.equals(sceneById.get(active.getDatasetId()), dataset.getScene())) {
                active.setStatus(CmrIndex.STATUS_RETIRED);
                indexMapper.update(active);
                // 同场景旧索引下线后，其所属数据集不能继续指向 retired 索引（否则列表显示可检索但搜不到）
                CmrDataset old = datasetMapper.selectOneById(active.getDatasetId());
                if (old != null && Objects.equals(old.getCurrentIndexId(), active.getId())) {
                    old.setCurrentIndexId(null);
                    old.setStatus(CmrDataset.STATUS_STORED);
                    datasetMapper.update(old, false);
                }
            }
        }
        index.setStatus(CmrIndex.STATUS_ACTIVE);
        index.setActivatedTime(LocalDateTime.now());
        indexMapper.update(index);

        dataset.setStatus(CmrDataset.STATUS_SEARCHABLE);
        dataset.setCurrentIndexId(index.getId());
        dataset.setStuckReason(null);
        // 同上：ignoreNulls=false 用于把 stuckReason 清成 null
        datasetMapper.update(dataset, false);
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
