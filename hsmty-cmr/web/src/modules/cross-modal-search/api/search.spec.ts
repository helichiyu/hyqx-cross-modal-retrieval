import { beforeEach, describe, expect, it, vi } from 'vitest'
import { http } from '@/core/http'
import { executeSearch, understandQuery } from './search'

vi.mock('@/core/http', () => ({
  http: { post: vi.fn() },
}))

describe('检索接口', () => {
  beforeEach(() => {
    vi.mocked(http.post).mockReset()
  })

  it('查询理解提交到模块前缀下的 /search/understand', async () => {
    const expected = { needClarify: false, queryModality: 'text' }
    vi.mocked(http.post).mockResolvedValue(expected)

    await expect(understandQuery({ query: '一只狗' })).resolves.toEqual(expected)
    expect(http.post).toHaveBeenCalledWith('/search/understand', { query: '一只狗' })
  })

  it('查询理解支持查询文件路径', async () => {
    vi.mocked(http.post).mockResolvedValue({ needClarify: false, queryModality: 'image' })

    await understandQuery({ uniqueId: 'q_002', queryFile: 'runtime/407m_query_understand/input/q_002/dog.jpg' })
    expect(http.post).toHaveBeenCalledWith('/search/understand', {
      uniqueId: 'q_002',
      queryFile: 'runtime/407m_query_understand/input/q_002/dog.jpg',
    })
  })

  it('执行检索提交到模块前缀下的 /search', async () => {
    const expected = {
      searchNo: 'SRH20261111ABCDEF',
      resultSetId: 'RST20261111101546B22C45',
      query: '苹果 照片',
      count: 1,
      items: [{ rank: 1, score: 0.87, modality: 'text' }],
    }
    vi.mocked(http.post).mockResolvedValue(expected)

    await expect(executeSearch({
      query: '苹果的照片',
      clarified: true,
      clarifiedQuery: '苹果 照片',
      topK: 10,
    })).resolves.toEqual(expected)
    expect(http.post).toHaveBeenCalledWith('/search', {
      query: '苹果的照片',
      clarified: true,
      clarifiedQuery: '苹果 照片',
      topK: 10,
    })
  })
})
