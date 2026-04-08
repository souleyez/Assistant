<template>
  <section class="page">
    <PageHeader
      eyebrow="Migration"
      title="AI Assistant 工作台"
      description="从 ai-data-platform 主应用迁移而来，当前以 Vue 3 前端和 Java 8 后端承接文档、数据源和报表核心流。"
    />

    <div v-if="loading" class="panel">加载中...</div>
    <div v-else-if="error" class="panel error-panel">{{ error }}</div>
    <template v-else-if="data">
      <section class="stats-grid">
        <StatCard label="知识库" :value="data.metrics.libraryCount" subtle="document libraries" />
        <StatCard label="文档数" :value="data.metrics.documentCount" subtle="stored files" />
        <StatCard label="数据源" :value="data.metrics.datasourceCount" subtle="managed definitions" />
        <StatCard label="模板数" :value="data.metrics.templateCount" subtle="report templates" />
      </section>

      <section class="dashboard-grid">
        <article class="panel hero-panel">
          <p class="eyebrow">Model Status</p>
          <h2>{{ data.model.currentModel || 'Local assistant model not configured' }}</h2>
          <p>
            OpenClaw 安装状态：{{ data.model.openclaw.installed ? '已安装' : '未安装' }}，
            运行状态：{{ data.model.openclaw.running ? '运行中' : '未运行' }}。
          </p>
          <div class="chip-row">
            <span v-for="provider in data.model.providers" :key="provider" class="chip">{{ provider }}</span>
          </div>
        </article>

        <article class="panel">
          <p class="eyebrow">Connected Bots</p>
          <div v-if="data.bots.length" class="list-stack">
            <div v-for="bot in data.bots" :key="bot.id" class="list-row">
              <div>
                <strong>{{ bot.name }}</strong>
                <p>{{ bot.channel }} · {{ bot.status }}</p>
              </div>
              <span class="chip">{{ bot.boundLibraries.length }} 个知识库</span>
            </div>
          </div>
          <p v-else>暂无已接通输出机器人。</p>
        </article>

        <article class="panel">
          <p class="eyebrow">Recent Activity</p>
          <div class="timeline">
            <div v-for="item in data.timeline" :key="item.id" class="timeline-item">
              <strong>{{ item.title }}</strong>
              <p>{{ item.description }}</p>
              <small>{{ formatTime(item.at) }}</small>
            </div>
          </div>
        </article>
      </section>
    </template>
  </section>
</template>

<script setup>
import { onMounted } from 'vue'
import { request } from '../api'
import { useAsyncState } from '../composables/useAsyncState'
import PageHeader from '../components/PageHeader.vue'
import StatCard from '../components/StatCard.vue'

const { data, loading, error, run } = useAsyncState(null)

function formatTime(value) {
  if (!value) {
    return '-'
  }
  return new Intl.DateTimeFormat('zh-CN', {
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit',
  }).format(new Date(value))
}

onMounted(() => {
  run(() => request('/api/home'))
})
</script>
