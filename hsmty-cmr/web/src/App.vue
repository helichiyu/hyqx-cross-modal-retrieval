<script setup lang="ts">
import { computed, onMounted, onUnmounted } from 'vue'
import { useRoute } from 'vue-router'
import { ElMessage } from 'element-plus'
import { bindUnauthorizedListener, useSessionStore } from '@/core/auth'
import { cmsMenus } from '@/modules/cross-modal-search/menu'

const route = useRoute()
const session = useSessionStore()

const menus = cmsMenus

const activeMenu = computed(() => `/${route.path.split('/').slice(0, 2).join('/')}`)

let unbind: (() => void) | undefined

onMounted(() => {
  unbind = bindUnauthorizedListener(() => {
    session.markExpired()
    ElMessage.error('登录已失效，请重新登录主应用')
  })
})

onUnmounted(() => unbind?.())
</script>

<template>
  <el-container class="cms-layout">
    <el-aside
      width="220px"
      class="cms-aside"
    >
      <div class="cms-brand">
        跨模态检索
      </div>
      <el-menu
        :default-active="activeMenu"
        router
      >
        <el-menu-item
          v-for="menu in menus"
          :key="menu.path"
          :index="menu.path"
        >
          {{ menu.title }}
        </el-menu-item>
      </el-menu>
    </el-aside>
    <el-container>
      <el-header class="cms-header">
        <span>{{ route.meta.title || '跨模态检索' }}</span>
        <el-tag
          v-if="session.expired"
          type="danger"
          effect="dark"
        >
          会话已失效
        </el-tag>
      </el-header>
      <el-main class="cms-main">
        <router-view />
      </el-main>
    </el-container>
  </el-container>
</template>

<style scoped>
.cms-layout {
  height: 100%;
  background: #f5f7fa;
}

.cms-aside {
  background: #fff;
  border-right: 1px solid #e4e7ed;
}

.cms-brand {
  height: 56px;
  line-height: 56px;
  padding-left: 20px;
  font-size: 16px;
  font-weight: 600;
  color: #303133;
}

.cms-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  background: #fff;
  border-bottom: 1px solid #e4e7ed;
}

.cms-main {
  padding: 20px;
}
</style>
