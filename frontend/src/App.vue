<template>
  <div class="shell">
    <SidebarNav />
    <main class="shell-main">
      <RouterView />
    </main>
  </div>
</template>

<script setup>
import { RouterView } from 'vue-router'
import SidebarNav from './components/SidebarNav.vue'
</script>
