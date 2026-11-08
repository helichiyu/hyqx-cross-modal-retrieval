<template>
  <div class="workspace">
    <aside class="sidebar">
      <nav class="nav-scroll">
        <template v-for="(group, groupIndex) in navGroups" :key="groupIndex">
          <div v-if="group.title" class="nav-group-title">{{ group.title }}</div>
          <RouterLink
            v-for="item in group.items"
            :key="item.path"
            :to="item.path"
            :title="item.title"
            class="nav-item"
            :class="{ active: isActive(item.path) }"
          >
            <span class="ico"><el-icon><component :is="item.icon" /></el-icon></span>
            <span>{{ item.title }}</span>
          </RouterLink>
          <div v-if="group.divider" class="nav-divider" />
        </template>
      </nav>
    </aside>

    <main class="main">
      <router-view />
    </main>
  </div>
</template>

<script setup lang="ts">
import { useRoute } from 'vue-router'

const route = useRoute()

const navGroups = [
  {
    title: '',
    divider: true,
    items: [
      { path: '/search', title: '检索台', icon: 'Search' },
      { path: '/history', title: '检索历史', icon: 'Clock' },
    ],
  },
  {
    title: '',
    divider: false,
    items: [
      { path: '/library', title: '库管理', icon: 'FolderOpened' },
      { path: '/strategies', title: '策略管理', icon: 'SetUp' },
      { path: '/tasks', title: '任务管理', icon: 'List' },
      { path: '/operation', title: '结果运营', icon: 'Service' },
      { path: '/evaluation', title: '效果评测', icon: 'DataAnalysis' },
    ],
  },
]

function isActive(path: string) {
  return route.path === path || route.path.startsWith(`${path}/`)
}
</script>

<style scoped>
.workspace {
  min-width: 0;
  display: flex;
  min-height: 100vh;
  background: var(--workspace-bg);
}

.sidebar {
  width: 220px;
  flex-shrink: 0;
  position: sticky;
  top: 0;
  height: 100vh;
  display: flex;
  flex-direction: column;
  background: #fff;
  border-right: 1px solid var(--content-line);
}

.nav-scroll {
  flex: 1;
  overflow-y: auto;
  padding: 14px 12px;
}

.nav-group-title {
  margin: 8px 10px 6px;
  color: #98a2b3;
  font-size: 11px;
  line-height: 16px;
}

.nav-item {
  display: flex;
  align-items: center;
  gap: 9px;
  min-height: 36px;
  padding: 0 10px;
  margin-bottom: 2px;
  border-radius: 6px;
  color: #344054;
  font-size: 13px;
  text-decoration: none;
  cursor: pointer;
  user-select: none;
  transition: background-color 0.16s ease, color 0.16s ease;
}

.nav-item:hover {
  background: var(--system-blue-weak);
  color: var(--system-blue);
}

.nav-item.active {
  background: var(--system-blue-weak);
  color: var(--system-blue);
  font-weight: 600;
  box-shadow: inset 2px 0 0 var(--system-blue);
}

.ico {
  width: 18px;
  height: 18px;
  display: flex;
  align-items: center;
  justify-content: center;
  color: #8a96ad;
  flex-shrink: 0;
}

.nav-item:hover .ico,
.nav-item.active .ico {
  color: var(--system-blue);
}

.nav-divider {
  height: 1px;
  margin: 8px 10px;
  background: var(--content-line);
}

.main {
  flex: 1;
  min-width: 0;
  height: 100vh;
  overflow-y: auto;
}

@media (max-width: 900px) {
  .workspace { flex-direction: column; }
  .sidebar { position: relative; width: 100%; height: auto; border-right: 0; border-bottom: 1px solid var(--content-line); }
  .nav-scroll { display: flex; align-items: center; gap: 4px; overflow-x: auto; padding: 8px 10px; }
  .nav-item { flex: 0 0 auto; min-height: 34px; }
  .nav-divider { width: 1px; height: 22px; margin: 0 5px; flex: 0 0 1px; }
  .main { height: auto; min-height: 0; overflow: visible; }
}
</style>
