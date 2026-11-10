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
 * 数据集服务：登记接入（创建 + 异步建库任务）、裁决、确认投产、重建、索引激活
 *
 * <p>状态机强制：非法流转直接 code=1（02 库表设计第 3 节）。
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

    /** 登记接入：解压/定位数据目录 → 建数据集（ingest）→ 建任务异步跑 401→406 */
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

        CmrDataset dataset = new CmrDataset();
        dataset.setId(idWorker.next());
        dataset.setBizNo(bizNo);
        dataset.setDatasourceId(dto.getDatasourceId());
        dataset.setName(dto.getName());
        dataset.setVersion(dto.getVersion());
        dataset.setScene(dto.getScene());
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

    /** 评定挂起裁决：通过→续跑建库（幂等重跑）；不通过→已剔除（终态） */
    @Transactional(rollbackFor = Exception.class)
    public void adjudicate(Long id, boolean pass, String note) {
        CmrDataset dataset = requireDataset(id);
        BizException.assertTrue(!CmrDataset.STATUS_STUCK.equals(dataset.getStatus()),
                "仅卡住状态的数据集可裁决");
        BizException.assertTrue(!"suspend".equals(dataset.getDecision()),
                "该数据集不是评定挂起（suspend），不可裁决");
        if (pass) {
            dataset.setStatus(CmrDataset.STATUS_STORED);
            dataset.setStuckReason(null);
            // ignoreNulls=false：Flex 默认跳过 null 字段，否则卡住原因清不掉
            datasetMapper.update(dataset, false);
            submitBuild(dataset);
            log.info("裁决通过，续跑建库: dataset={} note={}", dataset.getBizNo(), note);
        } else {
            dataset.setStatus(CmrDataset.STATUS_REJECTED);
            dataset.setStuckReason(note != null && !note.isBlank() ? note : "人工裁决不通过");
            datasetMapper.update(dataset);
            log.info("裁决不通过，数据集已剔除: dataset={}", dataset.getBizNo());
        }
    }

    /** 召回率不足人工确认投产：激活最新候选索引 → 可检索 */
    @Transactional(rollbackFor = Exception.class)
    public void confirmProduction(Long id) {
        CmrDataset dataset = requireDataset(id);
        BizException.assertTrue(!CmrDataset.STATUS_STUCK.equals(dataset.getStatus()),
                "仅卡住状态的数据集可确认投产");
        CmrIndex candidate = indexMapper.selectOneByQuery(QueryWrapper.create()
                .where(CMR_INDEX.DATASET_ID.eq(id).and(CMR_INDEX.STATUS.eq(CmrIndex.STATUS_CANDIDATE)))
                .orderBy(CMR_INDEX.ID.desc())
                .limit(1));
        BizException.assertNull(candidate, "没有可投产的候选索引");
        buildPipeline.activate(candidate, dataset);
        log.info("人工确认投产，索引激活: dataset={} index={}", dataset.getBizNo(), candidate.getIndexDir());
    }

    /** 重建（全链重跑，新任务；rejected 终态不可重建） */
    @Transactional(rollbackFor = Exception.class)
    public TaskVO rebuild(Long id) {
        CmrDataset dataset = requireDataset(id);
        BizException.assertTrue(CmrDataset.STATUS_REJECTED.equals(dataset.getStatus()),
                "已剔除的数据集不可重建");
        dataset.setStatus(CmrDataset.STATUS_INGEST);
        dataset.setStuckReason(null);
        // ignoreNulls=false：Flex 默认跳过 null 字段，否则卡住原因清不掉
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

    /** 回滚激活：candidate/retired 索引重新激活 */
    @Transactional(rollbackFor = Exception.class)
    public IndexVO activateIndex(Long indexId) {
        CmrIndex index = indexMapper.selectOneById(indexId);
        BizException.assertNull(index, "索引不存在");
        BizException.assertTrue(CmrIndex.STATUS_ACTIVE.equals(index.getStatus()), "该索引已处于激活状态");
        CmrDataset dataset = requireDataset(index.getDatasetId());
        buildPipeline.activate(index, dataset);
        return IndexVO.from(indexMapper.selectOneById(indexId));
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
