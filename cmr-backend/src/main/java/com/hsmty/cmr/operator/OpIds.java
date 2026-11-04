package com.hsmty.cmr.operator;

/**
 * 算子编号（与服务路由前缀一致）
 *
 * @author cmr
 * @since 2026-11-03
 */
public final class OpIds {

    public static final String INGEST = "401m_ingest_check";
    public static final String SCORE = "402m_dataset_score";
    public static final String PARSE = "403m_modal_parse";
    public static final String EMBED = "404m_embed";
    public static final String INDEX = "405m_index_build";
    public static final String RECALL = "406m_recall_check";
    public static final String UNDERSTAND = "407m_query_understand";
    public static final String RETRIEVAL = "408m_retrieval";
    public static final String EVALUATE = "409m_evaluate";

    private OpIds() {
    }
}
