<template>
  <aside class="sidebar">
    <div class="brand">
      <div class="brand-mark">A</div>
      <div>
        <div class="brand-title">Assistant</div>
        <div class="brand-subtitle">Vue 3 + Java 8</div>
      </div>
    </div>

    <nav class="sidebar-nav">
      <RouterLink v-for="item in items" :key="item.to" :to="item.to" class="nav-link">
        <span>{{ item.label }}</span>
        <small>{{ item.caption }}</small>
      </RouterLink>
    </nav>
  </aside>
</template>

<script setup>
const items = [
  { to: '/', label: '工作台', caption: 'Overview' },
  { to: '/documents', label: '文档中心', caption: 'Libraries & files' },
  { to: '/datasources', label: '数据源', caption: 'Managed captures' },
  { to: '/reports', label: '报表中心', caption: 'Templates & outputs' },
]
</script>
