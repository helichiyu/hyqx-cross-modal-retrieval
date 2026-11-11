package com.hsmty.cmr.search.service;

import cn.hutool.core.util.ObjectUtil;
import com.hsmty.cmr.feign.CmrOperatorFeignClient;
import com.hsmty.cmr.pojo.dto.OperatorSearchDTO;
import com.hsmty.cmr.pojo.dto.QueryUnderstandDTO;
import com.hsmty.cmr.pojo.dto.SearchCreateDTO;
import com.hsmty.cmr.pojo.vo.OperatorResult;
import com.hsmty.cmr.pojo.vo.OperatorSearchResultVO;
import com.hsmty.cmr.pojo.vo.QueryUnderstandVO;
import com.hsmty.cmr.pojo.vo.SearchResultItemVO;
import com.hsmty.cmr.pojo.vo.SearchResultVO;
import com.hsmty.cmr.search.config.SearchProperties;
import com.hsmty.cmr.search.entity.CmrIndex;
import com.hsmty.cmr.search.entity.CmrSearchHistory;
import com.hsmty.cmr.search.mapper.CmrIndexMapper;
import com.hsmty.core.common.enums.CommonResponseEnum;
import com.hsmty.core.common.exception.common.BusinessException;
import com.hsmty.core.util.BeanCopyUtils;
import com.mybatisflex.core.query.QueryWrapper;
import feign.FeignException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * 检索域服务：在线链的 407 查询理解与 408 检索执行。
 *
 * <p>算子是独立容器，本类只做参数校验、远程调用、结果落库与错误翻译，不伪造检索结果。
 * 结果集条目按 02 库表设计只落 ID 快照，文本与媒体定位不落库。
 *
 * @author cmr
 * @since 2026-11-10
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SearchService {

    private static final String INDEX_STATUS_ACTIVE = "active";
    private static final Set<String> MODALITY_KEYS = Set.of("text", "image", "audio", "video");
    private static final int MIN_TOP_K = 1;
    private static final int MAX_TOP_K = 100;
    private static final BigDecimal MIN_THRESHOLD = new BigDecimal("0.30");
    private static final BigDecimal MAX_THRESHOLD = new BigDecimal("0.99");
    private static final int WEIGHT_TOTAL = 100;

    private final CmrOperatorFeignClient operatorFeignClient;
    private final CmrIndexMapper indexMapper;
    private final SearchPersistService searchPersistService;
    private final SearchProperties searchProperties;

    /**
     * 查询理解：文本或查询文件二选一。
     *
     * @param dto 查询理解入参
     * @return 澄清标记、澄清选项与查询模态
     */
    public QueryUnderstandVO understand(QueryUnderstandDTO dto) {
        CommonResponseEnum.VALID_ERROR.assertTrue(dto == null
                || (!StringUtils.hasText(dto.getQuery()) && !StringUtils.hasText(dto.getQueryFile())));
        if (!StringUtils.hasText(dto.getUniqueId())) {
            dto.setUniqueId(UUID.randomUUID().toString());
        }

        OperatorResult<QueryUnderstandVO> result;
        try {
            result = operatorFeignClient.queryUnderstand(dto);
        } catch (FeignException e) {
            // 未启用 Spring Cloud CircuitBreaker 时 fallbackFactory 不生效，连接失败会抛 FeignException，
            // 这里统一翻译为业务错误，避免把熔断骨架的异常形态透给前端。
            log.error("检索算子调用失败: uniqueId={}, status={}", dto.getUniqueId(), e.status(), e);
            throw new BusinessException(CommonResponseEnum.UNKNOWN, new Object[0], "检索算子服务不可用");
        }
        if (result == null || result.getCode() == null || result.getCode() != OperatorResult.SUCCESS_CODE) {
            String reason = result == null || !StringUtils.hasText(result.getMsg())
                    ? "算子未返回有效响应" : result.getMsg();
            log.warn("查询理解失败: uniqueId={}, reason={}", dto.getUniqueId(), reason);
            throw new BusinessException(CommonResponseEnum.UNKNOWN, new Object[0], "查询理解失败：" + reason);
        }
        return result.getData();
    }

    /**
     * 执行检索：解析索引 → 调用 408 算子 → 落检索历史与结果集条目。
     *
     * <p>远程调用不在事务内，避免算子耗时期间占用数据库连接；落库由
     * {@link SearchPersistService#persist} 自行开启事务。
     *
     * @param dto 检索入参
     * @return 检索结果，含结果集编号与条目
     */
    public SearchResultVO search(SearchCreateDTO dto) {
        validate(dto);
        String query = resolveQuery(dto);
        int topK = dto.getTopK() == null ? searchProperties.getTopK() : dto.getTopK();
        Map<String, Integer> weights = dto.getWeights() == null
                ? new LinkedHashMap<>(searchProperties.getWeights()) : dto.getWeights();
        BigDecimal threshold = dto.getThreshold() == null
                ? searchProperties.getThreshold() : dto.getThreshold();
        boolean rerank = Boolean.TRUE.equals(dto.getRerank());

        CmrIndex activeIndex = findActiveIndex();
        String indexDir = activeIndex == null ? searchProperties.getIndexDir() : activeIndex.getIndexDir();

        OperatorSearchDTO operatorDTO = buildOperatorDTO(dto, query, indexDir, topK, weights, threshold, rerank);
        OperatorSearchResultVO operatorResult = callOperator(operatorDTO);

        CmrSearchHistory history = searchPersistService.persist(dto, query, threshold, operatorResult, activeIndex);
        return buildResult(history, operatorResult);
    }

    private void validate(SearchCreateDTO dto) {
        CommonResponseEnum.VALID_ERROR.message("检索参数不能为空").assertTrue(dto == null);
        CommonResponseEnum.VALID_ERROR.message("查询文本与查询文件必须二选一")
                .assertTrue(!StringUtils.hasText(dto.getQuery()) && !StringUtils.hasText(dto.getQueryFile()));
        if (dto.getTopK() != null) {
            CommonResponseEnum.VALID_ERROR.message("topK 必须在 1–100 之间")
                    .assertTrue(dto.getTopK() < MIN_TOP_K || dto.getTopK() > MAX_TOP_K);
        }
        if (dto.getThreshold() != null) {
            CommonResponseEnum.VALID_ERROR.message("阈值必须在 0.30–0.99 之间")
                    .assertTrue(dto.getThreshold().compareTo(MIN_THRESHOLD) < 0
                            || dto.getThreshold().compareTo(MAX_THRESHOLD) > 0);
        }
        if (dto.getWeights() != null) {
            Map<String, Integer> weights = dto.getWeights();
            CommonResponseEnum.VALID_ERROR.message("权重仅支持 text/image/audio/video")
                    .assertTrue(!MODALITY_KEYS.containsAll(weights.keySet()));
            int total = weights.values().stream().mapToInt(Integer::intValue).sum();
            CommonResponseEnum.VALID_ERROR.message("权重合计必须为 100").assertTrue(total != WEIGHT_TOTAL);
        }
    }

    private String resolveQuery(SearchCreateDTO dto) {
        if (Boolean.TRUE.equals(dto.getClarified()) && StringUtils.hasText(dto.getClarifiedQuery())) {
            return dto.getClarifiedQuery();
        }
        return dto.getQuery();
    }

    private CmrIndex findActiveIndex() {
        return indexMapper.selectOneByQuery(QueryWrapper.create()
                .eq(CmrIndex::getStatus, INDEX_STATUS_ACTIVE)
                .orderBy(CmrIndex::getActivatedTime, false)
                .limit(1));
    }

    private OperatorSearchDTO buildOperatorDTO(SearchCreateDTO dto, String query, String indexDir,
                                               int topK, Map<String, Integer> weights,
                                               BigDecimal threshold, boolean rerank) {
        OperatorSearchDTO operatorDTO = new OperatorSearchDTO();
        operatorDTO.setQuery(query);
        operatorDTO.setQueryFile(query == null ? dto.getQueryFile() : null);
        operatorDTO.setIndexDir(indexDir);
        operatorDTO.setTopK(topK);
        operatorDTO.setWeights(weights);
        operatorDTO.setRerank(rerank);
        operatorDTO.setThreshold(threshold);
        operatorDTO.setTargetModalities(dto.getTargetModalities());
        return operatorDTO;
    }

    private OperatorSearchResultVO callOperator(OperatorSearchDTO operatorDTO) {
        OperatorResult<OperatorSearchResultVO> result;
        try {
            result = operatorFeignClient.retrieve(operatorDTO);
        } catch (FeignException e) {
            log.error("检索算子调用失败: indexDir={}, status={}", operatorDTO.getIndexDir(), e.status(), e);
            throw new BusinessException(CommonResponseEnum.UNKNOWN, new Object[0], "检索算子服务不可用");
        }
        if (result == null || result.getCode() == null || result.getCode() != OperatorResult.SUCCESS_CODE) {
            String reason = result == null || !StringUtils.hasText(result.getMsg())
                    ? "算子未返回有效响应" : result.getMsg();
            log.warn("检索失败: indexDir={}, reason={}", operatorDTO.getIndexDir(), reason);
            throw new BusinessException(CommonResponseEnum.UNKNOWN, new Object[0], "检索失败：" + reason);
        }
        CommonResponseEnum.VALID_ERROR.message("算子未返回有效结果").assertTrue(result.getData() == null);
        return result.getData();
    }

    private SearchResultVO buildResult(CmrSearchHistory history, OperatorSearchResultVO operatorResult) {
        SearchResultVO vo = new SearchResultVO();
        vo.setSearchNo(history.getBizNo());
        vo.setResultSetId(history.getResultSetId());
        vo.setQuery(operatorResult.getQuery());
        vo.setQueryModality(operatorResult.getQueryModality());
        vo.setCount(history.getItemCount());
        vo.setModelVersion(operatorResult.getModelVersion());
        vo.setThreshold(history.getThreshold());
        vo.setElapsedMs(history.getElapsedMs() == null ? null : history.getElapsedMs().longValue());
        // 响应直接从算子结果转换，保留本次展示所需的 text/locator；落库只存 ID 快照。
        vo.setItems(ObjectUtil.isEmpty(operatorResult.getItems()) ? List.of()
                : operatorResult.getItems().stream()
                .map(item -> BeanCopyUtils.copy(item, SearchResultItemVO.class))
                .toList());
        return vo;
    }
}
