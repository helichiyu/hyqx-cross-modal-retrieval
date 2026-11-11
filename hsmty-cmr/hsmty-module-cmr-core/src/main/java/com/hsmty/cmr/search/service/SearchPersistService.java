package com.hsmty.cmr.search.service;

import cn.hutool.core.util.ObjectUtil;
import com.hsmty.cmr.common.id.BizNoGenerator;
import com.hsmty.cmr.pojo.dto.SearchCreateDTO;
import com.hsmty.cmr.pojo.vo.OperatorSearchItemVO;
import com.hsmty.cmr.pojo.vo.OperatorSearchResultVO;
import com.hsmty.cmr.search.config.SearchProperties;
import com.hsmty.cmr.search.entity.CmrIndex;
import com.hsmty.cmr.search.entity.CmrResultItem;
import com.hsmty.cmr.search.entity.CmrSearchHistory;
import com.hsmty.cmr.search.mapper.CmrResultItemMapper;
import com.hsmty.cmr.search.mapper.CmrSearchHistoryMapper;
import com.hsmty.core.util.JsonUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 检索结果落库。
 *
 * <p>独立成 Bean 是为了把事务边界收在写库阶段，避免调用算子期间占用数据库连接。
 *
 * @author cmr
 * @since 2026-11-11
 */
@Service
@RequiredArgsConstructor
public class SearchPersistService {

    private static final String ADOPT_STATUS_UNADOPTED = "unadopted";
    private static final String SEALED_NO = "F";
    private static final String BIZ_NO_PREFIX = "SRH";
    private static final String RESULT_SET_PREFIX = "RST";
    private static final int ITEM_NAME_MAX_LENGTH = 512;
    private static final int SOURCE_MAX_LENGTH = 256;

    private final CmrSearchHistoryMapper searchHistoryMapper;
    private final CmrResultItemMapper resultItemMapper;
    private final SearchProperties searchProperties;

    /**
     * 写入检索历史与结果集条目。
     *
     * @param dto            检索入参
     * @param query          实际使用的查询文本
     * @param threshold      本次生效阈值
     * @param operatorResult 算子结果
     * @param activeIndex    检索时激活索引，可为 null
     * @return 已写入的检索历史
     */
    @Transactional(rollbackFor = Exception.class)
    public CmrSearchHistory persist(SearchCreateDTO dto, String query, BigDecimal threshold,
                                    OperatorSearchResultVO operatorResult, CmrIndex activeIndex) {
        CmrSearchHistory history = buildHistory(dto, query, threshold, operatorResult, activeIndex);
        searchHistoryMapper.insert(history);
        saveResultItems(history, operatorResult);
        return history;
    }

    private CmrSearchHistory buildHistory(SearchCreateDTO dto, String query, BigDecimal threshold,
                                          OperatorSearchResultVO operatorResult, CmrIndex activeIndex) {
        CmrSearchHistory history = new CmrSearchHistory();
        history.setBizNo(BizNoGenerator.generate(BIZ_NO_PREFIX));
        history.setResultSetId(StringUtils.hasText(operatorResult.getResultSetId())
                ? operatorResult.getResultSetId() : BizNoGenerator.generate(RESULT_SET_PREFIX));
        history.setIndexId(activeIndex == null ? null : activeIndex.getId());
        history.setQueryText(truncate(query, 1024));
        history.setQueryFile(truncate(dto.getQueryFile(), 256));
        history.setQueryModality(operatorResult.getQueryModality());
        history.setClarified(Boolean.TRUE.equals(dto.getClarified()));
        history.setScene(dto.getScene());
        history.setStrategySnapshotJson(JsonUtils.toJsonString(operatorResult.getStrategy() == null
                ? Map.of() : operatorResult.getStrategy()));
        history.setItemCount(operatorResult.getCount() == null
                ? sizeOf(operatorResult.getItems()) : operatorResult.getCount());
        history.setElapsedMs(operatorResult.getElapsedMs() == null
                ? null : (int) Math.round(operatorResult.getElapsedMs()));
        history.setAdoptStatus(ADOPT_STATUS_UNADOPTED);
        history.setThreshold(threshold);
        history.setSealed(SEALED_NO);
        history.setSummaryExpireTime(LocalDateTime.now().plusDays(searchProperties.getSummaryDays()));
        fillOperator(history);
        return history;
    }

    private void saveResultItems(CmrSearchHistory history, OperatorSearchResultVO operatorResult) {
        if (ObjectUtil.isEmpty(operatorResult.getItems())) {
            return;
        }
        operatorResult.getItems().stream()
                .map(item -> toResultItem(history, item))
                .forEach(resultItemMapper::insert);
    }

    private CmrResultItem toResultItem(CmrSearchHistory history, OperatorSearchItemVO item) {
        CmrResultItem entity = new CmrResultItem();
        entity.setSearchId(history.getId());
        entity.setResultSetId(history.getResultSetId());
        entity.setResultRank(item.getRank());
        entity.setItemId(item.getItemId());
        entity.setItemName(truncate(item.getSourceFile(), ITEM_NAME_MAX_LENGTH));
        entity.setSource(truncate(item.getSource(), SOURCE_MAX_LENGTH));
        entity.setModality(item.getModality());
        entity.setScore(item.getScore());
        entity.setAlignScore(item.getAlignScore());
        entity.setClicked(Boolean.FALSE);
        return entity;
    }

    private void fillOperator(CmrSearchHistory history) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()
                || "anonymousUser".equals(authentication.getName())) {
            return;
        }
        history.setOperatorName(truncate(authentication.getName(), 64));
    }

    private static int sizeOf(List<?> list) {
        return list == null ? 0 : list.size();
    }

    private static String truncate(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength);
    }
}
