<script setup lang="ts">
import { ref } from 'vue'
import { ElMessage } from 'element-plus'
import { understandQuery, type ClarifyOption, type QueryUnderstandResult } from '../api/search'

const query = ref('')
const loading = ref(false)
const result = ref<QueryUnderstandResult | null>(null)
const selectedOption = ref<ClarifyOption | null>(null)

async function submit() {
  const text = query.value.trim()
  if (!text) {
    ElMessage.warning('请输入查询内容')
    return
  }
  loading.value = true
  selectedOption.value = null
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
  query.value = option.query
}

function reset() {
  query.value = ''
  result.value = null
  selectedOption.value = null
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
        :disabled="loading"
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
        当前接入 407 查询理解算子，用于歧义澄清与查询模态识别；408 检索执行与结果页待索引域迁移后接入。
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
        <p
          v-if="selectedOption"
          class="cms-tip"
        >
          已选择「{{ selectedOption.intent }}」，检索执行接口接入后使用改写查询「{{ selectedOption.query }}」发起检索。
        </p>
      </div>

      <el-button
        v-if="result"
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

.cms-block {
  margin-top: 16px;
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
</style>
