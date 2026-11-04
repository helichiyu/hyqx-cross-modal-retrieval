<template>
  <div class="page-card">
    <el-card>
      <template #header>
        <span>跨模态检索</span>
        <span class="text-secondary" style="margin-left: 12px">
          文本直接输入；图片/音频/视频选择文件上传后检索
        </span>
      </template>

      <el-form label-width="90px" style="max-width: 860px">
        <el-form-item label="查询内容">
          <el-input
            v-model="query"
            type="textarea"
            :rows="3"
            placeholder="输入查询文本，如：海边的狗"
            :disabled="!!queryFileName"
            @input="onQueryInput"
          />
        </el-form-item>
        <el-form-item label="查询文件">
          <div>
            <el-upload
              :auto-upload="false"
              :show-file-list="false"
              :on-change="onFileChange"
              accept="image/*,audio/*,video/*"
            >
              <el-button :icon="'Upload'">
                选择文件{{ queryFileName ? `（已选：${queryFileName}）` : '' }}
              </el-button>
            </el-upload>
            <div v-if="queryFile" class="text-secondary" style="margin-top: 4px">
              已上传：{{ queryFile }}
              <el-link type="danger" :underline="false" style="margin-left: 8px" @click="clearFile">
                移除
              </el-link>
            </div>
          </div>
        </el-form-item>
        <el-form-item label="业务场景">
          <el-select v-model="scene" placeholder="全部（all）" clearable style="width: 220px">
            <el-option
              v-for="d in scenes"
              :key="d.alias"
              :label="d.label"
              :value="d.alias"
            />
          </el-select>
        </el-form-item>

        <el-collapse>
          <el-collapse-item title="高级参数（留空则使用场景生效策略）" name="adv">
            <el-form-item label="模态权重">
              <div class="weights">
                <div v-for="m in MODALITIES" :key="m.key" class="weight-row">
                  <span class="weight-label">{{ m.label }}</span>
                  <el-slider
                    v-model="weights[m.key]"
                    :min="0"
                    :max="100"
                    :step="5"
                    style="flex: 1; margin: 0 12px"
                  />
                  <span class="weight-value">{{ weights[m.key] }}</span>
                </div>
                <div class="text-secondary">当前合计：{{ weightsSum }}（须为 100，0/0/0/0 表示不覆盖）</div>
              </div>
            </el-form-item>
            <el-form-item label="重排">
              <el-switch v-model="rerank" />
              <span class="text-secondary" style="margin-left: 8px">
                {{ rerank ? '开启精排（更准，约 +3s）' : '关闭（未覆盖策略值）' }}
              </span>
            </el-form-item>
            <el-form-item label="返回条数">
              <el-input-number v-model="topK" :min="1" :max="100" />
              <span class="text-secondary" style="margin-left: 8px">0 表示不覆盖</span>
            </el-form-item>
          </el-collapse-item>
        </el-collapse>

        <el-form-item style="margin-top: 16px">
          <el-button type="primary" size="large" :loading="searching" @click="doSearch">
            <el-icon><Search /></el-icon>&nbsp;检 索
          </el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <!-- 澄清浮层（407 needClarify） -->
    <el-dialog v-model="clarifyVisible" title="查询理解：需要澄清" width="480px">
      <div class="clarify-hint">{{ clarifyHint }}</div>
      <el-button
        v-for="opt in clarifyOptions"
        :key="opt"
        style="margin: 6px"
        @click="chooseClarify(opt)"
      >
        {{ opt }}
      </el-button>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { executeSearch, suggestWords, understand, uploadFile } from '../../api'
import { useDictStore } from '../../stores/dict'
import type { DictItem } from '../../api/types'

const router = useRouter()
const dictStore = useDictStore()

type ModalityKey = 'text' | 'image' | 'audio' | 'video'

const MODALITIES: { key: ModalityKey; label: string }[] = [
  { key: 'text', label: '文本' },
  { key: 'image', label: '图片' },
  { key: 'audio', label: '音频' },
  { key: 'video', label: '视频' },
]

const query = ref('')
const scene = ref('')
const queryFile = ref('')
const queryFileName = ref('')
const weights = reactive<Record<ModalityKey, number>>({ text: 0, image: 0, audio: 0, video: 0 })
const rerank = ref(false)
const topK = ref(0)
const searching = ref(false)

const scenes = ref<DictItem[]>([])
const clarifyVisible = ref(false)
const clarifyOptions = ref<string[]>([])
const clarifyHint = ref('')

const weightsSum = computed(
  () => weights.text + weights.image + weights.audio + weights.video,
)

onMounted(async () => {
  scenes.value = await dictStore.ensure('cmr_scene')
})

// 推荐词联想（前缀匹配）
let suggestTimer: ReturnType<typeof setTimeout> | undefined
function onQueryInput() {
  clearTimeout(suggestTimer)
  if (!query.value.trim()) {
    return
  }
  suggestTimer = setTimeout(async () => {
    try {
      const words = await suggestWords(scene.value || undefined, query.value.trim())
      if (words.length) {
        ElMessage({ message: `联想：${words.join(' / ')}`, duration: 2500, grouping: true })
      }
    } catch {
      // 联想失败不打扰主流程
    }
  }, 500)
}

async function onFileChange(file: { raw?: File }) {
  const raw = file.raw
  if (!raw) {
    return
  }
  try {
    const { path } = await uploadFile(raw)
    queryFile.value = path
    queryFileName.value = raw.name
    query.value = ''
  } catch (e) {
    ElMessage.error((e as Error).message)
  }
}

function clearFile() {
  queryFile.value = ''
  queryFileName.value = ''
}

function overrides(): Record<string, unknown> {
  const body: Record<string, unknown> = {}
  if (weightsSum.value === 100) {
    body.weights = { ...weights }
  } else if (weightsSum.value !== 0) {
    throw new Error(`模态权重合计须为 100，当前 ${weightsSum.value}`)
  }
  if (topK.value > 0) {
    body.topK = topK.value
  }
  return body
}

async function doSearch() {
  if (!query.value.trim() && !queryFile.value) {
    ElMessage.warning('请输入查询文本或选择查询文件')
    return
  }
  let adv: Record<string, unknown>
  try {
    adv = overrides()
  } catch (e) {
    ElMessage.warning((e as Error).message)
    return
  }
  if (rerank.value) {
    adv.rerank = true
  }
  searching.value = true
  try {
    // 1) 先过 407 查询理解（needClarify 则弹澄清）
    const u = await understand(buildBody())
    if (u.needClarify && u.clarifyOptions.length) {
      clarifyOptions.value = u.clarifyOptions
      clarifyHint.value = u.hint || '请选择更明确的查询意图'
      clarifyVisible.value = true
      return
    }
    await run(buildBody(), adv)
  } catch (e) {
    ElMessage.error((e as Error).message)
  } finally {
    searching.value = false
  }
}

function buildBody(clarifiedQuery?: string): Record<string, unknown> {
  const body: Record<string, unknown> = {}
  if (queryFile.value) {
    body.queryFile = queryFile.value
  } else {
    body.query = query.value.trim()
  }
  if (scene.value) {
    body.scene = scene.value
  }
  if (clarifiedQuery) {
    body.clarified = true
    body.clarifiedQuery = clarifiedQuery
  }
  return body
}

async function chooseClarify(opt: string) {
  clarifyVisible.value = false
  searching.value = true
  try {
    await run(buildBody(opt), overrides())
  } catch (e) {
    ElMessage.error((e as Error).message)
  } finally {
    searching.value = false
  }
}

async function run(body: Record<string, unknown>, adv: Record<string, unknown>) {
  const result = await executeSearch({ ...body, ...adv })
  router.push(`/search/results/${result.resultSetId}`)
}
</script>

<style scoped>
.weights {
  width: 100%;
}
.weight-row {
  display: flex;
  align-items: center;
}
.weight-label {
  width: 40px;
}
.weight-value {
  width: 32px;
  text-align: right;
  font-family: Consolas, monospace;
}
.clarify-hint {
  margin-bottom: 12px;
  color: #e6a23c;
}
</style>
