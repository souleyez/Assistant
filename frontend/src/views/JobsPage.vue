<template>
  <section class="page">
    <PageHeader
      eyebrow="Jobs"
      title="训练任务"
      description="单机队列模式：你从项目发起训练，任务在本机 GPU 上串行或准串行执行。"
    />

    <section class="content-grid">
      <article class="panel">
        <p class="eyebrow">启动新任务</p>
        <div class="form-grid">
          <select v-model="selectedProjectId" class="input input-wide">
            <option value="">选择训练项目</option>
            <option v-for="project in projects" :key="project.id" :value="project.id">{{ project.name }}</option>
          </select>
        </div>
        <div class="form-actions">
          <button class="button" :disabled="submitting || !selectedProjectId" @click="startJob">
            {{ submitting ? '启动中...' : '启动训练' }}
          </button>
        </div>
      </article>

      <article class="panel table-panel">
        <p class="eyebrow">任务列表</p>
        <table v-if="items.length" class="table">
          <thead>
            <tr>
              <th>项目</th>
              <th>进度</th>
              <th>指标</th>
              <th>目录</th>
              <th>状态</th>
              <th>操作</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="item in items" :key="item.id">
              <td>
                <strong>{{ item.projectName }}</strong>
                <div class="table-subtle">{{ item.datasetName }}</div>
              </td>
              <td>{{ item.currentEpoch }}/{{ item.totalEpochs }}</td>
              <td>
                <div class="table-subtle">mAP50 {{ item.map50 }}</div>
                <div class="table-subtle">P {{ item.precisionScore }} / R {{ item.recallScore }}</div>
                <div v-if="item.failureMessage" class="table-subtle">{{ item.failureMessage }}</div>
              </td>
              <td>
                <div class="table-subtle">run {{ item.outputPath || '-' }}</div>
                <div class="table-subtle">log {{ item.logPath || '-' }}</div>
              </td>
              <td><span class="chip">{{ item.status }}</span></td>
              <td>
                <div class="inline-actions">
                  <button class="button button-secondary" @click="loadLogs(item.id)">查看日志</button>
                  <button v-if="item.status === 'running'" class="button button-secondary" @click="completeJob(item.id)">标记完成</button>
                  <button v-if="item.status === 'running' || item.status === 'queued'" class="button button-danger" @click="cancelJob(item.id)">取消</button>
                </div>
              </td>
            </tr>
          </tbody>
        </table>
        <EmptyState v-else title="暂无训练任务" text="选择一个项目后启动单机训练。" />
      </article>
    </section>

    <section class="panel">
      <p class="eyebrow">任务日志</p>
      <div v-if="selectedLogJobId" class="conversation-meta">
        <span class="chip">{{ selectedLogJobId }}</span>
        <button class="button button-secondary" @click="loadLogs(selectedLogJobId)">刷新日志</button>
      </div>
      <pre class="response-block">{{ logText || '选择一个任务后查看最近日志。' }}</pre>
    </section>

    <div v-if="message" class="panel success-panel">{{ message }}</div>
    <div v-if="error" class="panel error-panel">{{ error }}</div>
  </section>
</template>

<script setup>
import { onMounted, ref } from 'vue'
import { request } from '../api'
import EmptyState from '../components/EmptyState.vue'
import PageHeader from '../components/PageHeader.vue'

const items = ref([])
const projects = ref([])
const selectedProjectId = ref('')
const submitting = ref(false)
const message = ref('')
const error = ref('')
const logText = ref('')
const selectedLogJobId = ref('')

async function loadAll() {
  const [jobResponse, projectResponse] = await Promise.all([
    request('/api/jobs'),
    request('/api/projects'),
  ])
  items.value = jobResponse.items
  projects.value = projectResponse.items
}

async function startJob() {
  submitting.value = true
  message.value = ''
  error.value = ''
  try {
    await request('/api/jobs', {
      method: 'POST',
      body: JSON.stringify({ projectId: selectedProjectId.value }),
    })
    selectedProjectId.value = ''
    await loadAll()
    message.value = '训练任务已启动。'
  } catch (err) {
    error.value = err instanceof Error ? err.message : '启动失败'
  } finally {
    submitting.value = false
  }
}

async function completeJob(id) {
  await request(`/api/jobs/${id}/complete`, { method: 'POST', body: '{}' })
  await loadAll()
  message.value = '训练任务已完成，并已登记模型。'
}

async function cancelJob(id) {
  await request(`/api/jobs/${id}/cancel`, { method: 'POST', body: '{}' })
  await loadAll()
  message.value = '训练任务已取消。'
}

async function loadLogs(id) {
  const response = await request(`/api/jobs/${id}/logs`)
  selectedLogJobId.value = id
  logText.value = Array.isArray(response.lines) ? response.lines.join('\n') : '暂无日志'
}

onMounted(loadAll)
</script>
