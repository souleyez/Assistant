<template>
  <aside class="sidebar">
    <div class="brand">
      <div class="brand-mark">A</div>
      <div>
        <div class="brand-title">Gemma 4 YOLO Studio</div>
        <div class="brand-subtitle">Single-machine trainer</div>
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
  { to: '/', label: '总览', caption: 'Machine & progress' },
  { to: '/datasets', label: '数据集', caption: 'YOLO corpora' },
  { to: '/projects', label: '训练项目', caption: 'Experiment specs' },
  { to: '/jobs', label: '训练任务', caption: 'Run queue' },
  { to: '/models', label: '模型仓库', caption: 'Artifacts' },
  { to: '/gemma', label: 'Gemma 助手', caption: 'Advisor' },
]
</script>
