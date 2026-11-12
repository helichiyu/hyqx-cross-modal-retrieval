<template>
  <div class="media-preview" @click.stop>
    <!-- 图片：宽度铺满容器，高度按原始比例自适应，超过上限才等比缩放 -->
    <img
      v-if="kind === 'image' && objectUrl"
      :src="objectUrl"
      class="media media-image"
      alt=""
    />
    <!-- 音频：只给播放条，不加展示框 -->
    <div v-else-if="kind === 'audio' && objectUrl" class="audio-bar">
      <audio :src="objectUrl" controls preload="metadata" class="media-audio" />
    </div>
    <!-- 视频：宽度铺满容器，高度随视频比例自适应 -->
    <video
      v-else-if="kind === 'video' && objectUrl"
      :src="objectUrl"
      controls
      playsinline
      preload="metadata"
      class="media media-video"
    />
    <!-- 文本：宽度固定，高度随文本长度变化，并限制上下限 -->
    <pre v-else-if="kind === 'text'" class="media text">{{ textContent }}</pre>
    <!-- 加载/失败 -->
    <div v-else-if="loading" class="media placeholder">
      <el-icon class="is-loading"><Loading /></el-icon> 加载中...
    </div>
    <div v-else class="media placeholder text-secondary">
      无预览（{{ ext || '未知类型' }}）
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, onBeforeUnmount, ref, watch } from 'vue'
import { http } from '../api/http'
import { fileUrl } from '../api'

const props = defineProps<{ path?: string | null; text?: string | null }>()

const IMAGE = ['jpg', 'jpeg', 'png', 'gif', 'webp', 'bmp']
const AUDIO = ['mp3', 'wav', 'flac', 'ogg', 'm4a']
const VIDEO = ['mp4', 'webm', 'avi', 'mov', 'mkv']
const TEXT = ['txt', 'json', 'jsonl', 'csv', 'md']

const MAX_PREVIEW_BYTES = 64 * 1024 * 1024

const objectUrl = ref('')
const textContent = ref('')
const loading = ref(false)

// 回源地址可能带查询串/锚点，取扩展名时先剥离；文本条目直接按 item.text 展示
const cleanPath = computed(() => {
  const raw = props.path ?? ''
  if (/^https?:\/\//i.test(raw)) {
    try {
      return new URL(raw).pathname
    } catch {
      return raw.split(/[?#]/)[0]
    }
  }
  return raw.split(/[?#]/)[0]
})
const ext = computed(() => cleanPath.value.split('.').pop()?.toLowerCase() ?? '')
const kind = computed(() => {
  if (props.text && props.text.trim()) {
    return 'text'
  }
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
  if (kind.value === 'text' && props.text) {
    textContent.value = props.text
    return
  }
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

watch(() => [props.path, props.text], load, { immediate: true })
onBeforeUnmount(release)
</script>

<style scoped>
.media-preview {
  width: 100%;
}
/*
 * 图片/视频：宽度固定铺满，高度由原始比例决定，超过 --preview-media-max 才等比缩放。
 * 不再给外层写死 height，媒体有多高就把下方文本往下挤多远。
 */
.media-preview .media {
  display: block;
  box-sizing: border-box;
  width: 100%;
  height: auto;
  max-width: 100%;
  max-height: var(--preview-media-max, 320px);
  object-fit: contain;
  border-radius: 4px;
  background: transparent;
}
.media-image {
  cursor: zoom-in;
}
/* 文本：宽度固定，高度随内容变化，并设置上下限 */
.media.text {
  min-height: 96px;
  max-height: var(--preview-text-max, 360px);
  overflow: auto;
  font-size: 12px;
  line-height: 1.65;
  padding: 10px 12px;
  white-space: pre-wrap;
  word-break: break-word;
  margin: 0;
  background: #f8fafc;
}
/* 音频：只要一条播放条，不要外框 */
.audio-bar {
  display: flex;
  align-items: center;
  width: 100%;
  padding: 4px 0;
  background: transparent;
}
.media-audio {
  display: block;
  flex: 1;
  width: 100%;
  min-width: 0;
  background: transparent;
}
.media.placeholder {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  min-height: 120px;
}
</style>
