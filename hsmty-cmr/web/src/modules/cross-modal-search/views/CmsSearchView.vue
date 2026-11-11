<script setup lang="ts">
import { computed, ref } from 'vue'
import { ElMessage } from 'element-plus'
import {
  executeSearch,
  understandQuery,
  type ClarifyOption,
  type QueryUnderstandResult,
  type SearchResult,
} from '../api/search'
import { MODALITY_LABELS } from '../dict'

const query = ref('')
const loading = ref(false)
const searching = ref(false)
const result = ref<QueryUnderstandResult | null>(null)
const selectedOption = ref<ClarifyOption | null>(null)
const searchResult = ref<SearchResult | null>(null)

const canSearch = computed(() => {
  if (!query.value.trim()) {
    return false
  }
  return !result.value?.needClarify || selectedOption.value !== null
})

async function submit() {
  const text = query.value.trim()
  if (!text) {
    ElMessage.warning('请输入查询内容')
    return
  }
  loading.value = true
  selectedOption.value = null
  searchResult.value = null
  try {
    result.value = await understandQuery({ query: text })
  } catch (e) {
    result.value = null
    ElMessage.error(e instanceof Error ? e.message : '查询理解失败')
  } finally {
    loading.value = false
  }
}

function choose(option: ClarifyOption) {
  selectedOption.value = option
  searchResult.value = null
}

async function search() {
  const text = query.value.trim()
  if (!text) {
    ElMessage.warning('请输入查询内容')
    return
  }
  if (result.value?.needClarify && !selectedOption.value) {
    ElMessage.warning('请先选择要查询的含义')
    return
  }
  searching.value = true
  try {
    searchResult.value = await executeSearch({
      query: text,
      clarified: selectedOption.value !== null,
      clarifiedQuery: selectedOption.value?.query,
    })
  } catch (e) {
    searchResult.value = null
    ElMessage.error(e instanceof Error ? e.message : '检索失败')
  } finally {
    searching.value = false
  }
}

function reset() {
  query.value = ''
  result.value = null
  selectedOption.value = null
  searchResult.value = null
}

function modalityLabel(modality?: string) {
  return modality ? MODALITY_LABELS[modality] ?? modality : '未知模态'
}

function modalityTag(modality?: string) {
  switch (modality) {
    case 'image':
      return 'success'
    case 'audio':
      return 'warning'
    case 'video':
      return 'danger'
    default:
      return 'info'
  }
}
</script>

<template>
  <div>
    <h2 class="cms-page-title">
      跨模态检索
    </h2>
    <el-card shadow="never">
      <el-input
        v-model="query"
        placeholder="输入文本描述，检索图像 / 音频 / 视频"
        clearable
        size="large"
        :disabled="loading || searching"
        @keyup.enter="submit"
      >
        <template #append>
          <el-button
            type="primary"
            :loading="loading"
            @click="submit"
          >
            查询理解
          </el-button>
        </template>
      </el-input>

      <p class="cms-tip">
        407 查询理解用于歧义澄清与查询模态识别；确认后执行 408 检索，检索历史会落库。
      </p>

      <el-alert
        v-if="result?.hint"
        class="cms-block"
        :title="result.hint"
        type="warning"
        :closable="false"
        show-icon
      />

      <div
        v-if="result && !result.hint"
        class="cms-block"
      >
        <el-descriptions
          :column="3"
          border
          size="small"
        >
          <el-descriptions-item label="是否需要澄清">
            {{ result.needClarify ? '是' : '否' }}
          </el-descriptions-item>
          <el-descriptions-item label="查询模态">
            {{ result.queryModality || '-' }}
          </el-descriptions-item>
          <el-descriptions-item label="命中的歧义词">
            {{ result.ambiguousTerm || '-' }}
          </el-descriptions-item>
          <el-descriptions-item
            label="归一化查询"
            :span="3"
          >
            {{ result.normalizedQuery || query }}
          </el-descriptions-item>
        </el-descriptions>
      </div>

      <div
        v-if="result?.needClarify && result.clarifyOptions?.length"
        class="cms-block"
      >
        <p class="cms-section-title">
          请选择你要查询的含义
        </p>
        <el-space
          wrap
          alignment="flex-start"
        >
          <el-card
            v-for="option in result.clarifyOptions"
            :key="option.query"
            class="cms-option"
            :class="{ 'cms-option--active': selectedOption?.query === option.query }"
            shadow="hover"
            @click="choose(option)"
          >
            <div class="cms-option__intent">
              {{ option.intent }}
            </div>
            <div class="cms-option__query">
              {{ option.query }}
            </div>
            <div
              v-if="option.hint"
              class="cms-option__hint"
            >
              {{ option.hint }}
            </div>
          </el-card>
        </el-space>
      </div>

      <div
        v-if="result && !result.hint"
        class="cms-block cms-actions"
      >
        <el-button
          type="primary"
          :loading="searching"
          :disabled="!canSearch"
          @click="search"
        >
          开始检索
        </el-button>
        <span
          v-if="result.needClarify && !selectedOption"
          class="cms-tip cms-tip--inline"
        >
          先选择澄清选项
        </span>
      </div>

      <div
        v-if="searchResult"
        class="cms-block"
      >
        <el-divider content-position="left">
          检索结果
        </el-divider>
        <el-descriptions
          :column="4"
          border
          size="small"
        >
          <el-descriptions-item label="结果集编号">
            {{ searchResult.resultSetId }}
          </el-descriptions-item>
          <el-descriptions-item label="检索编号">
            {{ searchResult.searchNo }}
          </el-descriptions-item>
          <el-descriptions-item label="结果数">
            {{ searchResult.count }}
          </el-descriptions-item>
          <el-descriptions-item label="耗时">
            {{ searchResult.elapsedMs ?? '-' }} ms
          </el-descriptions-item>
          <el-descriptions-item
            label="实际查询"
            :span="4"
          >
            {{ searchResult.query }}
          </el-descriptions-item>
        </el-descriptions>

        <el-empty
          v-if="!searchResult.items?.length"
          description="没有命中结果"
        />
        <div
          v-for="item in searchResult.items"
          :key="item.itemId"
          class="cms-result"
        >
          <div class="cms-result__head">
            <span class="cms-result__rank">#{{ item.rank }}</span>
            <el-tag
              :type="modalityTag(item.modality)"
              size="small"
            >
              {{ modalityLabel(item.modality) }}
            </el-tag>
            <span class="cms-result__score">融合分 {{ item.score ?? '-' }}</span>
            <span
              v-if="item.alignScore !== undefined"
              class="cms-result__score"
            >
              对齐分 {{ item.alignScore }}
            </span>
          </div>
          <div
            v-if="item.text"
            class="cms-result__text"
          >
            {{ item.text }}
          </div>
          <div class="cms-result__meta">
            <span>{{ item.sourceFile || item.itemId }}</span>
            <span v-if="item.source">来源：{{ item.source }}</span>
          </div>
        </div>
      </div>

      <el-button
        v-if="result || searchResult"
        class="cms-block"
        @click="reset"
      >
        重置
      </el-button>
    </el-card>
  </div>
</template>

<style scoped>
.cms-tip {
  margin-top: 16px;
  color: var(--el-text-color-secondary);
  font-size: 13px;
}

.cms-tip--inline {
  margin-top: 0;
  margin-left: 12px;
}

.cms-block {
  margin-top: 16px;
}

.cms-actions {
  display: flex;
  align-items: center;
}

.cms-section-title {
  margin: 0 0 12px;
  font-weight: 600;
}

.cms-option {
  width: 260px;
  cursor: pointer;
}

.cms-option--active {
  border-color: var(--el-color-primary);
}

.cms-option__intent {
  font-weight: 600;
}

.cms-option__query {
  margin-top: 6px;
  color: var(--el-color-primary);
}

.cms-option__hint {
  margin-top: 4px;
  color: var(--el-text-color-secondary);
  font-size: 12px;
}

.cms-result {
  margin-top: 12px;
  padding: 12px 16px;
  border: 1px solid var(--el-border-color-light);
  border-radius: 6px;
}

.cms-result__head {
  display: flex;
  align-items: center;
  gap: 12px;
}

.cms-result__rank {
  font-weight: 600;
  color: var(--el-color-primary);
}

.cms-result__score {
  color: var(--el-text-color-secondary);
  font-size: 12px;
}

.cms-result__text {
  margin-top: 8px;
  line-height: 1.6;
}

.cms-result__meta {
  display: flex;
  gap: 16px;
  margin-top: 8px;
  color: var(--el-text-color-secondary);
  font-size: 12px;
}
</style>
