package com.hsmty.cmr.domain.search.service;

import com.hsmty.cmr.domain.dataset.entity.CmrDataset;
import com.hsmty.cmr.domain.dataset.entity.CmrIndex;
import com.hsmty.cmr.domain.dataset.mapper.DatasetMapper;
import com.hsmty.cmr.domain.dataset.mapper.IndexMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * 激活索引选择规则回归：未指定场景时不能被后激活的联调/专项索引抢占。
 *
 * @author cmr
 * @since 2026-11-12
 */
@ExtendWith(MockitoExtension.class)
class SearchIndexSelectionTest {

    @Mock
    private IndexMapper indexMapper;
    @Mock
    private DatasetMapper datasetMapper;
    @InjectMocks
    private SearchService searchService;

    @Test
    void blankScenePrefersGeneralIndexEvenIfAnotherSceneActivatedLater() {
        CmrIndex e2e = index(2L, 102L);
        CmrIndex general = index(1L, 101L);
        // 按激活时间倒序，e2e 更新
        when(indexMapper.selectListByQuery(any())).thenReturn(List.of(e2e, general));
        when(datasetMapper.selectAll()).thenReturn(List.of(
                dataset(101L, "general"), dataset(102L, "e2e")));

        CmrIndex picked = invoke(null);

        assertThat(picked).isSameAs(general);
    }

    @Test
    void explicitSceneWins() {
        CmrIndex e2e = index(2L, 102L);
        CmrIndex general = index(1L, 101L);
        when(indexMapper.selectListByQuery(any())).thenReturn(List.of(e2e, general));
        when(datasetMapper.selectAll()).thenReturn(List.of(
                dataset(101L, "general"), dataset(102L, "e2e")));

        CmrIndex picked = invoke("e2e");

        assertThat(picked).isSameAs(e2e);
    }

    @Test
    void unknownSceneFallsBackToNewestActive() {
        CmrIndex e2e = index(2L, 102L);
        CmrIndex general = index(1L, 101L);
        when(indexMapper.selectListByQuery(any())).thenReturn(List.of(e2e, general));
        when(datasetMapper.selectAll()).thenReturn(List.of(
                dataset(101L, "general"), dataset(102L, "e2e")));

        CmrIndex picked = invoke("missing");

        assertThat(picked).isSameAs(e2e);
    }

    private CmrIndex invoke(String scene) {
        return ReflectionTestUtils.invokeMethod(searchService, "activeIndexOf", scene);
    }

    private static CmrIndex index(Long id, Long datasetId) {
        CmrIndex index = new CmrIndex();
        index.setId(id);
        index.setDatasetId(datasetId);
        index.setStatus(CmrIndex.STATUS_ACTIVE);
        return index;
    }

    private static CmrDataset dataset(Long id, String scene) {
        CmrDataset dataset = new CmrDataset();
        dataset.setId(id);
        dataset.setScene(scene);
        return dataset;
    }
}
