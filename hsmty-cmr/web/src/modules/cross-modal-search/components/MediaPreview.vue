<template>
  <div class="media-preview">
    <!-- 图片 -->
    <el-image
      v-if="kind === 'image' && objectUrl"
      :src="objectUrl"
      :preview-src-list="[objectUrl]"
      fit="contain"
      class="media"
      hide-on-click-modal
    />
    <!-- 音频 -->
    <audio v-else-if="kind === 'audio' && objectUrl" :src="objectUrl" controls class="media" />
    <!-- 视频 -->
    <video v-else-if="kind === 'video' && objectUrl" :src="objectUrl" controls class="media" />
    <!-- 文本 -->
    <pre v-else-if="kind === 'text'" class="media text">{{ textContent }}</pre>
    <!-- 加载/失败 -->
    <div v-else-if="loading" class="media placeholder">
      <el-icon class="is-loading"><Loading /></el-icon> 加载中...
    </div>
    <div v-else class="media placeholder text-secondary">
      无预览（{{ ext || '未知类型' }}）
      <el-link type="primary" :underline="false" @click="download">下载查看</el-link>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, onBeforeUnmount, ref, watch } from 'vue'
import { http } from '../api/http'
import { fileUrl } from '../api'

const props = defineProps<{ path?: string | null }>()

const IMAGE = ['jpg', 'jpeg', 'png', 'gif', 'webp', 'bmp']
const AUDIO = ['mp3', 'wav', 'flac', 'ogg', 'm4a']
const VIDEO = ['mp4', 'webm', 'avi', 'mov', 'mkv']
const TEXT = ['txt', 'json', 'jsonl', 'csv', 'md']

const MAX_PREVIEW_BYTES = 64 * 1024 * 1024

const objectUrl = ref('')
const textContent = ref('')
const loading = ref(false)

const ext = computed(() => (props.path ?? '').split('.').pop()?.toLowerCase() ?? '')
const kind = computed(() => {
  if (IMAGE.includes(ext.value)) {
    return 'image'
  }
  if (AUDIO.includes(ext.value)) {
    return 'audio'
  }
  if (VIDEO.includes(ext.value)) {
    return 'video'
  }
  if (TEXT.includes(ext.value)) {
    return 'text'
  }
  return 'other'
})

async function load() {
  release()
  if (!props.path || kind.value === 'other') {
    return
  }
  loading.value = true
  try {
    const blob = await http.blob(fileUrl(props.path))
    if (blob.size > MAX_PREVIEW_BYTES) {
      loading.value = false
      return
    }
    if (kind.value === 'text') {
      textContent.value = await blob.text()
    } else {
      objectUrl.value = URL.createObjectURL(blob)
    }
  } catch {
    // 加载失败保持占位态
  } finally {
    loading.value = false
  }
}

function release() {
  if (objectUrl.value) {
    URL.revokeObjectURL(objectUrl.value)
    objectUrl.value = ''
  }
  textContent.value = ''
}

function download() {
  if (!props.path) {
    return
  }
  http.blob(fileUrl(props.path, true)).then((blob) => {
    const url = URL.createObjectURL(blob)
    const a = document.createElement('a')
    a.href = url
    a.download = props.path!.split('/').pop() ?? 'file'
    a.click()
    URL.revokeObjectURL(url)
  })
}

watch(() => props.path, load, { immediate: true })
onBeforeUnmount(release)
</script>

<style scoped>
.media-preview .media {
  width: 100%;
  max-height: 260px;
  border-radius: 4px;
  background: #f5f7fa;
}
.media.text {
  overflow: auto;
  max-height: 200px;
  font-size: 12px;
  padding: 8px;
  white-space: pre-wrap;
  word-break: break-all;
  margin: 0;
}
.media.placeholder {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  min-height: 80px;
}
</style>
