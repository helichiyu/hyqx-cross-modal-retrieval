package com.hsmty.cmr.domain.operation.service;

import com.hsmty.cmr.common.api.PageQuery;
import com.hsmty.cmr.common.api.PageResult;
import com.hsmty.cmr.common.exception.BizException;
import com.hsmty.cmr.common.id.BizNoGenerator;
import com.hsmty.cmr.common.id.SnowflakeIdWorker;
import com.hsmty.cmr.domain.operation.dto.WordSaveDTO;
import com.hsmty.cmr.domain.operation.entity.CmrRecommendWord;
import com.hsmty.cmr.domain.operation.mapper.RecommendWordMapper;
import com.hsmty.cmr.domain.operation.vo.WordVO;
import com.mybatisflex.core.paginate.Page;
import com.mybatisflex.core.query.QueryWrapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

import static com.hsmty.cmr.domain.operation.entity.table.CmrRecommendWordTableDef.CMR_RECOMMEND_WORD;

/**
 * 推荐词服务：检索台联想（enabled 词按 sort）+ 运营维护（开关即时生效）
 *
 * @author cmr
 * @since 2026-11-03
 */
@Service
@RequiredArgsConstructor
public class WordService {

    private final RecommendWordMapper wordMapper;
    private final SnowflakeIdWorker idWorker;
    private final BizNoGenerator bizNoGenerator;

    /** 联想：启用 + 场景匹配(all 或精确) + 前缀，按 sort */
    public List<String> suggest(String scene, String prefix) {
        com.mybatisflex.core.query.QueryCondition sceneCond = CMR_RECOMMEND_WORD.SCENE.eq("all");
        if (scene != null && !scene.isBlank()) {
            sceneCond = sceneCond.or(CMR_RECOMMEND_WORD.SCENE.eq(scene));
        }
        QueryWrapper qw = QueryWrapper.create()
                .where(CMR_RECOMMEND_WORD.ENABLED.eq(true).and(sceneCond))
                .orderBy(CMR_RECOMMEND_WORD.SORT.asc())
                .limit(10);
        if (prefix != null && !prefix.isBlank()) {
            qw.where(CMR_RECOMMEND_WORD.WORD.like(prefix + "%"));
        }
        return wordMapper.selectListByQuery(qw).stream().map(CmrRecommendWord::getWord).toList();
    }

    public PageResult<WordVO> page(PageQuery query, String keyword) {
        QueryWrapper qw = QueryWrapper.create().from(CMR_RECOMMEND_WORD);
        if (keyword != null && !keyword.isBlank()) {
            qw.where(CMR_RECOMMEND_WORD.WORD.like(keyword));
        }
        qw.orderBy(CMR_RECOMMEND_WORD.SORT.asc()).orderBy(CMR_RECOMMEND_WORD.ID.desc());
        Page<CmrRecommendWord> page = wordMapper.paginate(
                Math.max(query.getPageNum(), 1), Math.max(query.getPageSize(), 1), qw);
        return PageResult.of(page.getRecords().stream().map(WordVO::from).toList(),
                page.getTotalRow(), query.getPageNum(), query.getPageSize());
    }

    @Transactional(rollbackFor = Exception.class)
    public WordVO create(WordSaveDTO dto) {
        Long exists = wordMapper.selectCountByQuery(QueryWrapper.create()
                .where(CMR_RECOMMEND_WORD.WORD.eq(dto.getWord())));
        BizException.assertTrue(exists != null && exists > 0, "推荐词已存在");
        CmrRecommendWord word = new CmrRecommendWord();
        word.setId(idWorker.next());
        word.setBizNo(bizNoGenerator.next(BizNoGenerator.PREFIX_WORD));
        word.setWord(dto.getWord());
        word.setScene(dto.getScene() == null || dto.getScene().isBlank() ? "all" : dto.getScene());
        word.setEnabled(!Boolean.FALSE.equals(dto.getEnabled()));
        word.setSort(dto.getSort() == null ? 0 : dto.getSort());
        word.setDelFlag("F");
        word.setCreateTime(LocalDateTime.now());
        wordMapper.insert(word, true);
        return WordVO.from(word);
    }

    @Transactional(rollbackFor = Exception.class)
    public WordVO update(Long id, WordSaveDTO dto) {
        CmrRecommendWord word = requireWord(id);
        if (!word.getWord().equals(dto.getWord())) {
            Long exists = wordMapper.selectCountByQuery(QueryWrapper.create()
                    .where(CMR_RECOMMEND_WORD.WORD.eq(dto.getWord()).and(CMR_RECOMMEND_WORD.ID.ne(id))));
            BizException.assertTrue(exists != null && exists > 0, "推荐词已存在");
            word.setWord(dto.getWord());
        }
        word.setScene(dto.getScene() == null || dto.getScene().isBlank() ? "all" : dto.getScene());
        word.setEnabled(!Boolean.FALSE.equals(dto.getEnabled()));
        word.setSort(dto.getSort() == null ? 0 : dto.getSort());
        wordMapper.update(word);
        return WordVO.from(word);
    }

    @Transactional(rollbackFor = Exception.class)
    public void remove(Long id) {
        BizException.assertNull(requireWord(id), "推荐词不存在");
        wordMapper.deleteById(id);
    }

    private CmrRecommendWord requireWord(Long id) {
        return wordMapper.selectOneById(id);
    }
}
