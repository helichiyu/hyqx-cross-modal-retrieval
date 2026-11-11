<template>
  <Teleport to="body">
    <div class="dev-mask" :class="{ open: modelValue }" @click="close" />
    <div
      class="dev-drawer"
      :class="{ open: modelValue }"
      role="dialog"
      aria-modal="true"
      :aria-label="title"
    >
      <div class="dev-head">
        <h3>{{ title }}</h3>
        <span class="dh-tag">给研发</span>
        <button type="button" class="dev-close" title="关闭（Esc）" aria-label="关闭" @click="close">×</button>
      </div>
      <div class="dev-body">
        <slot />
      </div>
    </div>
  </Teleport>
</template>

<script setup lang="ts">
import { onBeforeUnmount, watch } from 'vue'

/**
 * 原型 v5 九页统一的「研发说明」右侧抽屉。
 *
 * <p>仅承载研发对照说明，不参与业务操作；各页通过默认插槽传入本页说明内容。
 */
const props = withDefaults(
  defineProps<{
    modelValue: boolean
    title?: string
  }>(),
  { title: '研发需求说明' },
)

const emit = defineEmits<{ (e: 'update:modelValue', value: boolean): void }>()

function close() {
  emit('update:modelValue', false)
}

function onKeydown(event: KeyboardEvent) {
  if (event.key === 'Escape') {
    close()
  }
}

watch(
  () => props.modelValue,
  (open) => {
    if (open) {
      document.addEventListener('keydown', onKeydown)
    } else {
      document.removeEventListener('keydown', onKeydown)
    }
  },
)

onBeforeUnmount(() => document.removeEventListener('keydown', onKeydown))
</script>

<style scoped>
.dev-mask {
  position: fixed;
  inset: 0;
  z-index: 10000;
  background: rgba(15, 23, 42, 0.45);
  opacity: 0;
  pointer-events: none;
  transition: opacity 0.22s;
}
.dev-mask.open { opacity: 1; pointer-events: auto; }

.dev-drawer {
  position: fixed;
  top: 0;
  right: 0;
  z-index: 10001;
  display: flex;
  flex-direction: column;
  width: 720px;
  max-width: 100vw;
  height: 100vh;
  background: #f6f8fc;
  box-shadow: -8px 0 30px rgba(15, 23, 42, 0.18);
  transform: translateX(102%);
  transition: transform 0.28s ease;
}
.dev-drawer.open { transform: translateX(0); }

.dev-head {
  display: flex;
  align-items: center;
  gap: 10px;
  flex: 0 0 auto;
  padding: 14px 18px;
  background: #fff;
  border-bottom: 1px solid #e4eaf3;
}
.dev-head h3 { margin: 0; font-size: 15px; color: #1f2937; }
.dh-tag {
  padding: 2px 8px;
  border-radius: 4px;
  color: #b54708;
  background: #fdf3e6;
  font-size: 11px;
  font-weight: 600;
}
.dev-close {
  flex: 0 0 auto;
  width: 28px;
  height: 28px;
  margin-left: auto;
  border: none;
  border-radius: 6px;
  background: #f2f4f8;
  color: #475467;
  font-size: 15px;
  line-height: 1;
  cursor: pointer;
}
.dev-close:hover { background: #e6eaf2; }

.dev-body {
  flex: 1;
  min-height: 0;
  overflow: auto;
  padding: 16px 18px 36px;
}

.dev-body :deep(.help-lead) {
  margin: 0 0 12px;
  padding: 9px 12px;
  border: 1px dashed #cdd9f5;
  border-radius: 8px;
  background: var(--system-blue-weak);
  color: #475467;
  font-size: 12.5px;
  line-height: 1.75;
}
.dev-body :deep(.hb-t) {
  display: flex;
  align-items: center;
  gap: 7px;
  margin-bottom: 6px;
  color: #1f2a44;
  font-size: 12.5px;
  font-weight: 700;
}
.dev-body :deep(.hb-t)::before {
  content: '';
  width: 3px;
  height: 12px;
  border-radius: 2px;
  background: var(--system-blue);
}
.dev-body :deep(.help-table) { width: 100%; border-collapse: collapse; font-size: 11.5px; }
.dev-body :deep(.help-table th) {
  background: #f4f6fb;
  color: #7a8493;
  padding: 7px 9px;
  text-align: left;
  font-size: 10.5px;
  font-weight: 600;
}
.dev-body :deep(.help-table td) {
  padding: 7px 9px;
  border-bottom: 1px solid #eef2f8;
  color: #344054;
  vertical-align: top;
  line-height: 1.55;
}
.dev-body :deep(.help-note) {
  margin-top: 12px;
  padding-top: 9px;
  border-top: 1px dashed #eef2f8;
  color: #475467;
  font-size: 11.5px;
  line-height: 1.75;
}

@media (max-width: 560px) {
  .dev-drawer { width: 100vw; }
}
</style>
