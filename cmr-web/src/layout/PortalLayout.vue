<template>
  <el-container class="portal">
    <el-aside width="220px" class="aside">
      <div class="logo">
        <el-icon :size="22"><Search /></el-icon>
        <span>跨模态检索中心</span>
      </div>
      <el-menu
        :default-active="$route.path"
        router
        background-color="#001529"
        text-color="#a6adb4"
        active-text-color="#ffffff"
      >
        <el-menu-item v-for="item in menus" :key="item.path" :index="item.path">
          <el-icon><component :is="item.icon" /></el-icon>
          <span>{{ item.title }}</span>
        </el-menu-item>
      </el-menu>
    </el-aside>
    <el-container>
      <el-header class="header">
        <div class="header-title">{{ $route.meta.title }}</div>

        <span class="user-chip">
          <el-icon><User /></el-icon>
          {{ auth.user?.nickname || auth.user?.username }}
          <el-tag size="small" effect="plain">{{ roleLabel }}</el-tag>
        </span>
      </el-header>
      <el-main class="main">
        <router-view />
      </el-main>
    </el-container>
  </el-container>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { useRoute } from 'vue-router'
import { useAuthStore } from '../stores/auth'
import { routes } from '../router'

const auth = useAuthStore()
const route = useRoute()
const ROLE_LABELS: Record<string, string> = {
  admin: '管理员',
  operator: '运营',
  user: '检索用户',
}

const roleLabel = computed(() => ROLE_LABELS[auth.role] ?? auth.role)

// 按当前角色权限过滤菜单（与路由守卫同一套 permission 码）
const menus = computed(() => {
  const main = routes.find((r) => r.path === '/')
  return (main?.children ?? [])
    .filter((c) => !c.meta?.hidden)
    .filter((c) => !c.meta?.permission || auth.permissions.has(c.meta.permission as string))
    .map((c) => ({
      path: '/' + c.path,
      title: c.meta?.title as string,
      icon: c.meta?.icon as string,
    }))
})

</script>

<style scoped>
.portal {
  height: 100%;
}
.aside {
  background: #001529;
}
.logo {
  display: flex;
  align-items: center;
  gap: 8px;
  color: #fff;
  font-size: 15px;
  font-weight: 600;
  height: 56px;
  padding: 0 16px;
  border-bottom: 1px solid rgba(255, 255, 255, 0.08);
}
.aside :deep(.el-menu) {
  border-right: none;
}
.header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  background: #fff;
  border-bottom: 1px solid #e4e7ed;
}
.header-title {
  font-size: 16px;
  font-weight: 600;
}
.user-chip {
  display: flex;
  align-items: center;
  gap: 6px;
  cursor: pointer;
  font-size: 13px;
}
.main {
  padding: 0;
}
</style>
