package com.hsmty.cmr.domain.search.service;

import com.hsmty.cmr.common.exception.BizException;
import com.hsmty.cmr.common.id.BizNoGenerator;
import com.hsmty.cmr.common.id.SnowflakeIdWorker;
import com.hsmty.cmr.domain.dataset.entity.CmrDatasourceBlacklist;
import com.hsmty.cmr.domain.dataset.mapper.DatasetMapper;
import com.hsmty.cmr.domain.dataset.mapper.DatasourceBlacklistMapper;
import com.hsmty.cmr.domain.dataset.mapper.IndexMapper;
import com.hsmty.cmr.domain.operation.entity.CmrBlockedItem;
import com.hsmty.cmr.domain.operation.mapper.BlockedItemMapper;
import com.hsmty.cmr.domain.search.entity.CmrResultItem;
import com.hsmty.cmr.domain.search.entity.CmrSearchHistory;
import com.hsmty.cmr.domain.search.mapper.ClickEventMapper;
import com.hsmty.cmr.domain.search.mapper.FavoriteMapper;
import com.hsmty.cmr.domain.search.mapper.ItemVoteMapper;
import com.hsmty.cmr.domain.search.mapper.ResultItemMapper;
import com.hsmty.cmr.domain.search.mapper.SearchHistoryMapper;
import com.hsmty.cmr.domain.strategy.service.StrategyService;
import com.hsmty.cmr.domain.task.TaskExecutor;
import com.hsmty.cmr.domain.task.TaskService;
import com.hsmty.cmr.file.FileService;
import com.hsmty.cmr.operator.OperatorClient;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 检索策略边界：封存后赞踩只读、屏蔽条目与黑名单来源不下发
 *
 * @author cmr
 * @since 2026-11-10
 */
class SearchServicePolicyTest {

    private final SearchHistoryMapper historyMapper = mock(SearchHistoryMapper.class);
    private final ResultItemMapper resultItemMapper = mock(ResultItemMapper.class);
    private final ClickEventMapper clickEventMapper = mock(ClickEventMapper.class);
    private final ItemVoteMapper itemVoteMapper = mock(ItemVoteMapper.class);
    private final FavoriteMapper favoriteMapper = mock(FavoriteMapper.class);
    private final BlockedItemMapper blockedItemMapper = mock(BlockedItemMapper.class);
    private final IndexMapper indexMapper = mock(IndexMapper.class);
    private final DatasetMapper datasetMapper = mock(DatasetMapper.class);
    private final DatasourceBlacklistMapper blacklistMapper = mock(DatasourceBlacklistMapper.class);
    private final StrategyService strategyService = mock(StrategyService.class);
    private final OperatorClient operatorClient = mock(OperatorClient.class);
    private final TaskService taskService = mock(TaskService.class);
    private final TaskExecutor taskExecutor = mock(TaskExecutor.class);
    private final FileService fileService = mock(FileService.class);
    private final ItemMetaService itemMetaService = mock(ItemMetaService.class);
    private final SnowflakeIdWorker idWorker = new SnowflakeIdWorker(1, 1);
    private final BizNoGenerator bizNoGenerator = new BizNoGenerator();

    @Test
    void 封存任务的赞踩被拒绝() {
        CmrSearchHistory history = history("TASK-SEALED");
        history.setSealed("T");
        when(historyMapper.selectOneByQuery(any())).thenReturn(history);

        SearchService service = service();

        assertThatThrownBy(() -> service.vote("TASK-SEALED", "it-1", "up"))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("已封存");
    }

    @Test
    void 屏蔽条目与黑名单来源不再下发且命中数按实际条数() {
        CmrSearchHistory history = history("TASK-FILTER");
        history.setIndexId(null);
        when(historyMapper.selectOneByQuery(any())).thenReturn(history);
        when(resultItemMapper.selectListByQuery(any())).thenReturn(List.of(
                item(1, "it-blocked", "dataset-a"),
                item(2, "it-blacklisted", "blocked-source"),
                item(3, "it-ok", "dataset-a")));
        CmrBlockedItem blocked = new CmrBlockedItem();
        blocked.setItemId("it-blocked");
        when(blockedItemMapper.selectListByQuery(any())).thenReturn(List.of(blocked));
        CmrDatasourceBlacklist blacklist = new CmrDatasourceBlacklist();
        blacklist.setSourceName("blocked-source");
        when(blacklistMapper.selectListByQuery(any())).thenReturn(List.of(blacklist));
        when(itemMetaService.lookup(any(), any())).thenReturn(Map.of());

        Map<String, Object> summary = service().summary("TASK-FILTER");

        @SuppressWarnings("unchecked")
        List<com.hsmty.cmr.domain.search.vo.ResultItemVO> items =
                (List<com.hsmty.cmr.domain.search.vo.ResultItemVO>) summary.get("items");
        assertThat(items).extracting(com.hsmty.cmr.domain.search.vo.ResultItemVO::getItemId)
                .containsExactly("it-ok");
        assertThat(summary.get("itemCount")).isEqualTo(1);
    }

    private SearchService service() {
        return new SearchService(historyMapper, resultItemMapper, clickEventMapper, itemVoteMapper, favoriteMapper,
                blockedItemMapper, indexMapper, datasetMapper, blacklistMapper, strategyService, operatorClient,
                taskService, taskExecutor, fileService, itemMetaService, idWorker, bizNoGenerator);
    }

    private CmrSearchHistory history(String taskNo) {
        CmrSearchHistory history = new CmrSearchHistory();
        history.setId(System.nanoTime());
        history.setBizNo(taskNo);
        history.setResultSetId("RST-" + taskNo);
        history.setItemCount(3);
        history.setSealed("F");
        return history;
    }

    private CmrResultItem item(int rank, String itemId, String source) {
        CmrResultItem item = new CmrResultItem();
        item.setId(System.nanoTime() + rank);
        item.setRank(rank);
        item.setItemId(itemId);
        item.setItemName("name-" + itemId);
        item.setSource(source);
        item.setModality("text");
        return item;
    }
}
