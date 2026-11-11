<template>
  <div class="workspace">
    <aside class="sidebar">
      <nav class="nav-scroll">
        <template v-for="(group, groupIndex) in visibleGroups" :key="groupIndex">
          <RouterLink
            v-for="item in group.items"
            :key="item.path"
            :to="item.path"
            :title="item.title"
            class="nav-item"
            :class="{ active: isActive(item.path) }"
          >
            <span class="ico"><el-icon :size="16"><component :is="item.icon" /></el-icon></span>
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
import { computed } from 'vue'
import { useRoute } from 'vue-router'
import { useAuthStore } from '../stores/auth'

const route = useRoute()
const auth = useAuthStore()

/** 原型 v5 左栏顺序：检索台（独立）→ 管理四项 */
const navGroups = [
  {
    divider: true,
    items: [{ path: '/search', title: '检索台', icon: 'Search', permission: 'cmr:search:create' }],
  },
  {
    divider: false,
    items: [
      { path: '/library', title: '数据源库管理', icon: 'Coin', permission: 'cmr:dataset:page' },
      { path: '/strategies', title: '策略管理', icon: 'SetUp', permission: 'cmr:strategy:page' },
      { path: '/behaviors', title: '搜索行为', icon: 'DocumentCopy', permission: 'cmr:search:page' },
      { path: '/operation', title: '结果运营', icon: 'Star', permission: 'cmr:feedback:page' },
      { path: '/evaluation', title: '效果评测', icon: 'Histogram', permission: 'cmr:eval:page' },
    ],
  },
]

/** 权限码未就绪（本地无 UAP）时保守全量展示，避免菜单整列消失 */
function canVisit(permission: string) {
  return !auth.user || auth.permissions.has(permission)
}

const visibleGroups = computed(() =>
  navGroups
    .map((group) => ({ ...group, items: group.items.filter((item) => canVisit(item.permission)) }))
    .filter((group) => group.items.length > 0),
)

/** 仅精确命中高亮：结果页/摘要页属于检索流程，不点亮检索台 */
function isActive(path: string) {
  return route.path === path
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
  padding: 10px 12px;
}

.nav-item {
  display: flex;
  align-items: center;
  gap: 9px;
  padding: 9px 10px;
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
  .nav-item { flex: 0 0 auto; padding: 8px 10px; }
  .nav-divider { width: 1px; height: 22px; margin: 0 5px; flex: 0 0 1px; }
  .main { height: auto; min-height: 0; overflow: visible; }
}
</style>
