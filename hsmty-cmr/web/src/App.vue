<template>
  <router-view />
</template>

<script setup lang="ts">
import { onMounted, onUnmounted } from 'vue'
import { ElMessage } from 'element-plus'
import { bindUnauthorizedListener } from '@/core/auth'

let unbind: (() => void) | undefined

onMounted(() => {
  unbind = bindUnauthorizedListener(() => {
    ElMessage.error('登录已失效，请重新登录主应用')
  })
})

onUnmounted(() => unbind?.())
</script>
