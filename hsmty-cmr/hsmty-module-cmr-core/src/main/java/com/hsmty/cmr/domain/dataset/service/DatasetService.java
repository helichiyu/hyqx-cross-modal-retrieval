package com.hsmty.cmr.domain.dataset.service;

import com.hsmty.cmr.common.api.PageQuery;
import com.hsmty.cmr.common.api.PageResult;
import com.hsmty.cmr.common.exception.BizException;
import com.hsmty.cmr.common.id.BizNoGenerator;
import com.hsmty.cmr.common.id.SnowflakeIdWorker;
import com.hsmty.cmr.domain.dataset.dto.DatasetCreateDTO;
import com.hsmty.cmr.domain.dataset.entity.CmrDataset;
import com.hsmty.cmr.domain.dataset.entity.CmrIndex;
import com.hsmty.cmr.domain.dataset.mapper.DatasetMapper;
import com.hsmty.cmr.domain.dataset.mapper.IndexMapper;
import com.hsmty.cmr.domain.dataset.vo.DatasetDetailVO;
import com.hsmty.cmr.domain.dataset.vo.DatasetVO;
import com.hsmty.cmr.domain.dataset.vo.IndexVO;
import com.hsmty.cmr.domain.task.TaskExecutor;
import com.hsmty.cmr.domain.task.TaskService;
import com.hsmty.cmr.domain.task.entity.CmrTask;
import com.hsmty.cmr.domain.task.vo.TaskVO;
import com.hsmty.cmr.file.FileService;
import com.mybatisflex.core.paginate.Page;
import com.mybatisflex.core.query.QueryWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static com.hsmty.cmr.domain.dataset.entity.table.CmrDatasetTableDef.CMR_DATASET;
import static com.hsmty.cmr.domain.dataset.entity.table.CmrIndexTableDef.CMR_INDEX;

/**
 * 数据集服务：登记接入（创建 + 异步建库任务）、重建、索引回滚激活
 *
 * <p>按数据流 3.1 不设人工卡点：建库链 405 完成后自动激活，不再有评定裁决与投产确认。
 *
 * @author cmr
 * @since 2026-11-03
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DatasetService {

    private final DatasetMapper datasetMapper;
    private final IndexMapper indexMapper;
    private final FileService fileService;
    private final TaskService taskService;
    private final TaskExecutor taskExecutor;
    private final BuildPipeline buildPipeline;
    private final SnowflakeIdWorker idWorker;
    private final BizNoGenerator bizNoGenerator;

    /** 分页：status/scene/name 模糊/时间倒序 */
    public PageResult<DatasetVO> page(PageQuery query, String status, String scene, String name) {
        QueryWrapper qw = QueryWrapper.create().from(CMR_DATASET);
        if (notBlank(status)) {
            qw.where(CMR_DATASET.STATUS.eq(status));
        }
        if (notBlank(scene)) {
            qw.where(CMR_DATASET.SCENE.eq(scene));
        }
        if (notBlank(name)) {
            qw.where(CMR_DATASET.NAME.like(name));
        }
        qw.orderBy(CMR_DATASET.ID.desc());
        Page<CmrDataset> page = datasetMapper.paginate(
                Math.max(query.getPageNum(), 1), Math.max(query.getPageSize(), 1), qw);
        List<DatasetVO> vos = page.getRecords().stream().map(DatasetVO::from).toList();
        return PageResult.of(vos, page.getTotalRow(), query.getPageNum(), query.getPageSize());
    }

    /** 登记接入：解压/定位数据目录 → 建数据集（ingest）→ 建任务异步跑 401→405 */
    @Transactional(rollbackFor = Exception.class)
    public DatasetVO create(DatasetCreateDTO dto) {
        boolean hasFile = dto.getFile() != null && !dto.getFile().isEmpty();
        boolean hasPath = notBlank(dto.getDatasetPath());
        BizException.assertTrue(hasFile == hasPath, "数据集压缩包与已有目录必须二选一");
        String bizNo = bizNoGenerator.next(BizNoGenerator.PREFIX_DATASET);
        String fileRoot;
        if (hasFile) {
            fileRoot = fileService.extractDataset(bizNo, dto.getFile());
        } else {
            fileRoot = fileService.requireDir(dto.getDatasetPath());
        }
        return register(dto.getDatasourceId(), dto.getName(), dto.getVersion(), dto.getScene(), fileRoot, bizNo);
    }

    /**
     * 数据源接入：上游 manifest 已落到共享卷时登记数据集并提交建库任务（401→405）
     *
     * @param datasourceId 来源数据源
     * @param name         数据集名称
     * @param version      版本号
     * @param scene        业务场景
     * @param fileRoot     manifest 逻辑目录（dataset/...，不含原始内容）
     * @return 新数据集
     */
    @Transactional(rollbackFor = Exception.class)
    public DatasetVO registerUpstream(Long datasourceId, String name, String version, String scene, String fileRoot) {
        return registerUpstream(datasourceId, null, name, version, scene, fileRoot);
    }

    /**
     * 数据源接入登记（带上游数据集 ID）：登记前按 数据源 + 上游数据集 去重，重复接入直接拒绝。
     *
     * @param datasourceId      来源数据源
     * @param upstreamDatasetId 上游数据集 ID（接入去重锚点）
     * @param name              数据集名称
     * @param version           版本号
     * @param scene             业务场景
     * @param fileRoot          manifest 逻辑目录（dataset/...，不含原始内容）
     * @return 新数据集
     */
    @Transactional(rollbackFor = Exception.class)
    public DatasetVO registerUpstream(Long datasourceId, String upstreamDatasetId, String name,
                                      String version, String scene, String fileRoot) {
        BizException.assertTrue(existsUpstream(datasourceId, upstreamDatasetId), "该上游数据集已接入");
        return register(datasourceId, upstreamDatasetId, name, version, scene, fileRoot,
                bizNoGenerator.next(BizNoGenerator.PREFIX_DATASET));
    }

    /**
     * 同一数据源的同一上游数据集是否已接入（逻辑未删除口径）。
     *
     * @param datasourceId      来源数据源
     * @param upstreamDatasetId 上游数据集 ID
     * @return 已接入返回 true
     */
    public boolean existsUpstream(Long datasourceId, String upstreamDatasetId) {
        if (datasourceId == null || upstreamDatasetId == null || upstreamDatasetId.isBlank()) {
            return false;
        }
        Long count = datasetMapper.selectCountByQuery(QueryWrapper.create()
                .where(CMR_DATASET.DATASOURCE_ID.eq(datasourceId)
                        .and(CMR_DATASET.UPSTREAM_DATASET_ID.eq(upstreamDatasetId))));
        return count != null && count > 0;
    }

    private DatasetVO register(Long datasourceId, String name, String version, String scene,
                               String fileRoot, String bizNo) {
        return register(datasourceId, null, name, version, scene, fileRoot, bizNo);
    }

    private DatasetVO register(Long datasourceId, String upstreamDatasetId, String name, String version,
                               String scene, String fileRoot, String bizNo) {
        CmrDataset dataset = new CmrDataset();
        dataset.setId(idWorker.next());
        dataset.setBizNo(bizNo);
        dataset.setDatasourceId(datasourceId);
        dataset.setUpstreamDatasetId(upstreamDatasetId);
        dataset.setName(name);
        dataset.setVersion(version);
        dataset.setScene(scene == null || scene.isBlank() ? "general" : scene);
        dataset.setStatus(CmrDataset.STATUS_INGEST);
        dataset.setFileRoot(fileRoot);
        dataset.setItemCount(0);
        dataset.setDelFlag("F");
        dataset.setCreateTime(LocalDateTime.now());
        datasetMapper.insert(dataset, true);

        submitBuild(dataset);
        return DatasetVO.from(dataset);
    }

    /** 提交建库任务（新建/重建/裁决通过后续跑共用；算子幂等，全链重跑） */
    public CmrTask submitBuild(CmrDataset dataset) {
        CmrTask task = taskService.create(CmrTask.TYPE_BUILD, dataset.getId(),
                Map.of("datasetId", dataset.getId()));
        Long taskId = task.getId();
        Long datasetId = dataset.getId();
        taskExecutor.submit(task, t -> buildPipeline.run(t, datasetId));
        log.info("建库任务已提交: {} dataset={}", task.getBizNo(), dataset.getBizNo());
        return task;
    }

    public CmrDataset requireDataset(Long id) {
        CmrDataset dataset = datasetMapper.selectOneById(id);
        BizException.assertNull(dataset, "数据集不存在");
        return dataset;
    }

    public DatasetDetailVO detail(Long id) {
        CmrDataset dataset = requireDataset(id);
        List<IndexVO> indexes = indexMapper.selectListByQuery(QueryWrapper.create()
                        .where(CMR_INDEX.DATASET_ID.eq(id))
                        .orderBy(CMR_INDEX.ID.desc()))
                .stream().map(IndexVO::from).toList();
        List<TaskVO> tasks = taskService.tasksOf(CmrTask.TYPE_BUILD, id).stream()
                .map(this::toTaskVO)
                .toList();
        return DatasetDetailVO.builder()
                .dataset(DatasetVO.from(dataset))
                .metaJson(dataset.getMetaJson())
                .indexes(indexes)
                .tasks(tasks)
                .build();
    }

    /** 重建（全链重跑，新任务） */
    @Transactional(rollbackFor = Exception.class)
    public TaskVO rebuild(Long id) {
        CmrDataset dataset = requireDataset(id);
        dataset.setStatus(CmrDataset.STATUS_INGEST);
        dataset.setStuckReason(null);
        // ignoreNulls=false：Flex 默认跳过 null 字段，否则历史卡点原因清不掉
        datasetMapper.update(dataset, false);
        return toTaskVO(submitBuild(dataset));
    }

    private TaskVO toTaskVO(CmrTask t) {
        return TaskVO.builder()
                .id(t.getId()).bizNo(t.getBizNo()).type(t.getType()).refId(t.getRefId())
                .status(t.getStatus()).failType(t.getFailType()).failDisposal(t.getFailDisposal())
                .retryCount(t.getRetryCount()).currentStep(t.getCurrentStep())
                .errorMsg(t.getErrorMsg()).startedTime(t.getStartedTime())
                .finishedTime(t.getFinishedTime()).createTime(t.getCreateTime())
                .build();
    }

    public List<IndexVO> indexesOf(Long datasetId) {
        return indexMapper.selectListByQuery(QueryWrapper.create()
                        .where(CMR_INDEX.DATASET_ID.eq(datasetId))
                        .orderBy(CMR_INDEX.ID.desc()))
                .stream().map(IndexVO::from).toList();
    }

    private boolean notBlank(String s) {
        return s != null && !s.isBlank();
    }
}
