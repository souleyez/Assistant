<template>
  <section class="page">
    <PageHeader
      eyebrow="Knowledge"
      title="文档中心"
      description="保留原项目的轻量知识库治理逻辑，支持查看文档、统计解析状态以及快速新建知识库。"
    >
      <template #actions>
        <button class="button button-secondary" @click="loadDocuments">刷新</button>
      </template>
    </PageHeader>

    <div class="panel form-panel">
      <input v-model.trim="libraryName" class="input" placeholder="输入知识库名称" />
      <button class="button" :disabled="!libraryName || submitting" @click="createLibrary">
        {{ submitting ? '创建中...' : '新建知识库' }}
      </button>
    </div>

    <div v-if="message" class="panel success-panel">{{ message }}</div>
    <div v-if="loading" class="panel">加载中...</div>
    <div v-else-if="error" class="panel error-panel">{{ error }}</div>
    <template v-else-if="data">
      <section class="stats-grid">
        <StatCard label="总文件数" :value="data.totalFiles" subtle="all documents" />
        <StatCard label="已解析" :value="data.meta.parsed" subtle="parsed items" />
        <StatCard label="解析率" :value="parseRate" subtle="parse coverage" />
        <StatCard label="知识库" :value="data.libraries.length" subtle="libraries" />
      </section>

      <section class="content-grid">
        <article class="panel">
          <p class="eyebrow">Libraries</p>
          <div class="chip-row">
            <span v-for="library in data.libraries" :key="library.key" class="chip">
              {{ library.label }} · {{ library.documentCount }}
            </span>
          </div>
        </article>

        <article class="panel table-panel">
          <p class="eyebrow">Documents</p>
          <table class="table">
            <thead>
              <tr>
                <th>文件</th>
                <th>知识库</th>
                <th>类型</th>
                <th>解析状态</th>
                <th>更新时间</th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="item in data.items" :key="item.id">
                <td>{{ item.name }}</td>
                <td>{{ item.libraryLabel }}</td>
                <td>{{ item.fileType }}</td>
                <td>{{ item.parseStatus }}</td>
                <td>{{ formatTime(item.updatedAt) }}</td>
              </tr>
            </tbody>
          </table>
        </article>
      </section>
    </template>
  </section>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { request } from '../api'
import { useAsyncState } from '../composables/useAsyncState'
import PageHeader from '../components/PageHeader.vue'
import StatCard from '../components/StatCard.vue'

const { data, loading, error, run } = useAsyncState(null)
const libraryName = ref('')
const submitting = ref(false)
const message = ref('')

const parseRate = computed(() => {
  if (!data.value?.totalFiles) {
    return '0%'
  }
  return `${Math.round((data.value.meta.parsed / data.value.totalFiles) * 100)}%`
})

function formatTime(value) {
  return new Intl.DateTimeFormat('zh-CN', {
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit',
  }).format(new Date(value))
}

async function loadDocuments() {
  message.value = ''
  await run(() => request('/api/documents'))
}

async function createLibrary() {
  submitting.value = true
  message.value = ''
  try {
    await request('/api/documents/libraries', {
      method: 'POST',
      body: JSON.stringify({ name: libraryName.value }),
    })
    libraryName.value = ''
    message.value = '知识库已创建。'
    await loadDocuments()
  } catch (err) {
    message.value = err instanceof Error ? err.message : '创建失败'
  } finally {
    submitting.value = false
  }
}

onMounted(loadDocuments)
</script>
