# -*- coding: utf-8 -*-
"""生成【工具清单】样式的跨模态检索算子清单 xlsx（仿后训练4条产线工具清单 V2 的表式）。

用法：D:/anaconda3/python.exe scripts/gen_operator_list_xlsx.py [输出路径]
不带参数时输出到 docs/【工具清单】1109 跨模态检索算子清单V1.xlsx。
"""
import sys
from pathlib import Path

from openpyxl import Workbook
from openpyxl.styles import Alignment, Border, Font, PatternFill, Side
from openpyxl.utils import get_column_letter

HEADERS = [
    "#", "算子ID", "大环节", "算子名称", "算子类型", "能力说明", "流程是否可跳过",
    "处理逻辑", "前置依赖", "输入", "输出", "依赖模型", "失败处理", "约束边界",
    "资源要求", "性能实测", "接口路径", "完成状态｜调用场景",
]

# 每行：算子ID, 大环节, 名称, 类型, 能力说明, 可跳过, 处理逻辑, 前置依赖, 输入, 输出,
#        依赖模型, 失败处理, 约束边界, 资源要求, 性能实测, 接口路径, 状态场景
ROWS = [
    (
        "401m_ingest_check", "①数据建库链路\n(401→406)", "数据接入校验", "批量处理",
        "对接入数据集逐文件两级校验：读文件头魔数识别真实格式并与扩展名比对（记格式伪装），图片/音视频/文本做可解码性验证；与随附 metadata.json 清单对账，产出可解码率、模态覆盖率、数量一致率三项客观指标。",
        "不可跳过（建库首环）",
        "遍历目录按扩展名归类四模态→魔数比对扩展名→图片 Pillow 解码、音视频 FFmpeg 试解码、文本 UTF-8 读取验证→与清单对账→指标写 report.json，未通过文件及原因写 failures.json，逐文件追加血缘 lineage.jsonl。",
        "无（数据集目录经 /uploadFile 上传，支持 zip 自动解压）",
        "数据集目录（文本/图片/音频/视频四类文件）",
        "report.json、failures.json、lineage.jsonl",
        "无",
        "未通过校验的文件写入 failures.json 并记录原因，不阻断整批",
        "只产出客观校验指标，通过/挂起/剔除结论由 402 评定算子给出",
        "CPU≥2核；无GPU；内存≥2GB；暂存空间与数据集等量",
        "样例集（4 文件）单轮约 0.5s，三项指标与人工核对一致",
        "/401m_ingest_check/predict",
        "已交付｜P4 建库 401 环节",
    ),
    (
        "402m_dataset_score", "", "数据集评定", "批量处理",
        "对 401 校验报告的三项指标加权求和得综合评分，按通过线/缓议线给出 pass / suspend / reject 三档判定。",
        "不可跳过",
        "读 report.json 提取三项指标→合并权重（默认可解码率0.5、模态覆盖率0.3、数量一致率0.2，可请求覆盖且合计必须为1）→加权评分→对照通过线0.8、缓议线0.5 判定→评分/权重/阈值/结论写 score.json。",
        "401 report.json",
        "report.json（可选 weights、passLine、suspendLine，省略用默认）",
        "score.json（评分、权重、阈值、判定）",
        "无",
        "权重合计≠1 时拒绝执行",
        "只做质量评定不重跑校验；权重与阈值可按项目覆盖",
        "CPU≥1核；无GPU；内存≥512MB",
        "单次约 0.3s，三档判定分支经端到端验证",
        "/402m_dataset_score/predict",
        "已交付｜P4 建库 402 环节（suspend 转人工裁决）",
    ),
    (
        "403m_modal_parse", "", "模态内容解析", "批量处理",
        "将四模态文件切成可独立检索单元：文本按 500 字分段（相邻段重叠 50 字）、图片一图一单元、音频按 30 秒切片（转 48kHz 单声道 WAV）、视频每秒抽 1 帧；每单元分配唯一 itemId 并记录来源文件与定位，供检索结果回溯原文位置。",
        "不可跳过",
        "按扩展名识别四类文件→文本分段/音频切片/视频抽帧→全部单元逐行写 items.jsonl（含 sourceFile、locator、text）→逐文件追加血缘 lineage.jsonl。",
        "401 通过校验的数据集目录",
        "数据集目录",
        "items.jsonl、lineage.jsonl",
        "无（音视频解码使用 FFmpeg）",
        "单文件解析失败不影响整批，血缘逐文件留痕",
        "只切片取检索单元，不做内容质量评价",
        "CPU≥4核（抽帧计算密集）；无GPU；内存≥4GB；预留数据集两倍空间",
        "样例集单轮约 0.5s，产出 12 个单元、四模态全覆盖",
        "/403m_modal_parse/predict",
        "已交付｜P4 建库 403 环节",
    ),
    (
        "404m_embed", "", "多模态向量化", "批量处理+在线服务",
        "双通道向量化：文本/图片/视频帧走 Chinese-CLIP ViT-B/16，音频走 CLAP HTSAT（文本查音频场景用 CLAP 文本塔），全部 L2 归一化（余弦相似度等价内积）；模型懒加载单例驻留内存。",
        "不可跳过（建库与在线检索均依赖）",
        "批量模式读 items.jsonl 按模态分组→分通道编码→L2 归一化→逐行写 embeddings.jsonl 并记录模型版本；在线模式接收 queryText / queryImagePath / queryAudioPath 之一（channel=clap 切 CLAP 文本塔），响应内直接返回向量。",
        "403 items.jsonl（批量）；查询文本或查询文件（在线）",
        "items.jsonl；或 queryText、queryImagePath、queryAudioPath 之一",
        "embeddings.jsonl（512 维向量+模型版本）；或响应内 vector",
        "Chinese-CLIP ViT-B/16（权重约1.4GB）；CLAP HTSAT（约1.2GB）；均输出 512 维",
        "——",
        "只编码不检索；模型版本随产物落盘，供 405/408 做版本一致性校验",
        "CPU≥4核；GPU可选（缩短批量耗时）；内存≥8GB（加载后约3GB）；权重约2.6GB",
        "样例 12 单元批量约 1.6s（CPU）；在线单条文本 <1s",
        "/404m_embed/predict",
        "已交付｜P4 建库 404 环节；在线被 406/408 调用",
    ),
    (
        "405m_index_build", "", "向量索引构建", "批量处理",
        "按通道分建 FAISS IndexFlatIP（clip：文本/图片/视频帧；clap：音频），IndexIDMap2 以元数据行号作向量标识；单元元数据存索引目录 SQLite meta.db；index_meta.json 记录模型版本。",
        "不可跳过（产出可检索索引）",
        "读 embeddings.jsonl 与 items.jsonl 按 itemId 对齐→校验与已有索引模型版本一致（不一致拒绝构建）→分通道写 FAISS 索引→元数据写 meta.db 的 items 表→模型版本与统计写 index_meta.json。",
        "404 embeddings.jsonl（+403 items.jsonl）",
        "embeddings.jsonl、items.jsonl（可选）、indexDir（可选，省略写默认目录）",
        "索引目录（index_clip.faiss、index_clap.faiss、meta.db、index_meta.json）",
        "无（FAISS IndexFlatIP 精确检索）",
        "同一索引重复构建且模型版本不一致时拒绝",
        "IndexFlatIP 与暴力枚举结果一致；向量模型更换后旧索引自动失效，防跨版本混用",
        "CPU≥2核；无GPU；内存≥2GB（百万级按每百万约+2GB）；磁盘每百万条512维约2GB（单通道）",
        "样例 12 单元约 0.4s",
        "/405m_index_build/predict",
        "已交付｜P4 建库 405 环节；meta.db 兼作检索日志名称回查源（05 口径）",
    ),
    (
        "406m_recall_check", "", "投产召回率校验", "批量处理",
        "建库完成、投产之前，用抽检查询集对索引回放校验：逐条向量化后在 clip 通道检索，按 itemId 或来源文件判命中，汇总命中率与阈值比较给出 pass/fail，未达标阻止投产。",
        "不可跳过（投产门禁）",
        "读 evalFile（每行 query+answers）→逐条调 404 向量化→在指定索引 clip 通道检索前 topK→判命中→汇总命中率对阈值（默认0.8）→逐条明细与结论写 recall.json。",
        "405 索引目录 + 抽检查询集",
        "indexDir、evalFile、threshold（默认0.8）、topK（均可省略）",
        "recall.json（逐条命中明细+结论）",
        "间接依赖 404（查询向量化）",
        "未达标判 fail → 阻止投产，转后端「人工确认投产」流程",
        "只校验 clip 通道；结论只对当前索引版本负责",
        "CPU≥2核；无GPU；内存≥2GB",
        "样例 3 条查询约 1.6s，命中率 1.0",
        "/406m_recall_check/predict",
        "已交付｜P4 建库 406 环节（P4「确认投产」按钮依据）",
    ),
    (
        "407m_query_understand", "②在线检索链路\n(407→408)", "查询理解", "在线服务",
        "检索前置判断：查询命中歧义词典时返回澄清选项（意图说明+改写后查询，前端浮层供选择）；查询长度不足 3 字符返回补充提示不进检索；输入查询文件时按扩展名识别四类查询模态。",
        "可跳过（无歧义且描述完整时直接检索）",
        "接收 query 或 queryFile 二选一→查询文本查歧义词典（configs/ambiguity.json 外置配置）→命中生成澄清选项并置澄清标记→未命中且<3字返回补充提示→正常查询返回澄清标记为否及查询模态。",
        "无",
        "query 或 queryFile",
        "needClarify、clarifyOptions[]（intent/query/hint）、queryModality、hint",
        "无（歧义词典外置，不停机即可扩充词条）",
        "——",
        "只做前置判断不执行检索",
        "CPU≥1核；无GPU；内存≥512MB；词典<1MB",
        "单次 <0.4s；正常/歧义/过短/文件模态四类分支全覆盖",
        "/407m_query_understand/predict",
        "已交付｜P1 检索台检索前澄清",
    ),
    (
        "408m_retrieval", "", "检索服务", "在线服务",
        "在线多模态检索主链路：按查询模态定召回通道（文本同时走 clip+clap 双通道，图片/视频走 clip，音频走 clap），各通道召回目标条数 5 倍候选，通道内 z-score 归一取非负，同条目多通道命中保留 z 最高者；按四模态策略权重（合计100）加权融合并叠加精确词覆盖加成（保发票号/错误码类精确词）；可选 bge-reranker 精排；按总分降序输出 topK 并生成结果集编号。",
        "不可跳过（检索主链路）",
        "校验查询/索引目录/权重→比对索引模型版本（不一致拒绝）→调 404 生成查询向量→双通道 FAISS 召回→z-score 归一融合+精确词加成→开启精排时对前 rerank_top_n 条含文本条目联合打分重排→截取 topK 生成 RST 与策略快照返回。",
        "405 激活索引（查询向量经 404 在线生成）",
        "query 或 queryFile、indexDir、topK、weights（合计100）、rerank（均可省略用服务默认）",
        "RST 结果集（resultSetId、items[rank/score/modality/itemId/sourceFile/locator/text]、strategy 快照、elapsedMs）",
        "Chinese-CLIP、CLAP（经 404）；bge-reranker-v2-m3（可选精排，权重约2.2GB）",
        "索引模型版本不一致拒绝检索；权重合计≠100 拒绝",
        "只检索不落库——检索日志按 05 口径由后端只存 ID+分数，名称实时回查；sourceFile/locator 为逻辑路径，调用方挂共享卷直读",
        "CPU≥4核；GPU可选（显著缩短精排）；内存≥8GB",
        "样例索引关精排约 0.7s / 开精排约 2.0s（CPU）",
        "/408m_retrieval/search（四协议 /predict 同逻辑）",
        "已交付｜P1/P2 检索主链路；被 409 调用",
    ),
    (
        "409m_evaluate", "③质量评测链路\n(409)", "评测执行", "批量处理",
        "对评测集逐条调 408 检索，计算 recall@K / precision@K / nDCG@K 三项指标（同一答案多个条目只计一次，nDCG 按命中位置折损归一），对照验收线给 pass/fail；同一索引自动与上次评测对比，给出各指标差值与是否提升，供策略调参对比。",
        "条件启用（评测/调参对比时）",
        "读 evalFile（每行 query+answers，答案为 itemId 或来源文件名）→逐条调 408 取前 K 条→算单条三指标→全集平均→对照验收线（lines 置空的指标不卡线）→追加历史记录并与同索引上次对比→指标/明细/对比写 eval_report.json。",
        "405 索引目录 + 评测集",
        "indexDir、evalFile、k、lines、weights、rerank（weights/rerank 为评测对象策略参数，传入则全程按该策略检索，可对比生效与草稿策略）",
        "eval_report.json、history.jsonl（差值+improved）",
        "间接依赖 408（执行检索）",
        "未达标输出未达标指标清单（failedLines），结论 fail",
        "只评检索质量不做训练；同索引首次评测 comparison 为 null",
        "CPU≥2核；无GPU；内存≥2GB",
        "样例 3 条约 3.8s（recall@10=1.0）",
        "/409m_evaluate/predict",
        "已交付｜P8 评测执行（策略版本对比）",
    ),
]

FOOTER = (
    "部署形态：9 算子合并交付单容器 cmr-operators:1.0，对外唯一端口 6300（/healthz 健康检查）；"
    "inputFile / queryFile / indexDir 与返回的 sourceFile、locator.path 均为项目根相对逻辑路径"
    "（runtime/...、dataset/...），调用方按同名目录挂载共享卷即可直读。"
    "联调细节见 docs/前后端对接说明.md 与 docs/接口调用示例.md。生成时间 2026-11-09。"
)

HEADER_FILL = PatternFill("solid", fgColor="305496")
GROUP_FILL = PatternFill("solid", fgColor="D9E2F3")
THIN = Side(style="thin", color="9AA5B8")
BORDER = Border(left=THIN, right=THIN, top=THIN, bottom=THIN)


def build(output: Path) -> None:
    wb = Workbook()
    ws = wb.active
    ws.title = "跨模态检索算子"

    for col, header in enumerate(HEADERS, start=1):
        cell = ws.cell(row=1, column=col, value=header)
        cell.font = Font(bold=True, color="FFFFFF", size=11)
        cell.fill = HEADER_FILL
        cell.alignment = Alignment(horizontal="center", vertical="center", wrap_text=True)
        cell.border = BORDER

    for r, row in enumerate(ROWS, start=2):
        ws.cell(row=r, column=1, value=r - 1)
        for c, value in enumerate(row, start=2):
            ws.cell(row=r, column=c, value=value)
        for c in range(1, len(HEADERS) + 1):
            cell = ws.cell(row=r, column=c)
            cell.font = Font(size=10)
            cell.alignment = Alignment(vertical="top", wrap_text=True)
            cell.border = BORDER

    # 大环节列（C）按组竖向合并
    groups: list[tuple[int, int]] = []
    start = 2
    for r in range(2, len(ROWS) + 3):
        value = ws.cell(row=r, column=3).value if r <= len(ROWS) + 1 else None
        if value:
            if r > start:
                groups.append((start, r - 1))
            start = r
    for top, bottom in groups:
        if bottom > top:
            ws.merge_cells(start_row=top, start_column=3, end_row=bottom, end_column=3)
        for r in range(top, bottom + 1):
            cell = ws.cell(row=r, column=3)
            cell.fill = GROUP_FILL
            cell.font = Font(bold=True, size=10)
            cell.alignment = Alignment(horizontal="center", vertical="center", wrap_text=True)

    widths = [5, 17, 13, 13, 11, 52, 13, 58, 20, 30, 30, 26, 24, 32, 26, 22, 26, 24]
    for col, width in enumerate(widths, start=1):
        ws.column_dimensions[get_column_letter(col)].width = width

    note_row = len(ROWS) + 3
    ws.merge_cells(start_row=note_row, start_column=1, end_row=note_row, end_column=len(HEADERS))
    note = ws.cell(row=note_row, column=1, value=FOOTER)
    note.font = Font(size=9, color="595959")
    note.alignment = Alignment(vertical="top", wrap_text=True)
    ws.row_dimensions[note_row].height = 42

    ws.freeze_panes = "D2"
    ws.auto_filter.ref = f"A1:{get_column_letter(len(HEADERS))}{len(ROWS) + 1}"

    output.parent.mkdir(parents=True, exist_ok=True)
    wb.save(output)
    print(f"written: {output} ({output.stat().st_size} bytes, {len(ROWS)} rows)")


if __name__ == "__main__":
    default = Path(__file__).resolve().parent.parent / "docs" / "【工具清单】1109 跨模态检索算子清单V1.xlsx"
    build(Path(sys.argv[1]) if len(sys.argv) > 1 else default)
