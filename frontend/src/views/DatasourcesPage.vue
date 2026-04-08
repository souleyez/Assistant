<template>
  <section class="page">
    <PageHeader
      eyebrow="Capture"
      title="数据源工作台"
      description="延续原工作台模式，在一个页面里管理数据源定义、运行记录和知识库绑定。"
    />

    <section class="content-grid">
      <article class="panel">
        <p class="eyebrow">新建 / 编辑数据源</p>
        <div class="form-grid">
          <input v-model.trim="form.name" class="input" placeholder="数据源名称" />
          <select v-model="form.kind" class="input">
            <option value="web">公开网页</option>
            <option value="web_login">登录网页</option>
            <option value="database">数据库</option>
            <option value="erp">ERP</option>
            <option value="local_directory">本地目录</option>
          </select>
          <input v-model.trim="form.endpoint" class="input input-wide" placeholder="地址 / 连接串 / 目录路径" />
        </div>

        <div class="chip-row chip-row-select">
          <button
            v-for="library in libraries"
            :key="library.key"
            class="chip chip-button"
            :class="{ selected: form.targetLibraryKeys.includes(library.key) }"
            @click="toggleLibrary(library.key)"
          >
            {{ library.label }}
          </button>
        </div>

        <div class="form-actions">
          <button class="button" :disabled="submitting" @click="saveDatasource">
            {{ submitting ? '保存中...' : form.id ? '更新数据源' : '创建数据源' }}
          </button>
          <button class="button button-secondary" @click="resetForm">清空</button>
        </div>
      </article>

      <article class="panel">
        <p class="eyebrow">已管理数据源</p>
        <div v-if="loading" class="list-row">加载中...</div>
        <div v-else-if="error" class="error-panel">{{ error }}</div>
        <div v-else-if="definitions.length" class="list-stack">
          <div v-for="item in definitions" :key="item.id" class="list-row list-row-stretch">
            <div>
              <strong>{{ item.name }}</strong>
              <p>{{ item.kind }} · {{ item.status }} · {{ item.endpoint }}</p>
            </div>
            <div class="inline-actions">
              <button class="button button-secondary" @click="editDatasource(item)">编辑</button>
              <button class="button button-secondary" @click="runAction(item.id, 'run')">执行</button>
              <button class="button button-secondary" @click="runAction(item.id, item.status === 'active' ? 'pause' : 'activate')">
                {{ item.status === 'active' ? '暂停' : '启用' }}
              </button>
              <button class="button button-danger" @click="removeDatasource(item.id)">删除</button>
            </div>
          </div>
        </div>
        <EmptyState v-else title="暂无数据源" text="先创建一条数据源定义，再触发采集。" />
      </article>
    </section>

    <section class="panel">
      <p class="eyebrow">最近运行记录</p>
      <table v-if="runs.length" class="table">
        <thead>
          <tr>
            <th>数据源</th>
            <th>状态</th>
            <th>采集量</th>
            <th>开始时间</th>
            <th>操作</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="run in runs" :key="run.id">
            <td>{{ run.datasourceName }}</td>
            <td>{{ run.status }}</td>
            <td>{{ run.capturedCount }}</td>
            <td>{{ formatTime(run.startedAt) }}</td>
            <td>
              <button class="button button-danger" @click="deleteRun(run.id)">删除</button>
            </td>
          </tr>
        </tbody>
      </table>
      <EmptyState v-else title="暂无运行记录" text="执行一次数据源后，这里会出现运行结果。" />
    </section>

    <div v-if="message" class="panel success-panel">{{ message }}</div>
  </section>
</template>

<script setup>
import { onMounted, ref } from 'vue'
import { request } from '../api'
import PageHeader from '../components/PageHeader.vue'
import EmptyState from '../components/EmptyState.vue'

const loading = ref(false)
const error = ref('')
const submitting = ref(false)
const message = ref('')
const definitions = ref([])
const runs = ref([])
const libraries = ref([])
const form = ref({
  id: '',
  name: '',
  kind: 'web',
  endpoint: '',
  targetLibraryKeys: [],
})

function formatTime(value) {
  return new Intl.DateTimeFormat('zh-CN', {
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit',
  }).format(new Date(value))
}

function resetForm() {
  form.value = {
    id: '',
    name: '',
    kind: 'web',
    endpoint: '',
    targetLibraryKeys: [],
  }
}

function editDatasource(item) {
  form.value = {
    id: item.id,
    name: item.name,
    kind: item.kind,
    endpoint: item.endpoint,
    targetLibraryKeys: [...item.targetLibraryKeys],
  }
}

function toggleLibrary(key) {
  if (form.value.targetLibraryKeys.includes(key)) {
    form.value.targetLibraryKeys = form.value.targetLibraryKeys.filter((item) => item !== key)
    return
  }
  form.value.targetLibraryKeys = [...form.value.targetLibraryKeys, key]
}

async function loadAll() {
  loading.value = true
  error.value = ''
  try {
    const [definitionsResponse, runsResponse, documentsResponse] = await Promise.all([
      request('/api/datasources/definitions'),
      request('/api/datasources/runs'),
      request('/api/documents'),
    ])
    definitions.value = definitionsResponse.items
    runs.value = runsResponse.items
    libraries.value = documentsResponse.libraries
  } catch (err) {
    error.value = err instanceof Error ? err.message : '加载失败'
  } finally {
    loading.value = false
  }
}

async function saveDatasource() {
  submitting.value = true
  message.value = ''
  try {
    const path = form.value.id ? `/api/datasources/definitions/${form.value.id}` : '/api/datasources/definitions'
    const method = form.value.id ? 'PATCH' : 'POST'
    await request(path, {
      method,
      body: JSON.stringify(form.value),
    })
    resetForm()
    await loadAll()
    message.value = '数据源已保存。'
  } catch (err) {
    message.value = err instanceof Error ? err.message : '保存失败'
  } finally {
    submitting.value = false
  }
}

async function runAction(id, action) {
  message.value = ''
  await request(`/api/datasources/definitions/${id}/${action}`, {
    method: 'POST',
    body: '{}',
  })
  await loadAll()
  message.value = '数据源状态已更新。'
}

async function removeDatasource(id) {
  await request(`/api/datasources/definitions/${id}`, { method: 'DELETE' })
  await loadAll()
  message.value = '数据源已删除。'
}

async function deleteRun(id) {
  await request(`/api/datasources/runs/${id}`, { method: 'DELETE' })
  await loadAll()
  message.value = '运行记录已删除。'
}

onMounted(loadAll)
</script>
